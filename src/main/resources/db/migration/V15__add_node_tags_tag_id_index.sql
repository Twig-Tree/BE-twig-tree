-- 태그 삭제 시 ON DELETE CASCADE가 WHERE tag_id = ? 로 node_tags를 조회한다.
-- uk_node_tag (node_id, tag_id)는 node_id가 선두라 tag_id 단독 조건에 쓰이지 않으므로,
-- tag_id 선두 인덱스를 추가해 태그·트리·워크스페이스 삭제 시 테이블 전체 스캔을 막는다.
CREATE INDEX idx_node_tags_tag_id
    ON node_tags (tag_id);
