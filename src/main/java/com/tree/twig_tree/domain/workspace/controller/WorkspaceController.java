package com.tree.twig_tree.domain.workspace.controller;

import com.tree.twig_tree.domain.workspace.dto.WorkspaceReqDTO;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceResDTO;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceSuccessCode;
import com.tree.twig_tree.domain.workspace.service.WorkspaceService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Workspace", description = "워크스페이스 생성, 조회, 수정 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    /**
     * 특정 폴더 내 워크스페이스 목록 조회
     * @param folderId (null 가능) 이 값을 기준으로 폴더 내 워크스페이스 목록을 조회합니다.
     * @return
     */
    @Operation(summary = "특정 폴더 내 워크스페이스 목록 조회", description = "parentFolderId 값을 기준으로 폴더 내 워크스페이스 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<WorkspaceResDTO.GetWorkspace>> getWorkspaces(
            @RequestParam(required = false) Long folderId
    ) {
        BaseSuccessCode code = WorkspaceSuccessCode.WORKSPACES_FOUND;
        return ApiResponse.onSuccess(code, workspaceService.getWorkspaces(folderId));
    }

    /**
     * 워크스페이스 생성
     * @param dto // name, folderId (null 가능)
     * @return
     */
    @Operation(summary = "새로운 워크스페이스 생성", description = "")
    @PostMapping
    public ApiResponse<WorkspaceResDTO.WorkspaceId> createWorkspace(
            @RequestBody WorkspaceReqDTO.CreateWorkspace dto
    ) {
        BaseSuccessCode code = WorkspaceSuccessCode.WORKSPACE_CREATED;
        return ApiResponse.onSuccess(code, workspaceService.createWorkspace(dto));
    }

    /**
     * 워크스페이스 조회
     * @param workspaceId
     * @return
     */
    @Operation(summary = "워크스페이스 조회", description = "")
    @GetMapping("/{workspaceId}")
    public ApiResponse<WorkspaceResDTO.GetWorkspace> getWorkspace(
            @PathVariable Long workspaceId
    ) {
        BaseSuccessCode code = WorkspaceSuccessCode.WORKSPACE_FOUND;
        return ApiResponse.onSuccess(code, workspaceService.getWorkspace(workspaceId));
    }

    /**
     * 워크스페이스 수정
     * @param workspaceId
     * @return
     */
    @Operation(summary = "워크스페이스 수정", description = "")
    @PatchMapping("/{workspaceId}")
    public ApiResponse<WorkspaceResDTO.WorkspaceId> updateWorkspace(
            @PathVariable Long workspaceId
    ) {
        BaseSuccessCode code = WorkspaceSuccessCode.WORKSPACE_UPDATED;
        return ApiResponse.onSuccess(code, workspaceService.updateWorkspace(workspaceId));
    }

    /**
     * 워크스페이스 삭제
     * @param workspaceId
     * @return
     */
    @Operation(summary = "워크스페이스 삭제", description = "")
    @DeleteMapping("/{workspaceId}")
    public ApiResponse<Void> deleteWorkspace(
            @PathVariable Long workspaceId
    ) {
        BaseSuccessCode code = WorkspaceSuccessCode.WORKSPACE_DELETED;
        return ApiResponse.onSuccess(code, workspaceService.deleteWorkspace(workspaceId));
    }
}
