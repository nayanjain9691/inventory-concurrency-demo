package com.example.inventory.service;

import com.example.inventory.domain.CustomerOrder;
import com.example.inventory.domain.Product;
import com.example.inventory.repository.CustomerOrderRepository;
import com.example.inventory.repository.ProductRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final CustomerOrderRepository orderRepository;

    public InventoryService(ProductRepository productRepository,
                            CustomerOrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public CustomerOrder placeOrderOptimistic(String customerId, String sku, int qty) {
        Product product = productRepository.findBySku(sku)
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

    @Transactional
    public CustomerOrder placeOrderOptimisticWithRetry(String customerId, String sku, int qty) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return doPlaceOrder(customerId, sku, qty);
            } catch (OptimisticLockException ex) {
                if (attempts >= 3) {
                    throw ex;
                }
            }
        }
    }

    @Transactional
    protected CustomerOrder doPlaceOrder(String customerId, String sku, int qty) {
        Product product = productRepository.findBySku(sku)
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

