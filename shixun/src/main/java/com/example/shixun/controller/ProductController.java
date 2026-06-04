package com.example.shixun.controller;

import com.example.shixun.model.Product;
import com.example.shixun.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/products")
@Tag(name = "商品管理", description = "商品的增删改查接口")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "获取所有商品", description = "返回全部商品列表")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public CompletableFuture<List<Product>> findAll() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取商品", description = "根据商品ID查询单个商品")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "商品不存在")
    })
    public CompletableFuture<ResponseEntity<Product>> findById(
            @Parameter(description = "商品ID", example = "1") @PathVariable Long id) {
        return productService.findById(id)
            .thenApply(product -> {
                if (product == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
                }
                return ResponseEntity.ok(product);
            });
    }

    @PostMapping
    @Operation(summary = "新增商品", description = "创建一个新商品，名称、价格、库存为必填项")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败")
    })
    public CompletableFuture<ResponseEntity<Product>> create(@RequestBody Product product) {
        validateProduct(product);
        return productService.save(product)
            .thenApply(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新商品", description = "根据ID更新商品信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "404", description = "商品不存在")
    })
    public CompletableFuture<ResponseEntity<Product>> update(
            @Parameter(description = "商品ID", example = "1") @PathVariable Long id,
            @RequestBody Product product) {
        validateProduct(product);
        return productService.update(id, product)
            .thenApply(updated -> {
                if (updated == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
                }
                return ResponseEntity.ok(updated);
            });
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品", description = "根据ID删除商品")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "商品不存在")
    })
    public CompletableFuture<ResponseEntity<Void>> delete(
            @Parameter(description = "商品ID", example = "1") @PathVariable Long id) {
        return productService.delete(id)
            .thenApply(deleted -> {
                if (!deleted) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
                }
                return ResponseEntity.<Void>noContent().build();
            });
    }

    private void validateProduct(Product product) {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product name must not be blank");
        }
        if (product.getPrice() == null || product.getPrice() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be greater than 0");
        }
        if (product.getStock() == null || product.getStock() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock must not be negative");
        }
    }
}
