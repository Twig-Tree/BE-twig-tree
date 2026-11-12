package com.tree.twig_tree.domain.folder.service;

import com.tree.twig_tree.domain.folder.dto.FolderReqDTO;
import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 폴더 위치 이동 검증.
 *
 * <p>순환 판정 자체는 네이티브 Recursive CTE 가 하므로 여기서는 서비스가 그 결과에 맞게
 * 분기하는지만 본다. 실제 계층 탐색과 이름 중복(부분 UNIQUE 인덱스)은 실제 DB 가 필요해
 * {@code FolderMoveIntegrationTest} 에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FolderServiceMoveTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long FOLDER_ID = 100L;
    private static final Long TARGET_ID = 200L;

    @Mock
    private FolderRepository folderRepository;
    @Mock
    private MemberService memberService;
    @InjectMocks
    private FolderService folderService;

    private Folder folder(Long id, Long memberId) {
        Member member = Member.builder().id(memberId).build();
        return Folder.builder().id(id).name("폴더 " + id).member(member).build();
    }

    private FolderReqDTO.MoveFolder moveTo(Long folderParentId) {
        return new FolderReqDTO.MoveFolder(folderParentId);
    }

    @Test
    @DisplayName("folderParentId 가 null 이면 최상위 루트로 이동한다")
    void moveToRoot() {
        Folder parent = folder(TARGET_ID, OWNER_ID);
        Folder target = folder(FOLDER_ID, OWNER_ID);
        target.moveTo(parent);
        when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.of(target));

        var result = folderService.moveFolder(OWNER_ID, FOLDER_ID, moveTo(null));

        assertThat(result.folderParentId()).isNull();
        assertThat(target.getParent()).isNull();
        // 루트로 가는 경우에도 이름 중복(uk_folder_root_name)을 커밋 전에 확인해야 한다.
        verify(folderRepository).flush();
        verify(folderRepository, never()).isSelfOrAncestor(FOLDER_ID, TARGET_ID);
    }

    @Test
    @DisplayName("다른 폴더 아래로 이동하면 부모가 바뀐다")
    void moveUnderAnotherFolder() {
        Folder target = folder(FOLDER_ID, OWNER_ID);
        Folder newParent = folder(TARGET_ID, OWNER_ID);
        when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.of(target));
        when(folderRepository.findById(TARGET_ID)).thenReturn(Optional.of(newParent));
        when(folderRepository.isSelfOrAncestor(FOLDER_ID, TARGET_ID)).thenReturn(false);

        var result = folderService.moveFolder(OWNER_ID, FOLDER_ID, moveTo(TARGET_ID));

        assertThat(result.folderParentId()).isEqualTo(TARGET_ID);
        assertThat(target.getParent()).isSameAs(newParent);
        verify(folderRepository).flush();
    }

    @Test
    @DisplayName("자기 자신이나 하위 폴더 밑으로 옮기면 INVALID_MOVE_TARGET")
    void cycleIsRejected() {
        Folder target = folder(FOLDER_ID, OWNER_ID);
        Folder descendant = folder(TARGET_ID, OWNER_ID);
        when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.of(target));
        when(folderRepository.findById(TARGET_ID)).thenReturn(Optional.of(descendant));
        when(folderRepository.isSelfOrAncestor(FOLDER_ID, TARGET_ID)).thenReturn(true);

        assertError(() -> folderService.moveFolder(OWNER_ID, FOLDER_ID, moveTo(TARGET_ID)),
                FolderErrorCode.INVALID_MOVE_TARGET);
        assertThat(target.getParent()).isNull();
        verify(folderRepository, never()).flush();
    }

    @Test
    @DisplayName("목적지 폴더가 없으면 PARENT_NOT_FOUND")
    void parentNotFound() {
        when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.of(folder(FOLDER_ID, OWNER_ID)));
        when(folderRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertError(() -> folderService.moveFolder(OWNER_ID, FOLDER_ID, moveTo(TARGET_ID)),
                FolderErrorCode.PARENT_NOT_FOUND);
        verify(folderRepository, never()).flush();
    }

    @Test
    @DisplayName("다른 회원 소유 폴더를 옮기면 FOLDER_ACCESS_DENIED")
    void movingOtherMembersFolder() {
        when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.of(folder(FOLDER_ID, OWNER_ID)));

        assertError(() -> folderService.moveFolder(OTHER_ID, FOLDER_ID, moveTo(null)),
                FolderErrorCode.FOLDER_ACCESS_DENIED);
        verify(folderRepository, never()).flush();
    }

    @Test
    @DisplayName("다른 회원 소유 폴더로 옮기면 FOLDER_ACCESS_DENIED")
    void movingIntoOtherMembersFolder() {
        Folder target = folder(FOLDER_ID, OWNER_ID);
        when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.of(target));
        when(folderRepository.findById(TARGET_ID)).thenReturn(Optional.of(folder(TARGET_ID, OTHER_ID)));

        assertError(() -> folderService.moveFolder(OWNER_ID, FOLDER_ID, moveTo(TARGET_ID)),
                FolderErrorCode.FOLDER_ACCESS_DENIED);
        assertThat(target.getParent()).isNull();
        verify(folderRepository, never()).isSelfOrAncestor(FOLDER_ID, TARGET_ID);
        verify(folderRepository, never()).flush();
    }

    private void assertError(ThrowingCallable action, BaseErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOf(FolderException.class)
                .satisfies(e -> assertThat(((FolderException) e).getErrorCode()).isEqualTo(expected));
    }
}
