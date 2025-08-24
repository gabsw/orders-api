package orders.api.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderEnriched(
        UUID id,
        String ticker,
        int quantity,
        BigDecimal finalPrice,
        Instant createdAt,
        String thread
) {}
