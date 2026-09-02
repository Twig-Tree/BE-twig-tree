package com.tree.twig_tree.domain.workspace.controller;

import com.tree.twig_tree.domain.workspace.dto.WorkspaceReqDTO;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceResDTO;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceSuccessCode;
import com.tree.twig_tree.domain.workspace.service.WorkspaceService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Workspace", description = "워크스페이스 생성, 조회, 수정 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    /**
     * 모든 워크스페이스 목록 최신순 조회 (특정 폴더 기준 아님)
     * Recent 탭, Dashboard 탭의 최신 목록에서 사용됩니다.
     * @return
     */
    @Operation(summary = "전체 워크스페이스 목록 최신순 조회", description = "폴더에 속한 것과 관계없이 전체 워크스페이스 목록을 최신순으로 조회합니다.")
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<WorkspaceResDTO.GetWorkspace>>> getAllWorkspaces() {
        BaseSuccessCode code = WorkspaceSuccessCode.WORKSPACES_FOUND;
        return ApiResponse.onSuccess(code, workspaceService.getAllWorkspaces());
    }

    /**
     * 특정 폴더 내 워크스페이스 목록 최신순 조회
     * Directory 탭에서 사용됩니다.
     * @param folderId (null 가능) 이 값을 기준으로 폴더 내 워크스페이스 목록을 조회합니다.
     * @return
     */
    @Operation(summary = "특정 폴더 기준 워크스페이스 목록 최신순 조회", description = "folderId 값을 기준으로 폴더 내 워크스페이스 목록을 updatedAt 기준 최신순으로 조회합니다.<br>"
            + "folderId = null 이면 어떠한 폴더에도 속하지 않은 최상위 워크스페이스입니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceResDTO.GetWorkspace>>> getWorkspacesByFolder(
            @RequestParam(required = false) Long folderId
    ) {
        BaseSuccessCode code = WorkspaceSuccessCode.WORKSPACES_FOUND;
        return ApiResponse.onSuccess(code, workspaceService.getWorkspacesByFolder(folderId));
    }

    /**
     * 워크스페이스 생성
     * @param dto // name, folderId (null 가능)
     * @return
     */
    @Operation(summary = "새로운 워크스페이스 생성", description = "folderId = null 이면 어떠한 폴더에도 속하지 않은 최상위 워크스페이스가 생성됩니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceResDTO.GetWorkspace>> createWorkspace(
            @RequestBody @Valid WorkspaceReqDTO.CreateWorkspace dto
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
    public ResponseEntity<ApiResponse<WorkspaceResDTO.GetWorkspace>> getWorkspace(
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
    @Operation(summary = "워크스페이스 이름 수정", description = "")
    @PatchMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResDTO.GetWorkspace>> updateWorkspace(
            @PathVariable Long workspaceId,
            @RequestBody @Valid WorkspaceReqDTO.UpdateWorkspace dto
    ) {
        BaseSuccessCode code = WorkspaceSuccessCode.WORKSPACE_UPDATED;
        return ApiResponse.onSuccess(code, workspaceService.updateWorkspace(workspaceId, dto.name()));
    }

    /**
     * 워크스페이스 삭제
     * @param workspaceId
     * @return
     */
    @Operation(summary = "워크스페이스 삭제", description = "")
    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<Void>> deleteWorkspace(
            @PathVariable Long workspaceId
    ) {
        BaseSuccessCode code = WorkspaceSuccessCode.WORKSPACE_DELETED;
        return ApiResponse.onSuccess(code, workspaceService.deleteWorkspace(workspaceId));
    }
}
