package com.tree.twig_tree.domain.folder.service;

import com.tree.twig_tree.domain.folder.converter.FolderConverter;
import com.tree.twig_tree.domain.folder.dto.FolderProjection;
import com.tree.twig_tree.domain.folder.dto.FolderReqDTO;
import com.tree.twig_tree.domain.folder.dto.FolderResDTO;
import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FolderService {

    private final FolderRepository folderRepository;
    private final MemberService memberService;

    /**
     * 폴더 상위 경로 목록 조회
     * @param folderId
     * @return
     */
    public FolderResDTO.FolderPathList getFoldersPath(Long memberId, Long folderId) {
        Folder folder = validateFolder(folderId);
        validateFolderOwner(memberId, folder);

        // 해당 폴더ID로 상위 연결된 부모의 폴더들을 찾기 // Recursive CTE로 한번에 조회
        List<FolderProjection.FolderAncestorProjection> ancestors = folderRepository.findAncestorPathRaw(folderId);

        return FolderConverter.toGetFoldersPath(ancestors);

    }

    /**
     * 폴더 목록 조회
     * @param folderParentId
     * @return
     */
    public List<FolderResDTO.GetFolder> getFolders(Long memberId, Long folderParentId) {
        List<Folder> folders;
        if (folderParentId == null) {
            folders = folderRepository.findAllByParentIsNullAndMember_Id(memberId);
        } else {
            Folder parent = folderRepository.findById(folderParentId).orElseThrow(()->new FolderException(FolderErrorCode.PARENT_NOT_FOUND));
            validateFolderOwner(memberId, parent);
            folders = folderRepository.findAllByParentAndMember_Id(parent, memberId);
        }

        return FolderConverter.toGetFolders(folders);
    }

    /**
     * 폴더 생성
     * @param dto
     * @return
     */
    @Transactional
    public FolderResDTO.GetFolder createFolder(Long memberId, FolderReqDTO.CreateFolder dto) {

        Folder parent = null;
        if (dto.folderParentId() != null) {
            parent = folderRepository.findById(dto.folderParentId()).orElseThrow(() -> new FolderException(FolderErrorCode.PARENT_NOT_FOUND));
            validateFolderOwner(memberId, parent);
        }

        Member member = memberService.getById(memberId);

        Folder newFolder = Folder.builder()
                .parent(parent)
                .name(dto.name())
                .member(member)
                .build();

        folderRepository.save(newFolder);

        return FolderConverter.toGetFolder(newFolder);
    }

    /**
     * 폴더 조회
     * @param folderId
     * @return
     */
    public FolderResDTO.GetFolder getFolder(Long memberId, Long folderId) {
        Folder folder = validateFolder(folderId);
        validateFolderOwner(memberId, folder);

        return FolderConverter.toGetFolder(folder);
    }

    /**
     * 폴더 이름 수정
     * @param folderId
     * @param dto
     * @return
     */
    @Transactional
    public FolderResDTO.GetFolder updateFolder(Long memberId, Long folderId, FolderReqDTO.UpdateFolder dto) {
        Folder folder = validateFolder(folderId);
        validateFolderOwner(memberId, folder);

        folder.updateName(dto.name());

        return FolderConverter.toGetFolder(folder);
    }

    /**
     * 폴더 위치 이동
     * @param folderId 이동할 폴더
     * @param dto folderParentId 가 null 이면 최상위 루트로 이동
     * @return
     */
    @Transactional
    public FolderResDTO.GetFolder moveFolder(Long memberId, Long folderId, FolderReqDTO.MoveFolder dto) {
        // 순환 검사와 부모 변경 사이에 다른 이동이 끼어들지 못하게, 읽기 전에 회원 단위 락을 먼저 잡습니다.
        // 락이 없으면 A→B 와 B→A 가 동시에 들어올 때 양쪽 모두 순환 검사를 통과하고 서로 다른 행을
        // 갱신해 커밋되어, 조상 경로 조회(findAncestorPathRaw)가 끝나지 않는 순환이 저장됩니다.
        memberService.lockById(memberId);

        Folder folder = validateFolder(folderId);
        validateFolderOwner(memberId, folder);

        Folder newParent = null;
        if (dto.folderParentId() != null) {
            newParent = folderRepository.findById(dto.folderParentId())
                    .orElseThrow(() -> new FolderException(FolderErrorCode.PARENT_NOT_FOUND));
            validateFolderOwner(memberId, newParent);

            // 목적지 부모가 이동 대상 자신이거나 그 하위 폴더이면 순환 구조가 생깁니다.
            if (folderRepository.isSelfOrAncestor(folderId, newParent.getId())) {
                throw new FolderException(FolderErrorCode.INVALID_MOVE_TARGET);
            }
        }

        folder.moveTo(newParent);
        // 목적지에 같은 이름의 폴더가 있으면 커밋 전에 DUPLICATE_FOLDER_NAME 으로 드러냅니다.
        folderRepository.flush();

        return FolderConverter.toGetFolder(folder);
    }

    /**
     * 폴더 삭제
     * @param folderId
     * @return
     */
    @Transactional
    public Void deleteFolder(Long memberId, Long folderId) {
        Folder folder = validateFolder(folderId);
        validateFolderOwner(memberId, folder);

        folderRepository.delete(folder);
        return null;
    }

    // 검증 함수

    private Folder validateFolder(Long folderId) {
        return folderRepository.findById(folderId).orElseThrow(()-> new FolderException(FolderErrorCode.FOLDER_NOT_FOUND));
    }

    private void validateFolderOwner(Long memberId, Folder folder) {
        if (!folder.getMember().getId().equals(memberId)) {
            throw new FolderException(FolderErrorCode.FOLDER_ACCESS_DENIED);
        }
    }
}
