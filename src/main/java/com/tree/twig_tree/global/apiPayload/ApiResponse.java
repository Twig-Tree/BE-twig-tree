package com.tree.twig_tree.global.apiPayload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "data"}) // JSON 내 순서 지정
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private final Boolean isSuccess;

    @JsonProperty("code")
    private final String code;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("data")
    private T data;

    // body만 필요한 경우 (예: HttpServletResponse에 직접 쓰는 security handler)
    public static <T> ApiResponse<T> success(BaseSuccessCode code, T data) {
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), data);
    }

    public static <T> ApiResponse<T> failure(BaseErrorCode code, T data) {
        return new ApiResponse<>(false, code.getCode(), code.getMessage(), data);
    }

    // 컨트롤러에서 상태코드까지 함께 반환하는 경우
    public static <T> ResponseEntity<ApiResponse<T>> onSuccess(BaseSuccessCode code, T data) {
        return ResponseEntity.status(code.getStatus()).body(success(code, data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> onFailure(BaseErrorCode code, T data) {
        return ResponseEntity.status(code.getStatus()).body(failure(code, data));
    }


}
