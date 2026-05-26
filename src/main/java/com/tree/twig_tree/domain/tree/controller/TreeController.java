package com.tree.twig_tree.domain.tree.controller;

import com.tree.twig_tree.domain.tree.dto.TreeResDTO;
import com.tree.twig_tree.domain.tree.service.TreeService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import com.tree.twig_tree.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "트리 API", description = "트리 생성, 조회, 수정 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/trees")
public class TreeController {

    private final TreeService treeService;

//    /**
//     * 모든 트리 조회
//     */
//    @GetMapping
//    public void getAllTrees() {
//
//    }
//
//    /**
//     * 트리 정보 조회 (노드 정보X)
//     */
//    @GetMapping
//    public void getTreeInfo() {
//
//    }

    /**
     * 트리 생성
     */
    @Operation(summary = "새로운 트리 생성", description = "새로운 트리를 생성합니다.")
    @PostMapping
    public ApiResponse<TreeResDTO.TreeId> createTree(@RequestParam String name) {
        BaseSuccessCode code = GeneralSuccessCode.OK;
        return ApiResponse.onSuccess(code, treeService.createTree(name));
    }


}
