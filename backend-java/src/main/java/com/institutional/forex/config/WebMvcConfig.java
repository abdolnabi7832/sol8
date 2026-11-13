package com.institutional.forex.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * WebMvcConfig: Enables Spring Boot to serve static frontend Single-Page Application (SPA)
 * assets from classpath:/static/ alongside REST APIs on a unified port (8080).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        // If file exists, return it; otherwise forward to index.html for SPA routing
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        // Don't forward /api/ or /ws-forex to index.html
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws-forex")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
