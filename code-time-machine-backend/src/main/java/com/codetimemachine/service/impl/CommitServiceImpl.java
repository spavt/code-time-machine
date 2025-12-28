package com.codetimemachine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codetimemachine.common.BusinessException;
import com.codetimemachine.common.PageResult;
import com.codetimemachine.dto.CommitStatsDTO;
import com.codetimemachine.entity.AiAnalysis;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.entity.FileChange;
import com.codetimemachine.entity.Repository;
import com.codetimemachine.mapper.AiAnalysisMapper;
import com.codetimemachine.mapper.CommitRecordMapper;
import com.codetimemachine.mapper.FileChangeMapper;
import com.codetimemachine.mapper.RepositoryMapper;
import com.codetimemachine.service.AiService;
import com.codetimemachine.service.CommitService;
import com.codetimemachine.service.GitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommitServiceImpl implements CommitService {

    private final CommitRecordMapper commitRecordMapper;
    private final FileChangeMapper fileChangeMapper;
    private final AiAnalysisMapper aiAnalysisMapper;
    private final RepositoryMapper repositoryMapper;
    private final AiService aiService;
    private final GitService gitService;

    @Override
    public PageResult<CommitRecord> getList(Long repoId, int page, int pageSize, String keyword) {
        Page<CommitRecord> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<CommitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommitRecord::getRepoId, repoId);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(CommitRecord::getCommitMessage, keyword)
                    .or()
                    .like(CommitRecord::getAuthorName, keyword)
                    .or()
                    .like(CommitRecord::getShortHash, keyword));
        }

        wrapper.orderByDesc(CommitRecord::getCommitOrder);

        Page<CommitRecord> result = commitRecordMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    public CommitRecord getById(Long id) {
        CommitRecord commit = commitRecordMapper.selectById(id);
        if (commit == null) {
            throw new BusinessException("提交记录不存在");
        }
        return commit;
    }

    @Override
    public List<FileChange> getFileChanges(Long commitId) {
        LambdaQueryWrapper<FileChange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileChange::getCommitId, commitId);
        wrapper.orderByAsc(FileChange::getFilePath);
        return fileChangeMapper.selectList(wrapper);
    }

    @Override
    public AiAnalysis getAiAnalysis(Long commitId) {
        LambdaQueryWrapper<AiAnalysis> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAnalysis::getCommitId, commitId);
        wrapper.eq(AiAnalysis::getAnalysisType, "COMMIT");
        return aiAnalysisMapper.selectOne(wrapper);
    }

    @Override
    public AiAnalysis triggerAnalysis(Long commitId) {
        // 检查是否已有分析
        AiAnalysis existing = getAiAnalysis(commitId);
        if (existing != null) {
            return existing;
        }

        CommitRecord commit = getById(commitId);
        List<FileChange> changes = getFileChanges(commitId);

        // 构建diff摘要
        StringBuilder diffSummary = new StringBuilder();
        for (FileChange change : changes) {
            diffSummary.append(String.format("%s: %s (+%d/-%d)\n",
                    change.getChangeType(),
                    change.getFilePath(),
                    change.getAdditions() != null ? change.getAdditions() : 0,
                    change.getDeletions() != null ? change.getDeletions() : 0));
            if (change.getDiffText() != null && diffSummary.length() < 2000) {
                diffSummary.append(change.getDiffText().substring(0,
                        Math.min(500, change.getDiffText().length())));
                diffSummary.append("\n---\n");
            }
        }

        // 调用AI分析
        AiAnalysis analysis = aiService.analyzeCommit(commit, diffSummary.toString());
        analysis.setCommitId(commitId);
        analysis.setRepoId(commit.getRepoId());
        analysis.setAnalysisType("COMMIT");
        analysis.setCreatedAt(LocalDateTime.now());

        aiAnalysisMapper.insert(analysis);
        return analysis;
    }

    @Override
    public CommitStatsDTO getStats(Long commitId) {
        log.debug("获取提交统计: {}", commitId);

        // 先检查数据库中是否已有统计
        CommitRecord commit = getById(commitId);

        if (commit.getAdditions() != null && commit.getDeletions() != null) {
            // 已有统计，直接返回
            return CommitStatsDTO.of(
                    commit.getAdditions(),
                    commit.getDeletions(),
                    commit.getFilesChanged() != null ? commit.getFilesChanged() : 0);
        }

        // 需要计算统计
        Repository repo = repositoryMapper.selectById(commit.getRepoId());
        if (repo == null || repo.getLocalPath() == null) {
            log.warn("仓库不存在或本地路径为空: {}", commit.getRepoId());
            return CommitStatsDTO.empty();
        }

        // 计算统计
        CommitStatsDTO stats = gitService.calculateCommitStats(repo.getLocalPath(), commit.getCommitHash());

        if (stats.getCalculated()) {
            // 缓存到数据库
            commit.setAdditions(stats.getAdditions());
            commit.setDeletions(stats.getDeletions());
            commit.setFilesChanged(stats.getFilesChanged());
            commitRecordMapper.updateById(commit);
            log.debug("已缓存提交统计: {} -> +{} -{}", commitId, stats.getAdditions(), stats.getDeletions());
        }

        return stats;
    }
}
