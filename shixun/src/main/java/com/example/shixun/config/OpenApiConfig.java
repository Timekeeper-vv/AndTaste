package com.example.shixun.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shixunOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("商品与用户管理系统 API")
                        .description("提供商品和用户的增删改查接口，以及用户登录功能")
                        .version("1.0.0")
                        .contact(new Contact().name("admin").email("admin@test.com")));
    }
}