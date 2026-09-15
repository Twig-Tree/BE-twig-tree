package com.tree.twig_tree.global.apiPayload.swagger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link ApiErrorCodeExample} 를 한 메서드에 여러 번 달기 위한 컨테이너.
 * 컴파일러가 자동으로 사용함
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodeExamples {
    ApiErrorCodeExample[] value();
}
