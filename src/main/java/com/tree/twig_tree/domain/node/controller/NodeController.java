package com.tree.twig_tree.domain.node.controller;

import com.tree.twig_tree.domain.node.dto.NodeReqDTO;
import com.tree.twig_tree.domain.node.dto.NodeResDTO;
import com.tree.twig_tree.domain.node.exception.code.NodeSuccessCode;
import com.tree.twig_tree.domain.node.service.NodeService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Node", description = "노드 생성, 조회, 수정 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/trees/{treeId}/nodes")
public class NodeController {

    private final NodeService nodeService;

    /**
     * 새로운 노드 생성
     * @param treeId
     * @param dto
     * @return
     */
    @Operation(summary = "새로운 노드 생성", description = "트리에 새로운 노드를 생성합니다.")
    @PostMapping()
    public ApiResponse<NodeResDTO.GetNode> createNode(
            @PathVariable Long treeId, @RequestBody @Valid NodeReqDTO.CreateNode dto) {
        BaseSuccessCode code = NodeSuccessCode.NODE_CREATED;
        return ApiResponse.onSuccess(code, nodeService.createNode(treeId, dto));

    }

    /**
     * 노드 제목 수정
     * PATCH /trees/{treeId}/nodes/{nodeId}
     * @param treeId
     * @param nodeId
     * @param dto
     * @return
     */
    @Operation(summary = "노드 제목 수정", description = "특정 노드의 제목을 수정합니다.")
    @PatchMapping("/{nodeId}")
    public ApiResponse<NodeResDTO.GetNode> editNodeName(@PathVariable Long treeId, @PathVariable Long nodeId,
                                              @RequestBody @Valid NodeReqDTO.EditNodeName dto) {
        BaseSuccessCode code = NodeSuccessCode.NODE_UPDATED;
        return ApiResponse.onSuccess(code, nodeService.editNodeName(treeId, nodeId, dto));
    }

    /**
     * 노드 삭제
     * @param nodeId
     */
    @Operation(summary = "노드 삭제", description = "특정 노드를 삭제합니다. ")
    @DeleteMapping("/{nodeId}")
    public ApiResponse<Void> deleteNode(@PathVariable Long treeId, @PathVariable Long nodeId){
        BaseSuccessCode code = NodeSuccessCode.NODE_DELETED;
        nodeService.deleteNode(treeId, nodeId);
        return ApiResponse.onSuccess(code, null);
    }

    /**
     * 단일 노드 상세 조회
     * GET /trees/{treeId}/nodes/{nodeId}
     * @param treeId
     * @param nodeId
     * @return
     */
    @Operation(summary = "단일 노드 상세 조회", description = "특정 노드의 상세 정보를 조회합니다.")
    @GetMapping("/{nodeId}")
    public ApiResponse<NodeResDTO.GetNode> getNode(@PathVariable Long treeId, @PathVariable Long nodeId) {
        BaseSuccessCode code = NodeSuccessCode.NODE_FOUND;
        return ApiResponse.onSuccess(code, nodeService.getNode(treeId, nodeId));
    }

    /**
     * 트리의 전체 노드 조회
     * GET /trees/{treeId}/nodes
     * @param treeId
     * @return
     */
    @GetMapping
    @Operation(summary = "트리 전체 노드 조회", description = "트리의 모든 노드를 조회합니다.")
    public ApiResponse<NodeResDTO.GetTree> getFullTree(@PathVariable Long treeId) {
        BaseSuccessCode code = NodeSuccessCode.NODES_FOUND;
        return ApiResponse.onSuccess(code, nodeService.getFullTreeNodes(treeId));
    }

    /**
     * 특정 루트 기준 서브트리 조회
     * GET /trees/{treeId}/nodes/{rootId}
     * @param treeId
     * @param rootId
     * @return
     */
    @GetMapping("/{rootId}/subtree")
    @Operation(summary = "서브트리 노드 조회", description = "특정 노드를 루트로 하는 서브트리의 모든 노드를 조회합니다.")
    public ApiResponse<List<NodeResDTO.GetNode>> getSubTree(@PathVariable Long treeId, @PathVariable Long rootId) {
        BaseSuccessCode code = NodeSuccessCode.NODES_FOUND;
        return ApiResponse.onSuccess(code, nodeService.getSubTreeNodes(treeId, rootId));
    }
}
