CREATE INDEX idx_commit_record_repo_time
    ON commit_record (repo_id, commit_time);

CREATE INDEX idx_file_change_repo_extension
    ON file_change (repo_id, file_extension);

CREATE INDEX idx_file_change_repo_change_type
    ON file_change (repo_id, change_type);

CREATE INDEX idx_file_change_repo_path
    ON file_change (repo_id, file_path);

-- =====================================================
-- 性能优化索引 (2024-12-29)
-- =====================================================

-- 加速 commit 列表查询（按顺序分页）
CREATE INDEX idx_commit_record_repo_order
    ON commit_record (repo_id, commit_order);

-- 加速 commit hash 查找（用于批量 ID 映射）
CREATE INDEX idx_commit_record_repo_hash
    ON commit_record (repo_id, commit_hash);

-- 加速 file_change 按 commit 查询（JOIN 优化）
CREATE INDEX idx_file_change_commit_id
    ON file_change (commit_id);
