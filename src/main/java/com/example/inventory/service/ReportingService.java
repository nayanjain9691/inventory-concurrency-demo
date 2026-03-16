package com.example.inventory.service;

import com.example.inventory.domain.Product;
import com.example.inventory.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportingService {

    private final ProductRepository productRepository;

    public ReportingService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public int readStockReadUncommitted(String sku) {
        Product product = productRepository.findBySku(sku).orElseThrow();
        return product.getStock();
    }

    @Transactional
    public void updateStockWithoutCommit(String sku, int delta) {
        Product product = productRepository.findBySku(sku).orElseThrow();
        product.setStock(product.getStock() + delta);
        sleep(10000);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int nonRepeatableRead(String sku, long delayMillis) {
        Product p1 = productRepository.findBySku(sku).orElseThrow();
        int first = p1.getStock();
        sleep(delayMillis);
        Product p2 = productRepository.findBySku(sku).orElseThrow();
        int second = p2.getStock();
        return second - first;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public long phantomReadCount(long delayMillis) {
        long count1 = productRepository.count();
        sleep(delayMillis);
        long count2 = productRepository.count();
        return count2 - count1;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

