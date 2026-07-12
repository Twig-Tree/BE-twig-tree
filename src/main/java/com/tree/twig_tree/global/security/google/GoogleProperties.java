package com.tree.twig_tree.global.security.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix="google")
public record GoogleProperties(
        // List 인 이유는 현재는 client profile 이 web 밖에 없지만 나중에 app profile 등이 추가될 수도 있기 때문
        // 솔직히 의미 없긴 함
        List<String> clientIds
) {
}
