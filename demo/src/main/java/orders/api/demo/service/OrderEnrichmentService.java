package orders.api.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import orders.api.demo.dto.OrderCreate;
import orders.api.demo.dto.OrderEnriched;
import orders.api.demo.model.Order;
import orders.api.demo.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.StructuredTaskScope;

/**
 * OrderEnrichmentService
 *
 * What this class does:
 * ---------------------
 * - Accepts a lightweight "create request" (ticker + quantity).
 * - Validates the request against the DB (blocking call).
 * - In parallel, fetches the current price from an external HTTP API (blocking call).
 * - Persists the order and returns an enriched DTO with the final price and some thread info.
 *
 * Where Project Loom helps:
 * -------------------------
 * - We use Structured Concurrency (StructuredTaskScope.ShutdownOnFailure) to launch BOTH
 *   the DB validation and HTTP fetch concurrently, each on a VIRTUAL THREAD.
 * - Virtual threads are cheap to create (thousands+), so we can model concurrent blocking
 *   operations naturally without switching to reactive/async styles.
 * - When these blocking calls (DB/HTTP) wait for I/O, the virtual thread is parked and its
 *   underlying carrier OS thread is released back to the pool. This is the key efficiency gain.
 *
 * Under the hood (Loom basics):
 * -----------------------------
 * - scope.fork(...) starts a Subtask that runs on a virtual thread.
 * - scope.join() waits for all subtasks to finish (success or failure).
 * - scope.throwIfFailed() rethrows the FIRST failure, and ShutdownOnFailure cancels the siblings.
 * - Subtask.get() returns the result (blocking the *current* thread, virtual as well).
 * - Default scheduler for virtual threads is a global ForkJoinPool-like scheduler.
 *
 * Notes / Gotchas:
 * ----------------
 * - Avoid long native calls or holding synchronized locks during long waits; that can "pin"
 *   the carrier thread. Standard JDBC and java.net.http are Loom-friendly (non-pinning I/O).
 * - You kept a per-operation HTTP timeout (3s). If you later add a global budget using
 *   scope.joinUntil(...), ensure it’s >= the slowest subtask timeout or handle partial completion.
 */
@Service
@RequiredArgsConstructor
public class OrderEnrichmentService {

    private final OrderRepository repo;    // Spring Data JPA repository (blocking calls)
    private final HttpClient http;         // java.net.http client (blocking API, Loom-friendly)
    private final ObjectMapper mapper = new ObjectMapper(); // JSON parsing

    /**
     * createEnriched(OrderCreate)
     *
     * High-level flow:
     * 1) Log thread info (to show we’re running on virtual threads when configured).
     * 2) Open a structured concurrency scope that cancels siblings on the first failure.
     * 3) Fork two subtasks in PARALLEL:
     *      - validate(...) hits the DB (blocking)
     *      - fetchPrice(...) calls an external HTTP API (blocking)
     *    Both run on VIRTUAL THREADS (cheap, parkable during I/O).
     * 4) scope.join() waits for both; scope.throwIfFailed() propagates exceptions (and cancels siblings).
     * 5) On success, get the price, persist the order (blocking DB write is fine on virtual threads),
     *    and return an enriched DTO.
     *
     * Loom impact:
     * - The concurrency here is expressed with simple, imperative code—no CompletableFuture/Flux.
     * - Even though validate() and fetchPrice() block, the carrier OS threads are freed while waiting.
     */
    public OrderEnriched createEnriched(OrderCreate req) throws Exception {
        // For demo: show whether we’re on a virtual thread
        Thread t = Thread.currentThread();
        System.out.printf("POST /orders/enrich served by %s (virtual=%s)%n", t, t.isVirtual());

        // Structured concurrency scope: cancels siblings when one fails.
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // Subtask 1: DB validation (blocking). We wrap void -> Callable<Void>.
            // Under the hood: a VIRTUAL THREAD runs this; when JDBC blocks on I/O,
            // the virtual thread is parked and the carrier OS thread is released.
            StructuredTaskScope.Subtask<Void> validateTask = scope.fork(() -> {
                validate(req);
                return null;
            });

            // Subtask 2: External HTTP price fetch (blocking).
            // Also on a VIRTUAL THREAD; when waiting on the socket, it parks.
            StructuredTaskScope.Subtask<BigDecimal> priceTask = scope.fork(() -> fetchPrice(req.ticker()));

            // Wait for both subtasks to complete (success/failure).
            // This blocks the current (likely virtual) thread; parking is efficient here too.
            scope.join();

            // If any subtask failed, rethrow the exception (first one).
            // ShutdownOnFailure cancels the other subtask automatically.
            scope.throwIfFailed();

            // Safe now: both succeeded. get() returns the result of the price subtask.
            BigDecimal price = priceTask.get();

            // Persist the order. Blocking JDBC write is fine with virtual threads.
            Order o = new Order();
            o.setTicker(req.ticker());
            o.setQuantity(req.quantity());
            o.setPrice(price.doubleValue());
            o.setCreatedAt(Instant.now());

            Order saved = repo.save(o);

            // Return DTO including a human-readable thread string (nice for demos).
            return new OrderEnriched(
                    saved.getId(),
                    saved.getTicker(),
                    saved.getQuantity(),
                    price,
                    saved.getCreatedAt(),
                    STR."\{Thread.currentThread()} (virtual=\{Thread.currentThread().isVirtual()})"
            );
        }
    }

    /**
     * validate(OrderCreate)
     *
     * What it does:
     * - Guards against invalid quantity.
     * - Checks if the ticker exists in the DB using a Spring Data JPA derived query:
     *   boolean existsByTicker(String ticker)
     *
     * Under the hood:
     * - repo.existsByTicker(...) is a blocking call executed on a VIRTUAL THREAD.
     * - When the thread waits on JDBC I/O, it parks (Loom releases the carrier thread).
     */
    private void validate(OrderCreate req) {
        if (req.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        boolean exists = repo.existsByTicker(req.ticker());
        if (!exists) {
            throw new IllegalArgumentException("ticker not found in DB");
        }
    }

    /**
     * fetchPrice(String)
     *
     * What it does:
     * - Calls Binance public ticker API (blocking HTTP).
     * - Applies a per-request timeout of 3 seconds at the HTTP client level.
     * - Parses JSON into BigDecimal.
     *
     * Under the hood:
     * - http.send(...) is a blocking call on a VIRTUAL THREAD.
     * - While waiting for network I/O, the virtual thread parks and the OS thread is reused.
     * - On timeout or non-200, we fail fast; StructuredTaskScope will cancel siblings.
     */
    private BigDecimal fetchPrice(String ticker) throws Exception {
        URI uri = URI.create(STR."https://api.binance.com/api/v3/ticker/price?symbol=\{ticker}");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(3)) // per-operation budget
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(STR."Price API failed: \{resp.statusCode()}");
        }

        JsonNode json = mapper.readTree(resp.body());
        return new BigDecimal(json.get("price").asText());
    }
}
