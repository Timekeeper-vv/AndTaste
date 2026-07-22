package com.example.shixun.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class CreativeAssetWebConfig implements WebMvcConfigurer {
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.mediaType("glb", MediaType.parseMediaType("model/gltf-binary"));
        configurer.mediaType("gltf", MediaType.parseMediaType("model/gltf+json"));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path publicDir = Path.of(System.getProperty("user.dir"), "..", "shixun-vue", "public").normalize().toAbsolutePath();
        registry.addResourceHandler("/generated/**")
                .addResourceLocations(publicDir.resolve("generated").toUri().toString())
                .setCachePeriod(0);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(publicDir.resolve("uploads").toUri().toString())
                .setCachePeriod(0);
    }
}
