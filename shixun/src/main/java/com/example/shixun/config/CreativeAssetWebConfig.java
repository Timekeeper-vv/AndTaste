package com.example.shixun.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CreativeAssetWebConfig implements WebMvcConfigurer {
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.mediaType("glb", MediaType.parseMediaType("model/gltf-binary"));
        configurer.mediaType("gltf", MediaType.parseMediaType("model/gltf+json"));
    }

    /**
     * Keep legacy asset paths out of Spring's default static-resource handler.
     *
     * Older deployments wrote user uploads and generated files below
     * {@code /uploads} and {@code /generated}.  If those directories happen to
     * remain on the classpath or under a configured static location, Boot's
     * catch-all handler could expose them without authentication.  Registering
     * an intentionally empty handler for these paths makes requests resolve to
     * a normal 404; private assets are served only by CreativeAiController's
     * authenticated endpoints.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/generated/**", "/uploads/**")
                .addResourceLocations("classpath:/__private-assets-disabled__/");
    }

}
