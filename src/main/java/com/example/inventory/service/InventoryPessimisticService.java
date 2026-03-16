package com.example.inventory.service;

import com.example.inventory.domain.CustomerOrder;
import com.example.inventory.domain.Product;
import com.example.inventory.repository.CustomerOrderRepository;
import com.example.inventory.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryPessimisticService {

    private final ProductRepository productRepository;
    private final CustomerOrderRepository orderRepository;

    public InventoryPessimisticService(ProductRepository productRepository,
                                       CustomerOrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public CustomerOrder placeOrderPessimistic(String customerId, String sku, int qty) {
        Product product = productRepository.findBySkuForUpdate(sku)
                .orElseThrow(() -> new IllegalArgumentException("Unknown SKU " + sku));

        if (product.getStock() < qty) {
            throw new IllegalStateException("Insufficient stock");
        }

        product.setStock(product.getStock() - qty);

        CustomerOrder order = new CustomerOrder();
        order.setCustomerId(customerId);
        order.setProduct(product);
        order.setQuantity(qty);

        return orderRepository.save(order);
    }
}

