package com.codetimemachine.service;

import com.codetimemachine.common.PageResult;
import com.codetimemachine.dto.AnalyzeOptionsDTO;
import com.codetimemachine.dto.RepoOverviewDTO;
import com.codetimemachine.entity.Repository;

/**
 * 仓库服务
 */
public interface RepositoryService {

    /**
     * 分页获取仓库列表
     */
    PageResult<Repository> getList(int page, int pageSize);

    /**
     * 根据ID获取仓库
     */
    Repository getById(Long id);

    /**
     * 通过URL分析仓库（异步，默认配置）
     */
    Repository analyzeByUrl(String url);

    /**
     * 通过URL分析仓库（异步，带选项）
     */
    Repository analyzeByUrl(String url, AnalyzeOptionsDTO options);

    /**
     * 获取分析进度
     */
    int getAnalyzeProgress(Long repoId);

    /**
     * 获取仓库概览
     */
    RepoOverviewDTO getOverview(Long repoId);

    /**
     * 删除仓库
     */
    void delete(Long id);

    /**
     * 增量获取更多历史
     * 
     * @param repoId          仓库ID
     * @param additionalDepth 额外获取的提交数
     * @return 更新后的仓库信息
     */
    Repository fetchMoreHistory(Long repoId, int additionalDepth);
}
