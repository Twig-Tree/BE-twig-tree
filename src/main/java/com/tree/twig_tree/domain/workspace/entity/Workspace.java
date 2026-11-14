package com.tree.twig_tree.domain.workspace.entity;

import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@DynamicUpdate
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workspaces")
public class Workspace extends BaseEntity {

    /** 이름 최대 길이. DB 컬럼 길이이자 API·시드·자동 생성 이름의 검증 기준이다. */
    public static final int NAME_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workspace_id")
    private Long id;

    @Column(length = NAME_MAX_LENGTH, nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = true)
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 기본 메서드
     */
    public void updateName(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("워크스페이스 이름은 공백일 수 없습니다.");
        }
        this.name = newName;
    }

    // 워크스페이스 위치 이동 (newFolder 가 null 이면 폴더에 속하지 않는 최상위)
    public void moveTo(Folder newFolder) {
        this.folder = newFolder;
    }

}
