package com.tree.twig_tree.domain.memo.controller;

import com.tree.twig_tree.domain.memo.dto.MemoReqDTO;
import com.tree.twig_tree.domain.memo.dto.MemoResDTO;
import com.tree.twig_tree.domain.memo.exception.code.MemoSuccessCode;
import com.tree.twig_tree.domain.memo.service.MemoService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Memo")
@RestController
@RequiredArgsConstructor
@RequestMapping("/nodes/{nodeId}/memos")
public class MemoController {

    private final MemoService memoService;

    // memo는 초기값이 null로 생성되므로 PUT으로 생성과 수정이 모두 가능합니다.
    @Operation(summary = "메모 생성/수정", description = "메모를 업데이트합니다. 기본값이 null로 초기화되어있어 메모 생성은 수정과 같습니다.")
    @PutMapping
    public ApiResponse<MemoResDTO.GetMemo> updateMemo(
            @PathVariable Long nodeId,
            @RequestBody @Valid MemoReqDTO.UpdateMemo dto
            ) {
        BaseSuccessCode code = MemoSuccessCode.MEMO_UPDATED;
        return ApiResponse.onSuccess(code, memoService.updateMemo(nodeId, dto));
    }

    @Operation(summary = "메모 조회", description = "메모를 조회합니다. 메모 title은 노드 name과 같습니다.")
    @GetMapping
    public ApiResponse<MemoResDTO.GetMemo> getMemo(
            @PathVariable Long nodeId
    ) {
        BaseSuccessCode code = MemoSuccessCode.MEMO_FOUND;
        return ApiResponse.onSuccess(code, memoService.getMemo(nodeId));
    }

    @Operation(summary = "메모 삭제", description = "메모를 삭제합니다. 즉, null값이 됩니다.")
    @DeleteMapping
    public ApiResponse<Void> deleteMemo(
            @PathVariable Long nodeId
    ) {
        BaseSuccessCode code = MemoSuccessCode.MEMO_DELETED;
        return ApiResponse.onSuccess(code, memoService.deleteMemo(nodeId));
    }
}
