package com.tree.twig_tree.domain.workspace.service;

import com.tree.twig_tree.TestcontainersConfiguration;
import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.domain.member.repository.MemberRepository;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceReqDTO;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceResDTO;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import com.tree.twig_tree.global.apiPayload.util.ConstraintErrorCodeMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL 에서 워크스페이스 이동을 검증한다.
 * 이름 중복을 막는 부분 UNIQUE 인덱스(uk_workspace_root_name, uk_workspace_folder_name)와
 * 이동이 updatedAt 을 갱신하는지는 DB 없이 확인할 수 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WorkspaceMoveIntegrationTest {

    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;
    private Member member;

    private Folder folder;
    private Folder otherFolder;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .email("workspace-move-" + UUID.randomUUID() + "@test.com")
                .provider(Provider.GOOGLE)
                .providerId(UUID.randomUUID().toString())
                .role(Role.ROLE_USER)
                .build());
        memberId = member.getId();

        folder = saveFolder("폴더");
        otherFolder = saveFolder("다른폴더");
        workspace = saveWorkspace("워크스페이스", null);
    }

    @Test
    @DisplayName("폴더 아래로 이동하면 folderId 가 바뀐다")
    void moveIntoFolder() {
        WorkspaceResDTO.GetWorkspace result = move(workspace, folder);

        assertThat(result.folderId()).isEqualTo(folder.getId());
        assertThat(storedFolderId(workspace)).isEqualTo(folder.getId());
    }

    @Test
    @DisplayName("folderId 가 null 이면 폴더에 속하지 않는 최상위로 이동한다")
    void moveToRoot() {
        Workspace inFolder = saveWorkspace("폴더 안 워크스페이스", folder);

        WorkspaceResDTO.GetWorkspace result = move(inFolder, null);

        assertThat(result.folderId()).isNull();
        assertThat(storedFolderId(inFolder)).isNull();
    }

    @Test
    @DisplayName("이동하면 updatedAt 이 갱신되어 최근 목록 맨 앞으로 올라간다")
    void moveRefreshesUpdatedAt() {
        Instant before = storedUpdatedAt(workspace);

        move(workspace, folder);

        assertThat(storedUpdatedAt(workspace)).isAfter(before);
    }

    @Test
    @DisplayName("목적지 폴더에 같은 이름의 워크스페이스가 있으면 DUPLICATE_WORKSPACE_NAME")
    void duplicateNameInDestination() {
        saveWorkspace("워크스페이스", folder);

        assertDuplicateWorkspaceName(() -> move(workspace, folder));
    }

    @Test
    @DisplayName("루트에 같은 이름의 워크스페이스가 있으면 DUPLICATE_WORKSPACE_NAME")
    void duplicateNameInRoot() {
        // 루트 유일성은 uk_workspace_root_name (member_id, name) 이라 같은 회원의 최상위끼리만 겹친다.
        Workspace inFolder = saveWorkspace("워크스페이스", folder);

        assertDuplicateWorkspaceName(() -> move(inFolder, null));
    }

    @Test
    @DisplayName("다른 폴더로 옮긴 뒤에도 같은 이름 검사가 목적지 기준으로 적용된다")
    void duplicateNameFollowsDestination() {
        saveWorkspace("워크스페이스", otherFolder);

        // 이름이 겹치지 않는 폴더로는 옮길 수 있다.
        assertThat(move(workspace, folder).folderId()).isEqualTo(folder.getId());
        // 같은 이름이 이미 있는 폴더로는 옮길 수 없다.
        assertDuplicateWorkspaceName(() -> move(workspace, otherFolder));
    }

    private void assertDuplicateWorkspaceName(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(DataIntegrityViolationException.class)
                // GeneralExceptionAdvice 가 이 예외를 거쳐 응답 코드를 고른다.
                .satisfies(e -> assertThat(ConstraintErrorCodeMapper.getErrorCode(e))
                        .isEqualTo(WorkspaceErrorCode.DUPLICATE_WORKSPACE_NAME));
    }

    private WorkspaceResDTO.GetWorkspace move(Workspace target, Folder destination) {
        Long folderId = destination == null ? null : destination.getId();
        return workspaceService.moveWorkspace(memberId, target.getId(), new WorkspaceReqDTO.MoveWorkspace(folderId));
    }

    private Long storedFolderId(Workspace target) {
        Folder stored = workspaceRepository.findById(target.getId()).orElseThrow().getFolder();
        return stored == null ? null : stored.getId();
    }

    private Instant storedUpdatedAt(Workspace target) {
        return workspaceRepository.findById(target.getId()).orElseThrow().getUpdatedAt();
    }

    private Folder saveFolder(String name) {
        return folderRepository.save(Folder.builder().name(name).member(member).build());
    }

    private Workspace saveWorkspace(String name, Folder folder) {
        return workspaceRepository.save(Workspace.builder().name(name).folder(folder).member(member).build());
    }
}
