package com.tree.twig_tree.global.security.cookie;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "auth.cookie")
public record AuthCookieProperties(@DefaultValue("true") boolean secure) {
}
