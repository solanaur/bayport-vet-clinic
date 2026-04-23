package com.bayport.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@ConditionalOnProperty(name = "bayport.storage.provider", havingValue = "local", matchIfMissing = true)
public class WebConfig implements WebMvcConfigurer {

    /**
     * Root directory where uploaded pet photos and other assets are stored.
     * This is kept in sync with {@code bayport.upload-dir} used by the
     * {@link com.bayport.web.ApiControllers} upload endpoint so that
     * URLs like "/uploads/123_photo.jpg" resolve correctly in the browser.
     */
    @Value("${bayport.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve the configured upload directory to an absolute file URI.
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        
        // Ensure the location ends with a slash for proper resource resolution
        if (!location.endsWith("/")) {
            location += "/";
        }

        // Map /uploads/** to the configured upload directory
        // Path.toUri() already returns file:// URI format, so use it directly
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCachePeriod(3600); // Cache for 1 hour

        // For backward compatibility with any existing files in the legacy
        // "./uploads" folder, also keep the old handler.
        registry.addResourceHandler("/legacy-uploads/**")
                .addResourceLocations("file:uploads/");
    }
}
