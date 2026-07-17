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
                        .title("之间味道文创产品智能体平台 API")
                        .description("覆盖创意设计、供应链、打样生产、仓储物流、审批流程和经营管理的一体化业务系统")
                        .version("1.0.0")
                        .contact(new Contact().name("admin").email("admin@andtaste.com")));
    }
}
