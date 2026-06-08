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
                        .title("智慧养殖场管理与溯源系统 API")
                        .description("覆盖牲畜全生命周期的数字化管理：圈舍、批次、个体档案、生产事件记录、全链路溯源")
                        .version("1.0.0")
                        .contact(new Contact().name("admin").email("admin@test.com")));
    }
}