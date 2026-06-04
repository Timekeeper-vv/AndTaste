package com.example.shixun.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "商品实体")
public class Product {

    @Schema(description = "商品ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "商品名称", example = "苹果手机", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "商品价格（元）", example = "5999.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double price;

    @Schema(description = "库存数量", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer stock;

    @Schema(description = "商品分类", example = "电子产品")
    private String category;

    @Schema(description = "商品描述", example = "最新款苹果手机，性能强劲")
    private String description;

    public Product() {
    }

    public Product(Long id, String name, Double price, Integer stock, String category, String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.category = category;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}