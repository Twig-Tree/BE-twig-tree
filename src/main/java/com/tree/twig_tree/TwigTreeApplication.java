package com.tree.twig_tree;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class TwigTreeApplication {

	public static void main(String[] args) {
		SpringApplication.run(TwigTreeApplication.class, args);
	}

}
