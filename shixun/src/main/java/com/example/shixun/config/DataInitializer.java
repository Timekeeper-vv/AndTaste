package com.example.shixun.config;

import com.example.shixun.model.Product;
import com.example.shixun.model.User;
import com.example.shixun.service.ProductService;
import com.example.shixun.service.UserService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    ApplicationRunner initData(UserService userService, ProductService productService) {
        return args -> {
            if (userService.findAll().join().isEmpty()) {
                User u1 = new User(); u1.setUsername("zhangsan"); u1.setAge(25); u1.setEmail("zhangsan@example.com"); u1.setPhone("13800138001"); u1.setPassword("123456");
                User u2 = new User(); u2.setUsername("lisi");     u2.setAge(30); u2.setEmail("lisi@example.com");     u2.setPhone("13800138002"); u2.setPassword("123456");
                User u3 = new User(); u3.setUsername("wangwu");   u3.setAge(22); u3.setEmail("wangwu@example.com");   u3.setPhone("13800138003"); u3.setPassword("123456");
                userService.save(u1); userService.save(u2); userService.save(u3);
            }
            if (productService.findAll().join().isEmpty()) {
                Product p1 = new Product(null, "iPhone",     5999.0, 100, "Electronics", "Latest smartphone");
                Product p2 = new Product(null, "Sneakers",    299.0, 200, "Shoes",       "Comfortable sport shoes");
                Product p3 = new Product(null, "Laptop",     4599.0,  50, "Electronics", "Lightweight laptop");
                Product p4 = new Product(null, "Coffee Mug",   39.0, 500, "Household",   "Ceramic mug 350ml");
                productService.save(p1); productService.save(p2); productService.save(p3); productService.save(p4);
            }
        };
    }
}
