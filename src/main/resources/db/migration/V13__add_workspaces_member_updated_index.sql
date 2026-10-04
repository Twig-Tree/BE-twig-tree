-- 최근 워크스페이스 목록 커서 페이지네이션용 인덱스
-- WHERE member_id = ? ORDER BY updated_at DESC, workspace_id DESC 순서와 일치시켜
-- 정렬 없이 인덱스를 순서대로 읽고, 커서 위치부터 바로 조회할 수 있게 한다.
CREATE INDEX idx_workspaces_member_updated
    ON workspaces (member_id, updated_at DESC, workspace_id DESC);
