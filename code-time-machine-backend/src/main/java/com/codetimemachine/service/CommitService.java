package com.codetimemachine.service;

import com.codetimemachine.common.PageResult;
import com.codetimemachine.dto.CommitStatsDTO;
import com.codetimemachine.entity.AiAnalysis;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.entity.FileChange;

import java.util.List;

/**
 * 提交记录服务
 */
public interface CommitService {

    /**
     * 分页获取提交列表
     */
    PageResult<CommitRecord> getList(Long repoId, int page, int pageSize, String keyword);

    /**
     * 获取提交详情
     */
    CommitRecord getById(Long id);

    /**
     * 获取提交的文件变更
     */
    List<FileChange> getFileChanges(Long commitId);

    /**
     * 获取提交的AI分析
     */
    AiAnalysis getAiAnalysis(Long commitId);

    /**
     * 触发AI分析
     */
    AiAnalysis triggerAnalysis(Long commitId);

    /**
     * 获取提交的统计信息（按需计算）
     * 如果数据库中已有统计信息则直接返回，否则计算后缓存
     */
    CommitStatsDTO getStats(Long commitId);
}
