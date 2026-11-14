package com.tree.twig_tree.domain.seed.service;

import com.tree.twig_tree.TestcontainersConfiguration;
import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.service.AuthService;
import com.tree.twig_tree.domain.auth.service.GoogleIdTokenVerifier;
import com.tree.twig_tree.domain.auth.service.GoogleIdTokenVerifier.GoogleUserInfo;
import com.tree.twig_tree.domain.chat.service.TreeNodeWriter;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * 실제 PostgreSQL 에서 신규 가입 시 테스터 시드 트리가 만들어지는지 검증한다.
 * 시드 실패가 가입을 되돌리지 않는지(트랜잭션 분리)는 DB 없이 확인할 수 없다.
 */
@SpringBootTest(properties = "tester-seed.enabled=true")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TesterSeedIntegrationTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private TreeRepository treeRepository;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private TagRepository tagRepository;

    @MockitoSpyBean
    private TreeNodeWriter treeNodeWriter;
    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;
    // 토큰 저장은 이 테스트의 관심사가 아니다. Redis 없이(CI) 돌도록 대체한다
    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    @DisplayName("신규 가입 회원에게 최상위 '고구려' 워크스페이스와 시드 트리가 생긴다")
    void seedsTreeOnRegistration() {
        String providerId = UUID.randomUUID().toString();
        AuthResDTO.LoginResult login = login(providerId);
        Member member = member(providerId);

        assertThat(login.seedFailed()).isFalse();
        List<Workspace> workspaces = workspaceRepository.findAllByFolderIsNullAndMember_IdOrderByUpdatedAtDesc(member.getId());
        assertThat(workspaces).extracting(Workspace::getName).containsExactly("고구려");

        Long treeId = treeRepository.findIdByWorkspaceId(workspaces.get(0).getId()).orElseThrow();
        List<Node> nodes = nodeRepository.findFullTreeByTreeId(treeId);
        assertThat(nodes).hasSize(14);

        Node root = nodes.stream().filter(n -> n.getName().equals("고구려")).findFirst().orElseThrow();
        assertThat(childNames(root)).containsExactly("건국과 성장", "전성기", "대외 항쟁", "멸망");

        Node founding = child(root, "건국과 성장");
        assertThat(childNames(founding)).containsExactly("주몽", "유리왕", "태조왕");
        assertThat(childNames(child(root, "전성기"))).containsExactly("광개토대왕", "장수왕");
        assertThat(childNames(child(root, "대외 항쟁"))).containsExactly("살수대첩", "안시성 전투");
        assertThat(childNames(child(root, "멸망"))).containsExactly("연개소문", "나당 연합군");
        assertThat(nodeRepository.findAllByParentIdOrderByOrderIdAsc(founding.getId()))
            .extracting(Node::getOrderId).containsExactly(1L, 2L, 3L);

        Tree tree = treeRepository.findById(treeId).orElseThrow();
        assertThat(tagRepository.findAllByTree(tree)).extracting(Tag::getName)
            .containsExactlyInAnyOrder("인물", "왕", "전투", "영토 확장", "천도", "시험 빈출", "복습 필요");

        Map<String, List<String>> tagsByNode = nodes.stream().collect(Collectors.toMap(
            Node::getName, n -> tagRepository.findAllByNode(n).stream().map(Tag::getName).toList()));
        assertThat(tagsByNode.get("고구려")).isEmpty();
        assertThat(tagsByNode.get("건국과 성장")).isEmpty();
        assertThat(tagsByNode.get("주몽")).containsExactlyInAnyOrder("인물", "왕");
        assertThat(tagsByNode.get("장수왕")).containsExactlyInAnyOrder("인물", "왕", "천도", "영토 확장", "시험 빈출");
        assertThat(tagsByNode.get("살수대첩")).containsExactlyInAnyOrder("전투", "시험 빈출");
        assertThat(tagsByNode.get("연개소문")).containsExactly("인물");
        assertThat(tagsByNode.get("나당 연합군")).isEmpty();
        // 어느 노드에도 붙이지 않은 태그도 트리에는 만들어진다
        assertThat(tagsByNode.values()).noneMatch(tags -> tags.contains("복습 필요"));
    }

    @Test
    @DisplayName("기존 회원이 다시 로그인하면 시드 트리를 또 만들지 않는다")
    void doesNotSeedAgainOnLogin() {
        String providerId = UUID.randomUUID().toString();
        login(providerId);
        Member member = member(providerId);

        assertThat(login(providerId).seedFailed()).isFalse();
        assertThat(workspaceRepository.findAllByFolderIsNullAndMember_IdOrderByUpdatedAtDesc(member.getId()))
            .hasSize(1);
    }

    @Test
    @DisplayName("시드 저장이 실패해도 로그인은 성공해 seedFailed 로 알리고, 시드에서 만든 데이터만 롤백된다")
    void keepsRegistrationWhenSeedFails() {
        String providerId = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("시드 저장 실패")).when(treeNodeWriter).saveNodes(any(), any());

        AuthResDTO.LoginResult login;
        try {
            login = login(providerId);
        } finally {
            reset(treeNodeWriter);
        }

        assertThat(login.seedFailed()).isTrue();
        assertThat(login.tokens().accessToken()).isNotBlank();
        Member member = member(providerId);
        // 노드 저장 전에 만든 워크스페이스·트리도 시드 트랜잭션과 함께 롤백된다
        assertThat(workspaceRepository.findAllByFolderIsNullAndMember_IdOrderByUpdatedAtDesc(member.getId()))
            .isEmpty();

        // 다시 시도하지 않으므로 다음 로그인에서는 알리지 않는다
        assertThat(login(providerId).seedFailed()).isFalse();
    }

    private AuthResDTO.LoginResult login(String providerId) {
        String idToken = "id-token-" + providerId;
        when(googleIdTokenVerifier.verify(idToken))
            .thenReturn(new GoogleUserInfo(providerId, "seed-" + providerId + "@test.com", "테스터", null));
        return authService.googleLogin(idToken);
    }

    private Member member(String providerId) {
        return memberRepository.findByProviderAndProviderId(Provider.GOOGLE, providerId).orElseThrow();
    }

    private Node child(Node parent, String name) {
        return nodeRepository.findAllByParentIdOrderByOrderIdAsc(parent.getId()).stream()
            .filter(n -> n.getName().equals(name))
            .findFirst()
            .orElseThrow();
    }

    private List<String> childNames(Node parent) {
        return nodeRepository.findAllByParentIdOrderByOrderIdAsc(parent.getId()).stream()
            .map(Node::getName)
            .toList();
    }
}
