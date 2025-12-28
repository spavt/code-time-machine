CREATE INDEX idx_commit_record_repo_time
    ON commit_record (repo_id, commit_time);

CREATE INDEX idx_file_change_repo_extension
    ON file_change (repo_id, file_extension);

CREATE INDEX idx_file_change_repo_change_type
    ON file_change (repo_id, change_type);

CREATE INDEX idx_file_change_repo_path
    ON file_change (repo_id, file_path);
