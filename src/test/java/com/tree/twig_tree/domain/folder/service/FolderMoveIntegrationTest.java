package com.tree.twig_tree.domain.folder.service;

import com.tree.twig_tree.TestcontainersConfiguration;
import com.tree.twig_tree.domain.folder.dto.FolderReqDTO;
import com.tree.twig_tree.domain.folder.dto.FolderProjection;
import com.tree.twig_tree.domain.folder.dto.FolderResDTO;
import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.domain.member.repository.MemberRepository;
import com.tree.twig_tree.global.apiPayload.util.ConstraintErrorCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL 에서 폴더 이동을 검증한다.
 * 조상 탐색 재귀 CTE 와 이름 중복을 막는 부분 UNIQUE 인덱스(uk_folder_root_name, uk_folder_parent_name)는
 * DB 없이 확인할 수 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FolderMoveIntegrationTest {

    @Autowired
    private FolderService folderService;
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;
    private Member member;

    // 부모 ── 자식 ── 손자
    // 다른폴더
    private Folder parent;
    private Folder child;
    private Folder grandChild;
    private Folder other;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .email("folder-move-" + UUID.randomUUID() + "@test.com")
                .provider(Provider.GOOGLE)
                .providerId(UUID.randomUUID().toString())
                .role(Role.ROLE_USER)
                .build());
        memberId = member.getId();

        parent = save("부모", null);
        child = save("자식", parent);
        grandChild = save("손자", child);
        other = save("다른폴더", null);
    }

    @Test
    @DisplayName("다른 폴더 아래로 옮기면 하위 폴더 경로도 함께 바뀐다")
    void moveUnderAnotherFolder() {
        FolderResDTO.GetFolder result = move(parent, other);

        assertThat(result.folderParentId()).isEqualTo(other.getId());
        assertThat(ancestorNames(grandChild)).containsExactly("다른폴더", "부모", "자식", "손자");
    }

    @Test
    @DisplayName("folderParentId 가 null 이면 최상위 루트로 이동한다")
    void moveToRoot() {
        FolderResDTO.GetFolder result = move(child, null);

        assertThat(result.folderParentId()).isNull();
        assertThat(folderRepository.findById(child.getId()).orElseThrow().getParent()).isNull();
        assertThat(ancestorNames(grandChild)).containsExactly("자식", "손자");
    }

    @Test
    @DisplayName("현재 부모로 다시 옮기는 요청은 그대로 성공한다")
    void moveToCurrentParent() {
        FolderResDTO.GetFolder result = move(child, parent);

        assertThat(result.folderParentId()).isEqualTo(parent.getId());
        assertThat(folderRepository.findById(child.getId()).orElseThrow().getParent().getId())
                .isEqualTo(parent.getId());
    }

    @Test
    @DisplayName("루트에 있는 폴더를 루트로 옮기는 요청도 그대로 성공한다")
    void moveRootFolderToRoot() {
        FolderResDTO.GetFolder result = move(other, null);

        assertThat(result.folderParentId()).isNull();
        assertThat(folderRepository.findById(other.getId()).orElseThrow().getParent()).isNull();
    }

    @Test
    @DisplayName("자기 자신 밑으로 옮기면 INVALID_MOVE_TARGET")
    void movingIntoItself() {
        assertThatThrownBy(() -> move(parent, parent))
                .isInstanceOf(FolderException.class)
                .satisfies(e -> assertThat(((FolderException) e).getErrorCode())
                        .isEqualTo(FolderErrorCode.INVALID_MOVE_TARGET));
    }

    @Test
    @DisplayName("자기 하위 폴더 밑으로 옮기면 INVALID_MOVE_TARGET")
    void movingIntoOwnDescendant() {
        assertThatThrownBy(() -> move(parent, grandChild))
                .isInstanceOf(FolderException.class)
                .satisfies(e -> assertThat(((FolderException) e).getErrorCode())
                        .isEqualTo(FolderErrorCode.INVALID_MOVE_TARGET));

        // 거절된 요청은 기존 위치를 그대로 남긴다.
        assertThat(folderRepository.findById(parent.getId()).orElseThrow().getParent()).isNull();
    }

    @Test
    @DisplayName("목적지에 같은 이름의 폴더가 있으면 DUPLICATE_FOLDER_NAME")
    void duplicateNameInDestination() {
        save("자식", other);

        assertDuplicateFolderName(() -> move(child, other));
    }

    @Test
    @DisplayName("루트에 같은 이름의 폴더가 있으면 DUPLICATE_FOLDER_NAME")
    void duplicateNameInRoot() {
        // 루트 유일성은 uk_folder_root_name (member_id, name) 이라 같은 회원의 루트끼리만 겹친다.
        save("자식", null);

        assertDuplicateFolderName(() -> move(child, null));
    }

    @Test
    @DisplayName("조상 탐색: 자기 자신과 하위 폴더는 true, 형제·상위 폴더는 false")
    void isSelfOrAncestor() {
        assertThat(folderRepository.isSelfOrAncestor(parent.getId(), parent.getId())).isTrue();
        assertThat(folderRepository.isSelfOrAncestor(parent.getId(), grandChild.getId())).isTrue();
        assertThat(folderRepository.isSelfOrAncestor(parent.getId(), other.getId())).isFalse();
        assertThat(folderRepository.isSelfOrAncestor(child.getId(), parent.getId())).isFalse();
    }

    private void assertDuplicateFolderName(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(DataIntegrityViolationException.class)
                // GeneralExceptionAdvice 가 이 예외를 거쳐 응답 코드를 고른다.
                .satisfies(e -> assertThat(ConstraintErrorCodeMapper.getErrorCode(e))
                        .isEqualTo(FolderErrorCode.DUPLICATE_FOLDER_NAME));
    }

    private FolderResDTO.GetFolder move(Folder target, Folder newParent) {
        Long newParentId = newParent == null ? null : newParent.getId();
        return folderService.moveFolder(memberId, target.getId(), new FolderReqDTO.MoveFolder(newParentId));
    }

    private List<String> ancestorNames(Folder folder) {
        return folderRepository.findAncestorPathRaw(folder.getId()).stream()
                .map(FolderProjection.FolderAncestorProjection::getName)
                .toList();
    }

    private Folder save(String name, Folder parent) {
        return folderRepository.save(Folder.builder().name(name).parent(parent).member(member).build());
    }
}
