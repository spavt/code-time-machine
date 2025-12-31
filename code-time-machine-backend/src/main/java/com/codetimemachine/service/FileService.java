package com.codetimemachine.service;

import com.codetimemachine.dto.FileTimelineDTO;

import java.util.List;
import java.util.Map;

public interface FileService {

    List<Map<String, Object>> getFileTree(Long repoId);

    FileTimelineDTO getTimeline(Long repoId, String filePath, boolean includeContent);

    String getFileContent(Long repoId, Long commitId, String filePath);

    List<Map<String, Object>> searchFiles(Long repoId, String keyword);

    Map<String, Object> getEvolutionStory(Long repoId, String filePath);

    String getDiff(Long repoId, String fromCommit, String toCommit, String filePath);

    List<Map<String, Object>> getMethodTimeline(Long repoId, String filePath, String methodName);

    Map<Long, Map<String, Object>> getBatchFileContent(Long repoId, List<Long> commitIds, String filePath);
}
