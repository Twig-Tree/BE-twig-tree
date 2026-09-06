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
