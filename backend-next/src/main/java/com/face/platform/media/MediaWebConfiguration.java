package com.face.platform.media;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class MediaWebConfiguration implements WebMvcConfigurer {

    private final MediaStorageService storageService;

    public MediaWebConfiguration(MediaStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation = storageService.storageRoot().toUri().toString();
        if (!resourceLocation.endsWith("/")) resourceLocation += "/";
        registry.addResourceHandler("/upload/**")
            .addResourceLocations(resourceLocation)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
