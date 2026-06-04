package com.example.shixun.service;

import com.example.shixun.mapper.ProductMapper;
import com.example.shixun.model.Product;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ProductService {

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Async
    public CompletableFuture<List<Product>> findAll() {
        return CompletableFuture.completedFuture(productMapper.findAll());
    }

    @Async
    public CompletableFuture<Product> findById(Long id) {
        return CompletableFuture.completedFuture(productMapper.findById(id));
    }

    @Async
    public CompletableFuture<Product> save(Product product) {
        productMapper.insert(product);
        return CompletableFuture.completedFuture(product);
    }

    @Async
    public CompletableFuture<Product> update(Long id, Product product) {
        if (productMapper.findById(id) == null) return CompletableFuture.completedFuture(null);
        product.setId(id);
        productMapper.update(product);
        return CompletableFuture.completedFuture(product);
    }

    @Async
    public CompletableFuture<Boolean> delete(Long id) {
        return CompletableFuture.completedFuture(productMapper.deleteById(id) > 0);
    }
}
