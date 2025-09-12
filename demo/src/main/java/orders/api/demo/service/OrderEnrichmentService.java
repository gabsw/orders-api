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

@Service
@RequiredArgsConstructor
public class OrderEnrichmentService {

    private final OrderRepository repo;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrderEnriched createEnriched(OrderCreate req) throws Exception {
        Thread t = Thread.currentThread();
        System.out.printf("POST /orders/enrich served by %s (virtual=%s)%n", t, t.isVirtual());

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // validate: wrap void -> Callable<Void>
            StructuredTaskScope.Subtask<Void> validateTask = scope.fork(() -> {
                validate(req);
                return null;
            });

            // fetch price
            StructuredTaskScope.Subtask<BigDecimal> priceTask = scope.fork(() -> fetchPrice(req.ticker()));

            // wait for both to finish (rely on per-op timeouts)
            scope.join();

            // propagate any failure
            scope.throwIfFailed();

            // safe now: both succeeded
            BigDecimal price = priceTask.get();

            // persist
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
                    STR."\{Thread.currentThread()} (virtual=\{Thread.currentThread().isVirtual()})"
            );
        }
    }

    private void validate(OrderCreate req) {
        if (req.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        boolean exists = repo.existsByTicker(req.ticker());
        if (!exists) {
            throw new IllegalArgumentException("ticker not found in DB");
        }
    }

    private BigDecimal fetchPrice(String ticker) throws Exception {
        URI uri = URI.create(STR."https://api.binance.com/api/v3/ticker/price?symbol=\{ticker}");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(3))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(STR."Price API failed: \{resp.statusCode()}");
        }

        JsonNode json = mapper.readTree(resp.body());
        return new BigDecimal(json.get("price").asText());
    }
}
