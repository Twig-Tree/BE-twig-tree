package com.tree.twig_tree.domain.tree.controller;

import com.tree.twig_tree.domain.tree.dto.TreeResDTO;
import com.tree.twig_tree.domain.tree.exception.code.TreeSuccessCode;
import com.tree.twig_tree.domain.tree.service.TreeService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import com.tree.twig_tree.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "트리 API", description = "트리 생성, 조회, 수정 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/trees")
public class TreeController {

    private final TreeService treeService;

    /**
     * 모든 트리 조회
     * @return List<Tree>
     */
    @GetMapping
    @Operation(summary = "모든 트리 조회", description = "모든 트리를 조회합니다.")
    public ApiResponse<List<TreeResDTO.GetTree>> getAllTrees() {
        BaseSuccessCode code = TreeSuccessCode.TREES_FOUND;
        return ApiResponse.onSuccess(code, treeService.getAllTrees());
    }

    /**
     * 트리 정보 상세 조회 (노드 정보X)
     * @param treeId
     * @return Tree
     */
    @Operation(summary = "단일 트리 상세 조회", description = "특정 트리 정보를 조회합니다.")
    @GetMapping("/{treeId}")
    public ApiResponse<TreeResDTO.GetTree> getTree(@PathVariable Long treeId) {
        BaseSuccessCode code = TreeSuccessCode.TREE_FOUND;
        return ApiResponse.onSuccess(code, treeService.getTree(treeId));
    }

    /**
     * 트리 생성
     * @param name
     * @return treeId
     */
    @Operation(summary = "새로운 트리 생성", description = "새로운 트리를 생성합니다.")
    @PostMapping
    public ApiResponse<TreeResDTO.TreeId> createTree(@RequestParam String name) {
        BaseSuccessCode code = TreeSuccessCode.TREE_CREATED;
        return ApiResponse.onSuccess(code, treeService.createTree(name));
    }
}
