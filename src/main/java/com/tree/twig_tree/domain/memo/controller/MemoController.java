package com.tree.twig_tree.domain.memo.controller;

import com.tree.twig_tree.domain.memo.dto.MemoReqDTO;
import com.tree.twig_tree.domain.memo.dto.MemoResDTO;
import com.tree.twig_tree.domain.memo.exception.code.MemoSuccessCode;
import com.tree.twig_tree.domain.memo.service.MemoService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/nodes/{nodeId}/memos")
public class MemoController {

    private final MemoService memoService;

    // memo는 초기값이 null로 생성되므로 PUT으로 생성과 수정이 모두 가능합니다.
    @PutMapping
    public ApiResponse<MemoResDTO.GetMemo> updateMemo(
            @PathVariable Long nodeId,
            @RequestBody MemoReqDTO.UpdateMemo dto
            ) {
        BaseSuccessCode code = MemoSuccessCode.MEMO_UPDATED;
        return ApiResponse.onSuccess(code, memoService.updateMemo(nodeId, dto));
    }

    @GetMapping
    public ApiResponse<MemoResDTO.GetMemo> getMemo(
            @PathVariable Long nodeId
    ) {
        BaseSuccessCode code = MemoSuccessCode.MEMO_FOUND;
        return ApiResponse.onSuccess(code, memoService.getMemo(nodeId));
    }

    @DeleteMapping
    public ApiResponse<Void> deleteMemo(
            @PathVariable Long nodeId
    ) {
        BaseSuccessCode code = MemoSuccessCode.MEMO_DELETED;
        return ApiResponse.onSuccess(code, memoService.deleteMemo(nodeId));
    }
}
