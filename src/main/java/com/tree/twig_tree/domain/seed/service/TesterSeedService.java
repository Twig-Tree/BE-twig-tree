package com.tree.twig_tree.domain.seed.service;

import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.service.TreeNodeWriter;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.event.MemberRegisteredEvent;
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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 테스트 기간 동안, 새로 가입한 회원에게 미리 만들어 둔 트리(노드·태그 포함)를 넣어 준다.
 *
 * <p>{@code tester-seed.enabled=true} 일 때만 등록된다. 테스트가 끝나면 설정을 끄면 된다.
 *
 * <p>가입 트랜잭션 안에서 동기로 실행된다. 시드 저장이 실패하면 가입도 함께 롤백되어,
 * "가입은 됐는데 트리가 없는" 회원이 남지 않고 다음 로그인 때 다시 시도된다.
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

    @EventListener
    @Transactional
    public void onMemberRegistered(MemberRegisteredEvent event) {
        TesterSeedDTO seed = seedLoader.getSeed();
        Member member = memberService.getById(event.memberId());

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
