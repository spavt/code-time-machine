package com.codetimemachine.service;

import com.codetimemachine.dto.FileTimelineDTO;

import java.util.List;
import java.util.Map;

/**
 * 文件服务
 */
public interface FileService {

    /**
     * 获取文件树
     */
    List<Map<String, Object>> getFileTree(Long repoId);

    /**
     * 获取文件演化时间线
     */
    FileTimelineDTO getTimeline(Long repoId, String filePath, boolean includeContent);

    /**
     * 获取文件在特定提交时的内容
     */
    String getFileContent(Long repoId, Long commitId, String filePath);

    /**
     * 搜索文件
     */
    List<Map<String, Object>> searchFiles(Long repoId, String keyword);

    /**
     * 获取文件演进故事
     */
    Map<String, Object> getEvolutionStory(Long repoId, String filePath);

    /**
     * 获取文件在两个提交之间的diff
     */
    String getDiff(Long repoId, String fromCommit, String toCommit, String filePath);

    /**
     * 获取方法演化时间线
     */
    List<Map<String, Object>> getMethodTimeline(Long repoId, String filePath, String methodName);

    /**
     * 批量获取文件内容（用于预加载优化）
     * 使用并行处理提高效率
     */
    Map<Long, Map<String, Object>> getBatchFileContent(Long repoId, List<Long> commitIds, String filePath);
}
