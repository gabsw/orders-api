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

@Service
@RequiredArgsConstructor
public class OrderEnrichmentService {

    private final OrderRepository repo;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrderEnriched createEnriched(OrderCreate req) throws Exception {
        // Log thread info
        Thread t = Thread.currentThread();
        System.out.printf("POST /orders/enrich served by %s (virtual=%s)%n", t, t.isVirtual());

        // Start two virtual threads manually using Loom
        var validateThread = Thread.startVirtualThread(() -> validate(req));

        final BigDecimal[] priceHolder = new BigDecimal[1];
        var priceThread = Thread.startVirtualThread(() -> {
            try {
                priceHolder[0] = fetchPrice(req.ticker());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for both threads to finish (propagates exceptions)
        validateThread.join();
        priceThread.join();

        // Persist order (still blocking, but fine with virtual threads)
        BigDecimal price = priceHolder[0];

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

    private void validate(OrderCreate req) {
        if (req.ticker() == null || req.ticker().isBlank()) {
            throw new IllegalArgumentException("ticker is required");
        }
        if (req.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        try {
            // simulate blocking validation I/O
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Validation interrupted", e);
        }
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
        return new BigDecimal(json.get("price").asText());
    }
}
