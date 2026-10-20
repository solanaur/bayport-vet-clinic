package com.bayport.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serves the clinic HTML and assets from {@code BAYPORT_FRONTEND_DIR} so staff browsers
 * open one address on the clinic PC. API routes stay on the controllers.
 */
@Configuration
public class ClinicFrontendConfig implements WebMvcConfigurer {

    @Value("${bayport.frontend.dir:}")
    private String frontendDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (frontendDir == null || frontendDir.isBlank()) {
            return;
        }
        Path root = Path.of(frontendDir).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/*.html", "/favicon.ico")
                .addResourceLocations(location);
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location + "assets/");
        registry.addResourceHandler("/css/**")
                .addResourceLocations(location + "css/");
        registry.addResourceHandler("/js/**")
                .addResourceLocations(location + "js/");
    }
}
