package com.tree.twig_tree.domain.folder.controller;

import com.tree.twig_tree.domain.folder.dto.FolderReqDTO;
import com.tree.twig_tree.domain.folder.dto.FolderResDTO;
import com.tree.twig_tree.domain.folder.exception.code.FolderSuccessCode;
import com.tree.twig_tree.domain.folder.service.FolderService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "Folder", description = "폴더 생성, 조회, 수정, 삭제 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/folders")
public class FolderController {

    private final FolderService folderService;

    /**
     * 새로운 폴더 생성
     * parent_folder_id는 null이 가능하여 path variable이 아닌 request body로 받습니다.
     * @param dto
     * @return
     */
    @Operation(
            summary = "새로운 폴더 생성",
            description = "새로운 폴더를 생성합니다.<br>" +
                    "parent_folder_id가 null이면 최상위 루트에 들어갑니다.<br>" +
                    "같은 부모를 갖는 폴더끼리는 이름이 겹칠 수 없습니다."
    )    @PostMapping
    public ApiResponse<FolderResDTO.FolderId> createFolder(
            @RequestBody @Valid FolderReqDTO.CreateFolder dto
    ) {
        BaseSuccessCode code = FolderSuccessCode.FOLDER_CREATED;
        return ApiResponse.onSuccess(code, folderService.createFolder(dto));
    }

    /**
     * 폴더 이름 수정
     * @param folderId
     * @param dto
     * @return
     */
    @Operation(summary = "폴더 이름 수정", description = "폴더 이름을 수정합니다. 같은 부모를 갖는 폴더끼리는 이름이 겹칠 수 없습니다.")
    @PatchMapping("/{folderId}")
    public ApiResponse<FolderResDTO.FolderId> updateFolder(
            @PathVariable Long folderId,
            @RequestBody @Valid FolderReqDTO.UpdateFolder dto
    ){
        BaseSuccessCode code = FolderSuccessCode.FOLDER_UPDATED;
        return ApiResponse.onSuccess(code, folderService.updateFolder(folderId, dto));
    }

    /**
     * 폴더 삭제
     * @param folderId
     * @return
     */
    @Operation(summary = "폴더 삭제", description = "특정 folder_id를 갖는 폴더를 삭제합니다.")
    @DeleteMapping("/{folderId}")
    public ApiResponse<Void> deleteFolder(
            @PathVariable Long folderId
    ) {
        BaseSuccessCode code = FolderSuccessCode.FOLDER_DELETED;
        return ApiResponse.onSuccess(code, folderService.deleteFolder(folderId));
    }

    /**
     * 폴더 목록 조회
     * @param parentFolderId 이 값을 기준으로 하위 폴더 목록을 조회합니다.
     * @return
     */
    @Operation(summary = "폴더 목록 조회", description = "parent_folder_id를 기준으로 하위 폴더 목록을 조회합니다. <br>" +"파라미터 없이 조회하면 parend_folder_id = null 인 폴더가 조회됩니다.")
    @GetMapping
    public ApiResponse<List<FolderResDTO.GetFolder>> getFolders(
            @RequestParam(required = false) Long parentFolderId
    ) {
        BaseSuccessCode code = FolderSuccessCode.FOLDERS_FOUND;
        return ApiResponse.onSuccess(code, folderService.getFolders(parentFolderId));
    }


}
