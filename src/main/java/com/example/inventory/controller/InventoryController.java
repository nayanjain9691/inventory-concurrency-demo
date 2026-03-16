package com.example.inventory.controller;

import com.example.inventory.domain.CustomerOrder;
import com.example.inventory.service.InventoryPessimisticService;
import com.example.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService optimisticService;
    private final InventoryPessimisticService pessimisticService;

    public InventoryController(InventoryService optimisticService,
                               InventoryPessimisticService pessimisticService) {
        this.optimisticService = optimisticService;
        this.pessimisticService = pessimisticService;
    }

    @PostMapping("/optimistic/order")
    public CustomerOrder orderOptimistic(@RequestParam String customerId,
                                         @RequestParam String sku,
                                         @RequestParam int qty) {
        return optimisticService.placeOrderOptimistic(customerId, sku, qty);
    }

    @PostMapping("/pessimistic/order")
    public CustomerOrder orderPessimistic(@RequestParam String customerId,
                                          @RequestParam String sku,
                                          @RequestParam int qty) {
        return pessimisticService.placeOrderPessimistic(customerId, sku, qty);
    }
}

