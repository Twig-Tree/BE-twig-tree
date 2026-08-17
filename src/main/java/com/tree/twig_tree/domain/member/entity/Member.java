package com.tree.twig_tree.domain.member.entity;

import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_members_provider",
                columnNames = {"provider", "provider_id"}
        )
)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(nullable = false)
    private String email;

    private String name;

    @Column(length = 512)
    private String profileImage;

    // 소셜 로그인 제공자
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    // 제공자 쪽 고유 식별자
    @Column(nullable = false)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Role role;

    // 로그인 시마다 구글 프로필 최신화
    public void updateProfile(String name, String profileImage) {
        this.name = name;
        this.profileImage = profileImage;
    }
}
