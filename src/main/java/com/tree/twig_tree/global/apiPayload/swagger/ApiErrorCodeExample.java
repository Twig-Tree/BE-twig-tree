package com.tree.twig_tree.global.apiPayload.swagger;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;

import java.lang.annotation.*;

/**
 * 컨트롤러 메서드가 실제로 던질 수 있는 에러코드를 Swagger 응답 예시로 노출한다.
 * <p>{@link #only()} 를 비워두면 {@link #value()} 의 모든 상수를 노출하고,
 * 일부만 지정하면 그 enum 상수({@code name()})들만 노출한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiErrorCodeExamples.class)
public @interface ApiErrorCodeExample {
    Class<? extends BaseErrorCode> value();
    String[] only() default {};
}
