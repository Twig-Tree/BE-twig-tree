package com.tree.twig_tree.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {
}
