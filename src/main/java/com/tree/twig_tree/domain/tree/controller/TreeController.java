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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Tree", description = "트리 생성, 조회, 수정 관련 API")
@RestController
@RequiredArgsConstructor
public class TreeController {

    private final TreeService treeService;

    /**
     * 모든 트리 조회
     * @return List<Tree>
     */
    @GetMapping("/trees")
    @Operation(summary = "모든 트리 조회", description = "모든 트리를 조회합니다.")
    public ResponseEntity<ApiResponse<List<TreeResDTO.TreeId>>> getAllTrees() {
        BaseSuccessCode code = TreeSuccessCode.TREES_FOUND;
        return ApiResponse.onSuccess(code, treeService.getAllTrees());
    }

    /**
     * 트리 생성
     * @return treeId
     */
    @Operation(summary = "새로운 트리 생성", description = "특정 워크스페이스에 트리를 생성합니다. 하나의 워크스페이스에 하나의 트리만 생성가능합니다.")
    @PostMapping("/workspaces/{workspaceId}/trees")
    public ResponseEntity<ApiResponse<TreeResDTO.TreeId>> createTree(
            @PathVariable Long workspaceId
    ) {
        BaseSuccessCode code = TreeSuccessCode.TREE_CREATED;
        return ApiResponse.onSuccess(code, treeService.createTree(workspaceId));
    }

    /**
     * 트리 삭제
     * @param treeId
     */
    @Operation(summary = "트리 삭제", description = "트리를 삭제합니다.")
    @DeleteMapping("/workspaces/{workspaceId}/trees/{treeId}")
    public ResponseEntity<ApiResponse<Void>> deleteTree(
            @PathVariable Long workspaceId,
            @PathVariable Long treeId) {
        BaseSuccessCode code = TreeSuccessCode.TREE_DELETED;
        treeService.deleteTree(workspaceId, treeId);
        return ApiResponse.onSuccess(code, null);
    }
}
