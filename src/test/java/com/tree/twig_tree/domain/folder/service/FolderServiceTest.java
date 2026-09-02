package com.tree.twig_tree.domain.folder.service;

import com.tree.twig_tree.domain.folder.dto.FolderReqDTO;
import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 폴더 소유권 검증. 다른 회원의 폴더를 건드리면 FOLDER_ACCESS_DENIED 여야 한다.
 */
@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long FOLDER_ID = 100L;

    @Mock
    private FolderRepository folderRepository;
    @Mock
    private MemberService memberService;
    @InjectMocks
    private FolderService folderService;

    private Folder folderOwnedByOwner() {
        Member owner = Member.builder().id(OWNER_ID).build();
        return Folder.builder().id(FOLDER_ID).name("내 폴더").member(owner).build();
    }

    @Nested
    @DisplayName("다른 회원 소유 폴더에 접근하면 FOLDER_ACCESS_DENIED")
    class OtherMembersFolder {

        @BeforeEach
        void stubFolderLookup() {
            when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.of(folderOwnedByOwner()));
        }

        @Test
        @DisplayName("폴더 조회")
        void getFolder() {
            assertAccessDenied(() -> folderService.getFolder(OTHER_ID, FOLDER_ID));
        }

        @Test
        @DisplayName("상위 경로 조회")
        void getFoldersPath() {
            assertAccessDenied(() -> folderService.getFoldersPath(OTHER_ID, FOLDER_ID));
            verify(folderRepository, never()).findAncestorPathRaw(any());
        }

        @Test
        @DisplayName("이름 수정")
        void updateFolder() {
            FolderReqDTO.UpdateFolder dto = new FolderReqDTO.UpdateFolder("바뀐 이름");
            assertAccessDenied(() -> folderService.updateFolder(OTHER_ID, FOLDER_ID, dto));
        }

        @Test
        @DisplayName("삭제")
        void deleteFolder() {
            assertAccessDenied(() -> folderService.deleteFolder(OTHER_ID, FOLDER_ID));
            verify(folderRepository, never()).delete(any());
        }
    }

    @Test
    @DisplayName("본인 소유 폴더는 소유권 검증을 통과한다")
    void ownFolderPasses() {
        when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.of(folderOwnedByOwner()));

        assertThatCode(() -> folderService.getFolder(OWNER_ID, FOLDER_ID)).doesNotThrowAnyException();
    }

    private void assertAccessDenied(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(FolderException.class)
                .satisfies(e -> assertThat(((FolderException) e).getErrorCode())
                        .isEqualTo(FolderErrorCode.FOLDER_ACCESS_DENIED));
    }
}
