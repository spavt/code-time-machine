package com.codetimemachine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codetimemachine.entity.CommitRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CommitRecordMapper extends BaseMapper<CommitRecord> {
    
    @Select("""
        SELECT 
            author_name as authorName,
            author_email as authorEmail,
            COUNT(*) as commitCount,
            SUM(additions) as additions,
            SUM(deletions) as deletions,
            MIN(commit_time) as firstCommit,
            MAX(commit_time) as lastCommit
        FROM commit_record 
        WHERE repo_id = #{repoId}
        GROUP BY author_name, author_email
        ORDER BY commitCount DESC
        LIMIT #{limit}
    """)
    List<Map<String, Object>> getContributorStats(@Param("repoId") Long repoId, @Param("limit") int limit);

    @Select("""
        SELECT DATE(commit_time) as commitDate, COUNT(*) as commitCount
        FROM commit_record
        WHERE repo_id = #{repoId}
        GROUP BY DATE(commit_time)
        ORDER BY commitDate ASC
    """)
    List<Map<String, Object>> getCommitFrequency(@Param("repoId") Long repoId);

    @Select("""
        SELECT (DAYOFWEEK(commit_time) - 1) as dayOfWeek, HOUR(commit_time) as hour, COUNT(*) as commitCount
        FROM commit_record
        WHERE repo_id = #{repoId}
        GROUP BY (DAYOFWEEK(commit_time) - 1), HOUR(commit_time)
        ORDER BY dayOfWeek ASC, hour ASC
    """)
    List<Map<String, Object>> getActivityHeatmap(@Param("repoId") Long repoId);

    @Select("""
        <script>
        SELECT id, commit_hash as commitHash
        FROM commit_record
        WHERE repo_id = #{repoId}
          AND commit_hash IN
        <foreach collection="hashes" item="hash" open="(" separator="," close=")">
            #{hash}
        </foreach>
        </script>
    """)
    List<Map<String, Object>> getCommitIdsByHashes(@Param("repoId") Long repoId,
                                                   @Param("hashes") List<String> hashes);
}
