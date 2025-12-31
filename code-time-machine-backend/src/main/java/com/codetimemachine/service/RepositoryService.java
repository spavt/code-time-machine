package com.codetimemachine.service;

import com.codetimemachine.common.PageResult;
import com.codetimemachine.dto.AnalyzeOptionsDTO;
import com.codetimemachine.dto.RepoOverviewDTO;
import com.codetimemachine.entity.Repository;

public interface RepositoryService {

    PageResult<Repository> getList(int page, int pageSize);

    Repository getById(Long id);

    Repository analyzeByUrl(String url);

    Repository analyzeByUrl(String url, AnalyzeOptionsDTO options);

    int getAnalyzeProgress(Long repoId);

    RepoOverviewDTO getOverview(Long repoId);

    void delete(Long id);

    Repository fetchMoreHistory(Long repoId, int additionalDepth);
}
