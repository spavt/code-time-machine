package com.codetimemachine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codetimemachine.entity.FileChange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface FileChangeMapper extends BaseMapper<FileChange> {

    @Select("""
                SELECT fc.*, cr.commit_time, cr.commit_order, cr.author_name, cr.commit_message
                FROM file_change fc
                JOIN commit_record cr ON fc.commit_id = cr.id
                WHERE fc.repo_id = #{repoId} AND fc.file_path = #{filePath}
                ORDER BY cr.commit_order ASC
            """)
    List<FileChange> getFileHistory(@Param("repoId") Long repoId, @Param("filePath") String filePath);

    @Select("""
                SELECT DISTINCT file_path
                FROM file_change
                WHERE repo_id = #{repoId}
                ORDER BY file_path
            """)
    List<String> getDistinctFilePaths(@Param("repoId") Long repoId);

    @Select("""
                SELECT file_path as filePath, COUNT(*) as modifyCount
                FROM file_change
                WHERE repo_id = #{repoId}
                GROUP BY file_path
            """)
    List<Map<String, Object>> getFileModificationCounts(@Param("repoId") Long repoId);

    @Select("""
                SELECT file_extension as extension, COUNT(*) as fileCount
                FROM file_change
                WHERE repo_id = #{repoId}
                  AND file_extension IS NOT NULL
                  AND file_extension <> ''
                GROUP BY file_extension
                ORDER BY fileCount DESC
            """)
    List<Map<String, Object>> getFileTypeStats(@Param("repoId") Long repoId);

    @Select("""
                SELECT change_type as changeType, COUNT(*) as changeCount
                FROM file_change
                WHERE repo_id = #{repoId}
                GROUP BY change_type
            """)
    List<Map<String, Object>> getChangeTypeStats(@Param("repoId") Long repoId);

    @Select("""
                SELECT file_path as filePath, COUNT(*) as commitCount
                FROM file_change
                WHERE repo_id = #{repoId}
                  AND LOWER(file_path) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                GROUP BY file_path
                ORDER BY commitCount DESC
                LIMIT 50
            """)
    List<Map<String, Object>> searchFilePathsWithCommitCounts(@Param("repoId") Long repoId,
                                                              @Param("keyword") String keyword);

    @Select("""
                SELECT *
                FROM file_change
                WHERE commit_id = #{commitId}
                  AND change_type = 'RENAME'
                  AND (file_path = #{filePath} OR old_path = #{filePath})
                LIMIT 1
            """)
    FileChange findRenameForCommit(@Param("commitId") Long commitId, @Param("filePath") String filePath);
}
