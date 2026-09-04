package com.apexmatch.controller;

import com.apexmatch.model.Trade;
import com.apexmatch.dto.OrderRequest;
import com.apexmatch.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public List<Trade> createOrder(
            @RequestBody OrderRequest request) {

        return orderService.processOrder(request);
    }
}