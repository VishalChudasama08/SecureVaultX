package com.securevaultx.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "securevaultx.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {
}
