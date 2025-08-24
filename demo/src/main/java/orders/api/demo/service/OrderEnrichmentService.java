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
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
public class OrderEnrichmentService {

    private final OrderRepository repo;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrderEnriched createEnriched(OrderCreate req) throws Exception {
        // Prove which kind of thread is serving the request
        Thread t = Thread.currentThread();
        System.out.printf("POST /orders/enrich served by %s (virtual=%s)%n", t, t.isVirtual());

        // A per-call virtual-thread executor; closes immediately after use
        try (ExecutorService vexec = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())) {

            // Run validation + price fetch in parallel
            Future<Void> validateF = vexec.submit(() -> { validate(req); return null; });
            Future<BigDecimal> priceF = vexec.submit(() -> fetchPrice(req.ticker()));

            // Wait for both to complete (propagates exceptions)
            validateF.get();
            BigDecimal price = priceF.get();

            // Persist (blocking DB call)
            Order o = new Order();
            o.setTicker(req.ticker());
            o.setQuantity(req.quantity());
            o.setPrice(price.doubleValue());
            o.setCreatedAt(Instant.now());

            Order saved = repo.save(o);

            return new OrderEnriched(
                    saved.getId(),
                    saved.getTicker(),
                    saved.getQuantity(),
                    price,
                    saved.getCreatedAt(),
                    Thread.currentThread() + " (virtual=" + Thread.currentThread().isVirtual() + ")"
            );
        }
    }

    private void validate(OrderCreate req) throws InterruptedException {
        if (req.ticker() == null || req.ticker().isBlank()) {
            throw new IllegalArgumentException("ticker is required");
        }
        if (req.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        // simulate blocking validation I/O
        Thread.sleep(200);
    }

    private BigDecimal fetchPrice(String ticker) throws Exception {
        URI uri = URI.create("https://api.binance.com/api/v3/ticker/price?symbol=" + ticker);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(java.time.Duration.ofSeconds(3))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Price API failed: " + resp.statusCode());
        }
        JsonNode json = mapper.readTree(resp.body());
        // Response shape: { "symbol": "BTCUSDT", "price": "60432.12000000" }
        return new BigDecimal(json.get("price").asText());
    }
}

