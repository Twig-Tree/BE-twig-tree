package com.tree.twig_tree.domain.seed.service;

import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.service.TreeNodeWriter;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.seed.dto.TesterSeedDTO;
import com.tree.twig_tree.domain.seed.dto.TesterSeedDTO.SeedNode;
import com.tree.twig_tree.domain.tag.entity.NodeTag;
import com.tree.twig_tree.domain.tag.entity.Tag;
import com.tree.twig_tree.domain.tag.repository.NodeTagRepository;
import com.tree.twig_tree.domain.tag.repository.TagRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 테스트 기간 동안, 새로 가입한 회원에게 미리 만들어 둔 트리(노드·태그 포함)를 넣어 준다.
 *
 * <p>{@code tester-seed.enabled=true} 일 때만 등록된다. 테스트가 끝나면 설정을 끄면 된다.
 *
 * <p>로그인 흐름에서 가입 트랜잭션이 커밋된 뒤 별도 트랜잭션으로 호출된다. 테스트용 부가 기능이 로그인을 막으면
 * 안 되므로, 시드 저장이 실패해도 시드만 롤백하고 실패 여부를 돌려줄 뿐 예외를 던지지 않는다.
 * 실패한 회원은 트리 없이 시작하며, 다음 로그인 때 다시 시도하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tester-seed.enabled", havingValue = "true")
public class TesterSeedService {

    private final TesterSeedLoader seedLoader;
    private final MemberService memberService;
    private final WorkspaceRepository workspaceRepository;
    private final TreeRepository treeRepository;
    private final TreeNodeWriter treeNodeWriter;
    private final NodeRepository nodeRepository;
    private final TagRepository tagRepository;
    private final NodeTagRepository nodeTagRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 새 회원에게 시드 트리를 만든다. 호출자가 트랜잭션 안에 있더라도 휩쓸리지 않도록 항상 새 트랜잭션을 연다.
     *
     * @return 성공하면 true, 실패해 시드를 롤백했으면 false
     */
    public boolean seed(Long memberId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            tx.executeWithoutResult(status -> createSeedTree(memberId));
            return true;
        } catch (RuntimeException e) {
            log.error("테스터 시드 트리 생성 실패, 시드 없이 가입을 유지합니다. memberId={}", memberId, e);
            return false;
        }
    }

    private void createSeedTree(Long memberId) {
        TesterSeedDTO seed = seedLoader.getSeed();
        Member member = memberService.getById(memberId);

        // 새 회원이라 최상위 워크스페이스가 없으므로 uk_workspace_root_name 과 충돌하지 않는다
        Workspace workspace = workspaceRepository.save(Workspace.builder()
            .name(seed.workspaceName())
            .member(member)
            .build());
        Tree tree = treeRepository.save(Tree.builder().workspace(workspace).build());
        List<TreeGenResDTO.Node> savedNodes = treeNodeWriter.saveNodes(tree, seed.toTree());
        saveTags(tree, seed, savedNodes);

        log.info("테스터 시드 트리 생성 완료 memberId={}, workspaceId={}", member.getId(), workspace.getId());
    }

    /**
     * 트리에 태그를 만들고 노드에 붙인다.
     *
     * <p>TreeNodeWriter 는 노드를 BFS 순서로 저장해 같은 순서로 돌려주므로, 시드 노드를 BFS 로 나열하면
     * 저장된 노드와 위치로 짝지을 수 있다.
     */
    private void saveTags(Tree tree, TesterSeedDTO seed, List<TreeGenResDTO.Node> savedNodes) {
        Map<String, Tag> tagsByName = new HashMap<>();
        for (String name : seed.tags()) {
            tagsByName.put(name, tagRepository.save(Tag.builder().name(name).tree(tree).build()));
        }

        List<SeedNode> seedNodes = seed.nodesInBfsOrder();
        if (seedNodes.size() != savedNodes.size()) {
            throw new IllegalStateException("시드 노드 수와 저장된 노드 수가 다릅니다: seed="
                + seedNodes.size() + ", saved=" + savedNodes.size());
        }

        for (int i = 0; i < seedNodes.size(); i++) {
            List<String> tagNames = seedNodes.get(i).tags();
            if (tagNames.isEmpty()) {
                continue;
            }
            Node node = nodeRepository.getReferenceById(savedNodes.get(i).nodeId());
            for (String tagName : tagNames) {
                nodeTagRepository.save(NodeTag.builder().node(node).tag(tagsByName.get(tagName)).build());
            }
        }
    }
}
