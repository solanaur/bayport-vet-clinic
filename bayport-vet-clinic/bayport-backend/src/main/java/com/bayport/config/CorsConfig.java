package com.bayport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Value("${spring.web.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,file://}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET","POST","PUT","DELETE","OPTIONS","PATCH","HEAD")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    // Resource handlers moved to WebConfig to avoid conflicts
    // This class now only handles CORS configuration
}
