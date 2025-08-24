package orders.api.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
public class Order {

    @Id
    @GeneratedValue
    private UUID id;
    private String ticker;
    private int quantity;
    private double price;
    private Instant createdAt = Instant.now();
}
