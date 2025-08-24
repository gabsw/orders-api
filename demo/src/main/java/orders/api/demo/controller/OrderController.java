package orders.api.demo.controller;

import lombok.RequiredArgsConstructor;
import orders.api.demo.model.Order;
import orders.api.demo.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;

    @GetMapping
    public List<Order> listOrders() {
        Thread t = Thread.currentThread();
        System.out.printf("GET /orders served by %s (virtual=%s)%n", t, t.isVirtual());
        return service.getAllOrders();
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable UUID id) {
        return service.getOrderById(id);
    }

    @PostMapping
    public Order createOrder(@RequestBody Order order) {
        Thread t = Thread.currentThread();
        System.out.printf("POST /orders served by %s (virtual=%s)%n", t, t.isVirtual());
        return service.createOrder(order);
    }
}

