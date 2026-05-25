package com.tree.twig_tree.domain.node.controller;

import com.tree.twig_tree.domain.node.service.NodeService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import com.tree.twig_tree.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

    @GetMapping("/test")
    public ApiResponse<String> test(
            @RequestParam Long testId
    ) {
        BaseSuccessCode code = GeneralSuccessCode.OK;
        return ApiResponse.onSuccess(code, nodeService.test());
    }



}
