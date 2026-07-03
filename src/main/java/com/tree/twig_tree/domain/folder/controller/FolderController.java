package com.tree.twig_tree.domain.folder.controller;

import com.tree.twig_tree.domain.folder.dto.FolderReqDTO;
import com.tree.twig_tree.domain.folder.dto.FolderResDTO;
import com.tree.twig_tree.domain.folder.exception.code.FolderSuccessCode;
import com.tree.twig_tree.domain.folder.service.FolderService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@Tag(name = "폴더 API", description = "폴더 생성, 조회, 수정, 삭제 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/folders")
public class FolderController {

    private final FolderService folderService;

    /**
     * 새로운 폴더 생성
     * @param dto
     * @return
     */
    @PostMapping()
    public ApiResponse<FolderResDTO.FolderId> createFolder(
            @RequestBody @Valid FolderReqDTO.createFolder dto
    ) {
        BaseSuccessCode code = FolderSuccessCode.FOLDER_CREATED;
        return ApiResponse.onSuccess(code, folderService.createFolder(dto));
    }

}
