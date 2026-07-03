package com.tree.twig_tree.domain.folder.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "folders")
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "folder_id")
    private Long id;

    @Column(nullable = false)
    private String name;

    // 사용자 ID
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "member_id")
//    private Member member;

    // 부모 폴더 ID
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_parent_id")
    private Folder parent;

    // 자식 리스트
    @OneToMany(mappedBy = "parent", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @Builder.Default
    List<Folder> children = new ArrayList<>();

    /**
     * 기본 메서드
     */
    // 폴더 이름 수정
    public void updateName(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("폴더 제목은 공백일 수 없습니다.");
        }
        this.name = newName;
    }


}
