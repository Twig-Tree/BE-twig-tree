package com.tree.twig_tree.global.apiPayload.swagger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증 없이 호출 가능한 엔드포인트({@code SecurityConfig} 의 permitAll 대상)임을 표시한다.
 * Swagger 문서에서 공통 401/403 에러 예시를 자동으로 붙이지 않기 위해 사용한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
