package com.codetimemachine.service;

import com.codetimemachine.common.PageResult;
import com.codetimemachine.dto.CommitStatsDTO;
import com.codetimemachine.entity.AiAnalysis;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.entity.FileChange;

import java.util.List;

public interface CommitService {

    PageResult<CommitRecord> getList(Long repoId, int page, int pageSize, String keyword);

    CommitRecord getById(Long id);

    List<FileChange> getFileChanges(Long commitId);

    AiAnalysis getAiAnalysis(Long commitId);

    AiAnalysis triggerAnalysis(Long commitId);

    CommitStatsDTO getStats(Long commitId);
}
