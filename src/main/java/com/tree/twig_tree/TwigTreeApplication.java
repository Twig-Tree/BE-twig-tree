package com.tree.twig_tree;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.TimeZone;

@ConfigurationPropertiesScan
@EnableJpaAuditing
@SpringBootApplication
public class TwigTreeApplication {

	public static void main(String[] args) {
		// LocalDateTime.now() 기반 값(BaseEntity의 createdAt/updatedAt 등)이 실행 환경(로컬/컨테이너)의
		// 타임존에 따라 달라지지 않도록, JVM 기본 시간대를 UTC로 고정한다.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(TwigTreeApplication.class, args);
	}

}
