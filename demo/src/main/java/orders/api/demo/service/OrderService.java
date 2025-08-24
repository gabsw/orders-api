package orders.api.demo.service;

import lombok.RequiredArgsConstructor;
import orders.api.demo.model.Order;
import orders.api.demo.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;

    public List<Order> getAllOrders() {
        return repository.findAll();
    }

    public Order getOrderById(UUID id) {
        return repository.findById(id).orElseThrow();
    }

    public Order createOrder(Order order) {
        return repository.save(order);
    }
}

