package com.tree.twig_tree.domain.workspace.entity;

import com.tree.twig_tree.domain.folder.entity.Folder;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workspaces")
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workspace_id")
    private Long id;

    @Column(nullable = false)
    private String name;

    // 생성일자
    //private LocalDateTime createAt;

    // 수정일자
    //private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = true)
    private Folder folder;

    /**
     * 기본 메서드
     */
    public void updateName(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("워크스페이스 이름은 공백일 수 없습니다.");
        }
        this.name = newName;
    }

}
