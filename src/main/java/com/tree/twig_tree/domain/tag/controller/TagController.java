package com.tree.twig_tree.domain.tag.controller;

import com.tree.twig_tree.domain.tag.dto.TagReqDTO;
import com.tree.twig_tree.domain.tag.dto.TagResDTO;
import com.tree.twig_tree.domain.tag.exception.code.TagSuccessCode;
import com.tree.twig_tree.domain.tag.service.TagService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class TagController {

    private final TagService tagService;

    // 태그 생성
    @Operation(summary = "태그 생성", description = "트리의 태그를 생성합니다.")
    @PostMapping("/trees/{treeId}/tags")
    public ResponseEntity<ApiResponse<TagResDTO.GetTag>> createTag(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long treeId,
            @RequestBody TagReqDTO.CreateTag dto
    ) {
        BaseSuccessCode code = TagSuccessCode.TAG_CREATED;
        return ApiResponse.onSuccess(code, tagService.createTag(memberId, treeId, dto));
    }

    // 태그 이름 수정
    @Operation(summary = "태그 아름 수정", description = "트리의 태그 이름을 수정합니다.")
    @PatchMapping("/trees/{treeId}/tags/{tagId}")
    public ResponseEntity<ApiResponse<TagResDTO.GetTag>> updateTagName(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long treeId,
            @PathVariable Long tagId,
            @RequestBody TagReqDTO.EditTagName dto
    ) {
        BaseSuccessCode code = TagSuccessCode.TAG_UPDATED;
        return ApiResponse.onSuccess(code, tagService.updateTagName(memberId, treeId, tagId, dto));
    }

    // 태그 삭제
    @Operation(summary = "태그 삭제", description = "트리의 태그를 삭제합니다.")
    @DeleteMapping("/trees/{treeId}/tags/{tagId}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long treeId,
            @PathVariable Long tagId
    )
    {
        BaseSuccessCode code = TagSuccessCode.TAG_DELETED;
        return ApiResponse.onSuccess(code, tagService.deleteTag(memberId, treeId, tagId));
    }

    // 트리의 태그 목록 조회
    @Operation(summary = "트리의 태그 목록 조회", description = "트리에 속한 모든 태그 목록을 조회합니다.")
    @GetMapping("/trees/{treeId}/tags")
    public ResponseEntity<ApiResponse<List<TagResDTO.GetTag>>> getTreeTags(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long treeId
    ) {
        BaseSuccessCode code = TagSuccessCode.TAGS_FOUND;
        return ApiResponse.onSuccess(code, tagService.getTreeTags(memberId, treeId));
    }

    // 노드의 태그 목록 조회
    @Operation(summary = "노드의 태그 목록 조회", description = "노드에 속한 모든 태그 목록을 조회합니다.")
    @GetMapping("/nodes/{nodeId}/tags")
    public ResponseEntity<ApiResponse<List<TagResDTO.GetTag>>> getNodeTags(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long nodeId
    ) {
        BaseSuccessCode code = TagSuccessCode.TAGS_FOUND;
        return ApiResponse.onSuccess(code, tagService.getNodeTags(memberId, nodeId));
    }

    // 노드의 태그 추가
    @Operation(summary = "노드의 태그 추가", description = "노드에 태그 하나를 추가합니다.")
    @PostMapping("/nodes/{nodeId}/tags")
    public ResponseEntity<ApiResponse<List<TagResDTO.GetTag>>> addNodeTag(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long nodeId
    ) {
        BaseSuccessCode code = TagSuccessCode.TAG_ADDED;
        return ApiResponse.onSuccess(code, tagService.addNodeTag(memberId, nodeId));
    }

    // 노드의 태그 삭제
    @Operation(summary = "노드의 태그 삭제", description = "노드에 태그 하나를 삭제합니다.")
    @DeleteMapping("/nodes/{nodeId}/tags")
    public ResponseEntity<ApiResponse<List<TagResDTO.GetTag>>> removeNodeTag(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long nodeId
    ) {
        BaseSuccessCode code = TagSuccessCode.TAG_REMOVED;
        return ApiResponse.onSuccess(code, tagService.removeNodeTag(memberId, nodeId));
    }
}
