package com.tree.twig_tree.domain.workspace.service;

import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.converter.WorkspaceConverter;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceCursor;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceReqDTO;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceResDTO;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.exception.WorkspaceException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final FolderRepository folderRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TreeRepository treeRepository;
    private final MemberService memberService;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 전체 워크스페이스 최신순 조회
     * @param cursor 직전 응답의 nextCursor (null 이면 첫 페이지)
     * @param size 요청 개수 (1 ~ 50 범위로 보정)
     * @return
     */
    public WorkspaceResDTO.GetWorkspaceSlice getAllWorkspaces(Long memberId, String cursor, Integer size) {
        int pageSize = normalizeSize(size);

        // 1) size + 1 개를 조회한다.
        //    하나 더 가져와서 실제로 넘치면 다음 페이지가 있다고 판단한다.
        int fetchSize = pageSize + 1;
        List<Workspace> fetched;
        if (cursor == null || cursor.isBlank()) {
            fetched = workspaceRepository.findRecentFirstPage(memberId, PageRequest.of(0, fetchSize));
        } else {
            WorkspaceCursor decoded = WorkspaceCursor.decode(cursor);
            fetched = workspaceRepository.findRecentAfterCursor(memberId, decoded.updatedAt(), decoded.workspaceId(), PageRequest.of(0, fetchSize));
        }

        // 2) hasNext 기록하고 넘친 1개는 잘라낸다.
        boolean hasNext = fetched.size() > pageSize;
        List<Workspace> workspaceList = hasNext ? fetched.subList(0, pageSize) : fetched;

        // 트리 ID 매핑
        Map<Long, Long> treeIdByWorkspaceId = treeRepository.findAllByWorkspaceIn(workspaceList).stream()
                .collect(Collectors.toMap(tree -> tree.getWorkspace().getId(), Tree::getId));
        List<WorkspaceResDTO.GetWorkspace> workspaces = WorkspaceConverter.toGetWorkspaces(workspaceList, treeIdByWorkspaceId);

        // 3) 다음 커서는 이번 페이지의 마지막 항목으로 만든다.
        String nextCursor = hasNext ? WorkspaceCursor.from(workspaces.get(workspaces.size() - 1)).encode() : null;

        return WorkspaceResDTO.GetWorkspaceSlice.builder()
                .workspaces(workspaces)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    // size 가 없거나 범위를 벗어나면 에러 대신 경계값으로 보정 (프론트 친화적)
    private int normalizeSize(Integer size) {
        if (size == null) return DEFAULT_PAGE_SIZE;
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    /**
     * 특정 폴더 기준 워크스페이스 목록 최신순 조회
     * @param folderId null이면 폴더에 속하지 않는 최상위 워크스페이스
     * @return
     */
    public List<WorkspaceResDTO.GetWorkspace> getWorkspacesByFolder(Long memberId, Long folderId) {
        if (folderId != null) {
            Folder folder = validateFolder(folderId);
            validateFolderOwner(memberId, folder);
        }

        List<Workspace> workspaceList;
        if (folderId == null) {
            workspaceList = workspaceRepository.findAllByFolderIsNullAndMember_IdOrderByUpdatedAtDesc(memberId);
        } else {
            workspaceList = workspaceRepository.findAllByFolder_IdAndMember_IdOrderByUpdatedAtDesc(folderId, memberId);
        }

        Map<Long, Long> treeIdByWorkspaceId = treeRepository.findAllByWorkspaceIn(workspaceList).stream()
                .collect(Collectors.toMap(tree -> tree.getWorkspace().getId(), Tree::getId));

        return WorkspaceConverter.toGetWorkspaces(workspaceList, treeIdByWorkspaceId);
    }

    /**
     * 워크스페이스 생성
     * @param dto
     * @return
     */
    @Transactional
    public WorkspaceResDTO.GetWorkspace createWorkspace(Long memberId, WorkspaceReqDTO.CreateWorkspace dto) {
        // 워크스페이스를 생성할 폴더 위치가 있는지 확인
        Folder folder = null;
        if (dto.folderId() != null) {
            folder = validateFolder(dto.folderId());
            validateFolderOwner(memberId, folder);
        }

        Member member = memberService.getById(memberId);

        Workspace workspace = Workspace.builder()
                .name(dto.name())
                .folder(folder)
                .member(member)
                .build();

        workspaceRepository.save(workspace);

        // 생성 직후 워크스페이스에는 트리가 있을 수 없음 -> null
        return WorkspaceConverter.toGetWorkspace(workspace, null);
    }

    /**
     * 워크스페이스 조회
     * @param workspaceId
     * @return
     */
    public WorkspaceResDTO.GetWorkspace getWorkspace(Long memberId, Long workspaceId) {
        Workspace workspace = validateWorkspace(workspaceId);
        validateWorkspaceOwner(memberId, workspace);

        Long treeId = treeRepository.findByWorkspace(workspace).map(Tree::getId).orElse(null);

        return WorkspaceConverter.toGetWorkspace(workspace, treeId);
    }

    /**
     * 워크스페이스 이름 수정
     * @param workspaceId
     * @return
     */
    @Transactional
    public WorkspaceResDTO.GetWorkspace updateWorkspace(Long memberId, Long workspaceId, String name) {
        Workspace workspace = validateWorkspace(workspaceId);
        validateWorkspaceOwner(memberId, workspace);

        workspace.updateName(name);
        workspaceRepository.flush();

        Long treeId = treeRepository.findByWorkspace(workspace).map(Tree::getId).orElse(null);

        return WorkspaceConverter.toGetWorkspace(workspace, treeId);
    }

    /**
     * 워크스페이스 삭제
     * @param workspaceId
     * @return
     */
    @Transactional
    public Void deleteWorkspace(Long memberId, Long workspaceId) {
        Workspace workspace = validateWorkspace(workspaceId);
        validateWorkspaceOwner(memberId, workspace);

        workspaceRepository.delete(workspace);
        return null;
    }

    // 검증 함수

    private Workspace validateWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
    }

    private Folder validateFolder(Long folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new FolderException(FolderErrorCode.FOLDER_NOT_FOUND));
    }

    private void validateFolderOwner(Long memberId, Folder folder) {
        if (!folder.getMember().getId().equals(memberId)) {
            throw new FolderException(FolderErrorCode.FOLDER_ACCESS_DENIED);
        }
    }

    private void validateWorkspaceOwner(Long memberId, Workspace workspace) {
        if (!workspace.getMember().getId().equals(memberId)) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }
}
