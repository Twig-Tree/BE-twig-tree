package com.tree.twig_tree.global.apiPayload.swagger;

import com.tree.twig_tree.domain.auth.controller.AuthController;
import com.tree.twig_tree.domain.chat.controller.ChatController;
import com.tree.twig_tree.domain.node.controller.NodeController;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeOperationCustomizerTest {

    private final ErrorCodeOperationCustomizer customizer = new ErrorCodeOperationCustomizer();

    @Test
    @DisplayName("@ApiErrorCodeExample 로 지정한 도메인 에러코드가 상태코드별로 응답 예시에 묶인다")
    void groupsDomainErrorCodesByStatus() throws NoSuchMethodException {
        Method method = NodeController.class.getMethod(
                "createNode", Long.class, com.tree.twig_tree.domain.node.dto.NodeReqDTO.CreateNode.class);
        Operation operation = customize(NodeController.class, null, method);

        ApiResponses responses = operation.getResponses();

        assertThat(exampleKeys(responses, "404")).containsExactlyInAnyOrder(
                "TREE404-1", "NODE404-2", "NODE404-3");
        assertThat(exampleKeys(responses, "409")).containsExactlyInAnyOrder(
                "NODE409-1", "NODE409-2");
        // @Valid 파라미터가 있으므로 공통 400 도 자동으로 붙는다.
        assertThat(exampleKeys(responses, "400")).contains("COMMON400-1");
        // 인증이 필요한 엔드포인트이므로 공통 401/403 이 자동으로 붙는다.
        assertThat(exampleKeys(responses, "401")).contains("COMMON401-1");
        assertThat(exampleKeys(responses, "403")).contains("COMMON403-1");
        // 모든 엔드포인트에 공통 500 이 붙는다.
        assertThat(exampleKeys(responses, "500")).contains("COMMON500-1");
    }

    @Test
    @DisplayName("@PublicApi 가 붙은 엔드포인트는 공통 401/403 예시가 붙지 않는다")
    void publicApiSkipsAuthErrorCodes() throws NoSuchMethodException {
        Method method = AuthController.class.getMethod(
                "googleLogin", com.tree.twig_tree.domain.auth.dto.AuthReqDTO.GoogleLogin.class);
        Operation operation = customize(AuthController.class, null, method);

        ApiResponses responses = operation.getResponses();

        // AUTH401-1(유효하지 않은 구글 토큰)은 도메인 에러코드이므로 여전히 노출되어야 하지만,
        // 인증 필터가 붙이는 공통 COMMON401-1 은 @PublicApi 엔드포인트에는 붙지 않아야 한다.
        assertThat(exampleKeys(responses, "401")).containsExactly("AUTH401-1");
        assertThat(responses.get("403")).isNull();
        assertThat(exampleKeys(responses, "500")).contains("COMMON500-1");
    }

    @Test
    @DisplayName("@Valid 파라미터가 없는 메서드도 명시적으로 추가한 mock 검증 에러코드가 노출된다")
    void explicitDomainErrorCodeIsExposedWithoutValidParameter() throws NoSuchMethodException {
        Method method = ChatController.class.getMethod(
                "generateTreeFromFile", Long.class,
                org.springframework.web.multipart.MultipartFile.class, String.class,
                com.tree.twig_tree.domain.chat.client.LlmProvider.class, String.class);
        Operation operation = customize(ChatController.class, null, method);

        ApiResponses responses = operation.getResponses();

        // mock 시나리오 검증 실패(ChatErrorCode.INVALID_MOCK_SCENARIO)는 @Valid 파라미터로는 감지되지 않으므로
        // 컨트롤러에 명시적으로 붙인 CHAT400-12 가 노출되어야 한다.
        assertThat(exampleKeys(responses, "400")).contains("CHAT400-12");
    }

    private Operation customize(Class<?> controllerType, Object bean, Method method) {
        HandlerMethod handlerMethod = new HandlerMethod(instantiate(controllerType), method);
        Operation operation = new Operation().responses(new ApiResponses());
        return customizer.customize(operation, handlerMethod);
    }

    /** 컨트롤러 생성자의 의존성은 실제 호출되지 않으므로 null 로 채운다. */
    private Object instantiate(Class<?> controllerType) {
        try {
            Object[] args = new Object[controllerType.getDeclaredConstructors()[0].getParameterCount()];
            return controllerType.getDeclaredConstructors()[0].newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.util.Set<String> exampleKeys(ApiResponses responses, String status) {
        io.swagger.v3.oas.models.responses.ApiResponse response = responses.get(status);
        if (response == null) {
            return java.util.Set.of();
        }
        Content content = response.getContent();
        MediaType mediaType = content.get("application/json");
        return mediaType.getExamples().keySet();
    }
}
