package com.tree.twig_tree.global.apiPayload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

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

    // 성공한 경우
    public static <T> ApiResponse<T> onSuccess(BaseSuccessCode code, T data) {
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), data );
    }

    // 실패한 경우
    public static <T> ApiResponse<T> onFailure(BaseErrorCode code, T data) {
        return new ApiResponse<>(false, code.getCode(), code.getMessage(), data );
    }


}
