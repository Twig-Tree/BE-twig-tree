package com.tree.twig_tree.domain.member.service;

import com.tree.twig_tree.TestcontainersConfiguration;
import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.exception.AuthException;
import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import com.tree.twig_tree.domain.auth.service.AuthService;
import com.tree.twig_tree.domain.auth.service.GoogleIdTokenVerifier;
import com.tree.twig_tree.domain.auth.service.GoogleIdTokenVerifier.GoogleUserInfo;
import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.exception.MemberException;
import com.tree.twig_tree.domain.member.exception.code.MemberErrorCode;
import com.tree.twig_tree.domain.member.repository.MemberRepository;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tag.entity.Tag;
import com.tree.twig_tree.domain.tag.repository.TagRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import com.tree.twig_tree.global.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 PostgreSQL 에서 회원 탈퇴 시 소유 데이터가 FK CASCADE 로 함께 지워지는지 검증한다.
 * 시드를 켜서 워크스페이스·트리·노드·태그까지 갖춘 회원을 만든다.
 */
@SpringBootTest(properties = "tester-seed.enabled=true")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MemberWithdrawIntegrationTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private TreeRepository treeRepository;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private TagRepository tagRepository;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;
    // Redis 없이(CI) 돌도록 대체한다
    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    @DisplayName("탈퇴하면 회원과 그 회원의 폴더·워크스페이스·트리·노드·태그가 모두 지워지고, 다른 회원 데이터는 남는다")
    void deletesMemberAndOwnedData() {
        Member target = register();
        Member other = register();
        Owned targetData = ownedData(target);
        Owned otherData = ownedData(other);

        authService.withdraw(target.getId());

        assertThat(memberRepository.findById(target.getId())).isEmpty();
        assertThat(folderRepository.findById(targetData.folderId())).isEmpty();
        assertThat(workspaceRepository.findById(targetData.workspaceId())).isEmpty();
        assertThat(treeRepository.findById(targetData.treeId())).isEmpty();
        assertThat(nodeRepository.findAllById(targetData.nodeIds())).isEmpty();
        assertThat(tagRepository.findAllById(targetData.tagIds())).isEmpty();

        assertThat(memberRepository.findById(other.getId())).isPresent();
        assertThat(folderRepository.findById(otherData.folderId())).isPresent();
        assertThat(workspaceRepository.findById(otherData.workspaceId())).isPresent();
        assertThat(treeRepository.findById(otherData.treeId())).isPresent();
        assertThat(nodeRepository.findAllById(otherData.nodeIds())).hasSameSizeAs(otherData.nodeIds());
        assertThat(tagRepository.findAllById(otherData.tagIds())).hasSameSizeAs(otherData.tagIds());

        verify(refreshTokenStore).deleteAll(target.getId());
    }

    @Test
    @DisplayName("refresh 토큰 폐기가 실패해도 탈퇴는 성공한다")
    void withdrawsEvenWhenTokenStoreFails() {
        Member target = register();
        doThrow(new AuthException(AuthErrorCode.TOKEN_STORE_UNAVAILABLE))
            .when(refreshTokenStore).deleteAll(target.getId());

        assertThatCode(() -> authService.withdraw(target.getId())).doesNotThrowAnyException();
        assertThat(memberRepository.findById(target.getId())).isEmpty();
    }

    @Test
    @DisplayName("폐기되지 않고 남은 refresh 토큰으로도 탈퇴 후에는 재발급받을 수 없다")
    void rejectsReissueAfterWithdraw() {
        String providerId = UUID.randomUUID().toString();
        AuthResDTO.LoginResult login = login(providerId);
        Long memberId = member(providerId).getId();

        authService.withdraw(memberId);
        // 토큰 폐기가 실패해 저장소에 jti 가 남아 있는 상황
        when(refreshTokenStore.exists(anyLong(), any())).thenReturn(true);

        assertThatThrownBy(() -> authService.reissue(login.tokens().refreshToken()))
            .isInstanceOf(MemberException.class)
            .extracting("errorCode").isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 회원이 탈퇴하면 MEMBER_NOT_FOUND 이고 토큰 저장소를 건드리지 않는다")
    void rejectsUnknownMember() {
        assertThatThrownBy(() -> authService.withdraw(Long.MAX_VALUE))
            .isInstanceOf(MemberException.class)
            .extracting("errorCode").isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
        verify(refreshTokenStore, org.mockito.Mockito.never()).deleteAll(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("토큰 저장소가 AuthException 이 아닌 런타임 예외를 던져도 탈퇴는 성공한다")
    void withdrawsEvenWhenTokenStoreThrowsUnexpectedException() {
        Member target = register();
        doThrow(new IllegalStateException("redis connection closed"))
            .when(refreshTokenStore).deleteAll(target.getId());

        assertThatCode(() -> authService.withdraw(target.getId())).doesNotThrowAnyException();
        assertThat(memberRepository.findById(target.getId())).isEmpty();
    }

    private Member register() {
        String providerId = UUID.randomUUID().toString();
        login(providerId);
        return member(providerId);
    }

    private AuthResDTO.LoginResult login(String providerId) {
        String idToken = "id-token-" + providerId;
        when(googleIdTokenVerifier.verify(idToken))
            .thenReturn(new GoogleUserInfo(providerId, "withdraw-" + providerId + "@test.com", "탈퇴자", null));
        return authService.googleLogin(idToken);
    }

    private Member member(String providerId) {
        return memberRepository.findByProviderAndProviderId(Provider.GOOGLE, providerId).orElseThrow();
    }

    /** 시드로 생긴 워크스페이스·트리·노드·태그에 폴더 하나를 더해 회원 소유 데이터의 id 를 모은다. */
    private Owned ownedData(Member member) {
        Folder folder = folderRepository.save(Folder.builder().name("탈퇴 테스트").member(member).build());

        Workspace workspace = workspaceRepository.findAllByFolderIsNullAndMember_IdOrderByUpdatedAtDesc(member.getId())
            .get(0);
        Long treeId = treeRepository.findIdByWorkspaceId(workspace.getId()).orElseThrow();
        Tree tree = treeRepository.findById(treeId).orElseThrow();
        List<Long> nodeIds = nodeRepository.findFullTreeByTreeId(treeId).stream().map(Node::getId).toList();
        List<Long> tagIds = tagRepository.findAllByTree(tree).stream().map(Tag::getId).toList();

        assertThat(nodeIds).isNotEmpty();
        assertThat(tagIds).isNotEmpty();
        return new Owned(folder.getId(), workspace.getId(), treeId, nodeIds, tagIds);
    }

    private record Owned(Long folderId, Long workspaceId, Long treeId, List<Long> nodeIds, List<Long> tagIds) {
    }
}
