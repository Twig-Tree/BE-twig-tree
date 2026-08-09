package com.tree.twig_tree.global.security.jwt;

/**
 * 발급된 refresh 토큰의 jti 를 보관한다.
 * 서버가 발급 사실을 기억해야 회전·재사용 탐지·로그아웃이 가능해진다.
 */
public interface RefreshTokenStore {

    /** 새로 발급한 jti 를 refresh 수명만큼 보관한다. */
    void save(Long memberId, String jti);

    /** 아직 유효한 jti 인지 확인한다. */
    boolean exists(Long memberId, String jti);

    /**
     * 회전된 jti 를 짧은 유예 기간만 남기고 만료 예약한다.
     * 즉시 삭제하면 동시에 날아온 재발급 요청이 서로를 재사용으로 오인하게 된다.
     */
    void markRotated(Long memberId, String jti);

    /** 해당 세션 하나만 종료한다. (로그아웃) */
    void delete(Long memberId, String jti);

    /** 회원의 모든 세션을 종료한다. (재사용 탐지) */
    void deleteAll(Long memberId);
}
