package com.tree.twig_tree.domain.node.controller;

import com.tree.twig_tree.domain.node.exception.NodeException;
import com.tree.twig_tree.domain.node.service.NodeService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import com.tree.twig_tree.global.apiPayload.code.GeneralErrorCode;
import com.tree.twig_tree.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

    @GetMapping("/error")
    @Operation(summary = "에러 핸들러 동작 테스트 API", description = "flag 값이 false이면 의도적으로 NodeException을 발생시킵니다.")
    public ApiResponse<String> testErrorHandler(@RequestParam(name = "flag") boolean flag) {

        if (!flag) {
            // 여기서 예외를 던지면 Controller Advice가 가로챕니다.
            throw new NodeException(GeneralErrorCode.BAD_REQUEST);
        }

        BaseSuccessCode code = GeneralSuccessCode.OK;
        return ApiResponse.onSuccess(code, "API가 성공적으로 호출되었습니다.");
    }



}
