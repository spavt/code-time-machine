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

    @Select("""
            <script>
            SELECT
                cr.id as commitId,
                cr.short_hash as shortHash,
                cr.commit_message as commitMessage,
                cr.author_name as authorName,
                cr.commit_time as commitTime,
                COUNT(DISTINCT fc.file_path) as touchedFiles
            FROM commit_record cr
            JOIN file_change fc ON fc.commit_id = cr.id
            WHERE cr.repo_id = #{repoId}
              <if test="filePaths != null and filePaths.size() > 0">
                AND fc.file_path IN
                <foreach collection="filePaths" item="path" open="(" separator="," close=")">
                    #{path}
                </foreach>
              </if>
            GROUP BY cr.id, cr.short_hash, cr.commit_message, cr.author_name, cr.commit_time, cr.commit_order
            ORDER BY touchedFiles DESC, cr.commit_order DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> getKeyCommitsByFiles(@Param("repoId") Long repoId,
                                                   @Param("filePaths") List<String> filePaths,
                                                   @Param("limit") int limit);

    @Select("""
            <script>
            SELECT
                fc1.file_path as fileA,
                fc2.file_path as fileB,
                COUNT(DISTINCT fc1.commit_id) as coChangeCount
            FROM file_change fc1
            JOIN file_change fc2
              ON fc1.commit_id = fc2.commit_id
             AND fc1.file_path &lt; fc2.file_path
            WHERE fc1.repo_id = #{repoId}
              AND fc2.repo_id = #{repoId}
              <if test="filePaths != null and filePaths.size() &gt; 0">
                AND fc1.file_path IN
                <foreach collection="filePaths" item="path" open="(" separator="," close=")">
                    #{path}
                </foreach>
                AND fc2.file_path IN
                <foreach collection="filePaths" item="path" open="(" separator="," close=")">
                    #{path}
                </foreach>
              </if>
            GROUP BY fc1.file_path, fc2.file_path
            HAVING COUNT(DISTINCT fc1.commit_id) &gt;= #{minCoChange}
            ORDER BY coChangeCount DESC
            </script>
            """)
    List<Map<String, Object>> getCoChangePairs(@Param("repoId") Long repoId,
                                               @Param("filePaths") List<String> filePaths,
                                               @Param("minCoChange") int minCoChange);

    @Select("""
            <script>
            SELECT
                cr.id as commitId,
                cr.commit_order as commitOrder,
                cr.short_hash as shortHash,
                cr.commit_message as commitMessage,
                cr.author_name as authorName,
                cr.commit_time as commitTime,
                COUNT(DISTINCT fc.file_path) as touchedFiles,
                GROUP_CONCAT(DISTINCT fc.file_path ORDER BY fc.file_path SEPARATOR '||') as hitFiles,
                GROUP_CONCAT(DISTINCT fc.change_type ORDER BY fc.change_type SEPARATOR ',') as changeTypes,
                SUM(COALESCE(fc.additions, 0)) as additions,
                SUM(COALESCE(fc.deletions, 0)) as deletions
            FROM commit_record cr
            JOIN file_change fc ON fc.commit_id = cr.id
            WHERE cr.repo_id = #{repoId}
              <if test="filePaths != null and filePaths.size() &gt; 0">
                AND fc.file_path IN
                <foreach collection="filePaths" item="path" open="(" separator="," close=")">
                    #{path}
                </foreach>
              </if>
            GROUP BY cr.id, cr.commit_order, cr.short_hash, cr.commit_message, cr.author_name, cr.commit_time
            ORDER BY cr.commit_order ASC
            </script>
            """)
    List<Map<String, Object>> getModuleCommitCandidates(@Param("repoId") Long repoId,
                                                        @Param("filePaths") List<String> filePaths);
}
