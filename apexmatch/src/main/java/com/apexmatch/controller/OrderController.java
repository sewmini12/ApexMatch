package com.apexmatch.controller;

import com.apexmatch.dto.ErrorResponse;
import com.apexmatch.dto.OrderRequest;
import com.apexmatch.model.Trade;
import com.apexmatch.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order submission and real-time matching operations")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(
            summary = "Submit a new order",
            description = "Submits a BUY or SELL order to the matching engine. If compatible resting counter-orders exist in the order book, trades are executed immediately according to Price-Time Priority. Any remaining unmatched quantity is placed on the order book."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Order processed successfully. Returns a list of executed trades (empty if the order rests unmatched in the book).",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = Trade.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation or business rule error (e.g. missing fields, non-positive quantity, invalid price)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping
    public List<Trade> createOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Order submission payload specifying user, symbol, side, type, price, and quantity",
                    required = true
            )
            @RequestBody OrderRequest request) {

        return orderService.processOrder(request);
    }
}