package com.kasha.orderprocessing.service;

import com.kasha.orderprocessing.dto.request.CreateProductRequest;
import com.kasha.orderprocessing.dto.response.ProductResponse;
import com.kasha.orderprocessing.entity.Product;
import com.kasha.orderprocessing.exception.ProductNotFoundException;
import com.kasha.orderprocessing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<ProductResponse> getAll() {
        return productRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(value = "products", key = "#id")
    public ProductResponse getById(Long id) {
        return toResponse(productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id)));
    }

    public ProductResponse create(CreateProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .build();
        return toResponse(productRepository.save(product));
    }

    @CacheEvict(value = "products", key = "#id")
    public void evictCache(Long id) {
        // Evicts stale product entry after stock update
    }

    private ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .stockQuantity(p.getStockQuantity())
                .build();
    }
}
