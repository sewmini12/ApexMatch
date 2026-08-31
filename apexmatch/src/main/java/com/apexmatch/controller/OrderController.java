package com.apexmatch.controller;

import com.apexmatch.dto.OrderRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping
    public String createOrder(@RequestBody OrderRequest request) {

        System.out.println("New Order Received");
        System.out.println("------------------");
        System.out.println("User: " + request.getUserId());
        System.out.println("Symbol: " + request.getSymbol());
        System.out.println("Side: " + request.getSide());
        System.out.println("Type: " + request.getType());
        System.out.println("Price: " + request.getPrice());
        System.out.println("Quantity: " + request.getQuantity());

        return "Order received successfully";
    }
}