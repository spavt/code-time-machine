package com.codetimemachine.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codetimemachine.common.BusinessException;
import com.codetimemachine.common.PageResult;
import com.codetimemachine.dto.AnalyzeOptionsDTO;
import com.codetimemachine.dto.RepoOverviewDTO;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.entity.FileChange;
import com.codetimemachine.entity.Repository;
import com.codetimemachine.mapper.CommitRecordMapper;
import com.codetimemachine.mapper.FileChangeMapper;
import com.codetimemachine.mapper.RepositoryMapper;
import com.codetimemachine.service.GitService;
import com.codetimemachine.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepositoryServiceImpl implements RepositoryService {

    private final RepositoryMapper repositoryMapper;
    private final CommitRecordMapper commitRecordMapper;
    private final FileChangeMapper fileChangeMapper;
    private final GitService gitService;
    private final RepositoryAnalyzeService repositoryAnalyzeService;

    @Value("${app.repo-storage-path:./repos}")
    private String repoStoragePath;

    @Override
    public PageResult<Repository> getList(int page, int pageSize) {
        Page<Repository> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Repository> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Repository::getCreatedAt);

        Page<Repository> result = repositoryMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    public Repository getById(Long id) {
        Repository repo = repositoryMapper.selectById(id);
        if (repo == null) {
            throw new BusinessException("仓库不存在");
        }
        return repo;
    }

    @Override
    @Transactional
    public Repository analyzeByUrl(String url) {
        // 使用默认配置
        return analyzeByUrl(url, AnalyzeOptionsDTO.recommended());
    }

    @Override
    @Transactional
    public Repository analyzeByUrl(String url, AnalyzeOptionsDTO options) {
        // 检查是否已存在
        LambdaQueryWrapper<Repository> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Repository::getUrl, url);
        Repository existing = repositoryMapper.selectOne(wrapper);

        if (existing != null) {
            // 如果正在分析中，直接返回
            if (existing.getStatus() == 1) {
                return existing;
            }
            // 如果已完成，也直接返回
            if (existing.getStatus() == 2) {
                return existing;
            }
        }

        // 创建新仓库记录
        Repository repo = new Repository();
        repo.setUrl(url);
        repo.setName(extractRepoName(url));
        repo.setStatus(1); // 分析中
        repo.setAnalyzeProgress(0);
        repo.setCreatedAt(LocalDateTime.now());
        repo.setUpdatedAt(LocalDateTime.now());

        // 保存分析配置
        repo.setAnalyzeDepth(options.getDepth());
        repo.setAnalyzeSince(options.getSince());
        if (options.getPathFilters() != null && !options.getPathFilters().isEmpty()) {
            repo.setAnalyzePathFilters(JSON.toJSONString(options.getPathFilters()));
        }

        // 生成本地路径
        String localPath = repoStoragePath + File.separator + UUID.randomUUID().toString();
        repo.setLocalPath(localPath);

        if (existing != null) {
            repo.setId(existing.getId());
            repositoryMapper.updateById(repo);
        } else {
            repositoryMapper.insert(repo);
        }

        // 异步执行分析（传递选项）
        repositoryAnalyzeService.analyzeAsync(repo, options);

        return repo;
    }

    @Override
    @Transactional
    public Repository fetchMoreHistory(Long repoId, int additionalDepth) {
        Repository repo = getById(repoId);

        // 调用 Git 服务获取更多历史
        boolean success = gitService.fetchMoreHistory(repo.getLocalPath(), additionalDepth);
        if (!success) {
            throw new BusinessException("获取更多历史失败");
        }

        // 更新仓库信息
        Repository info = gitService.parseRepositoryInfo(repo.getLocalPath());
        repo.setTotalCommits(info.getTotalCommits());

        // 更新分析深度
        int currentDepth = repo.getAnalyzeDepth() != null ? repo.getAnalyzeDepth() : 500;
        repo.setAnalyzeDepth(currentDepth + additionalDepth);

        repo.setUpdatedAt(LocalDateTime.now());
        repositoryMapper.updateById(repo);

        // 设置可以继续加载更多
        repo.setCanLoadMore(info.getTotalCommits() > repo.getAnalyzeDepth());

        return repo;
    }

    @Override
    public int getAnalyzeProgress(Long repoId) {
        Integer progress = repositoryAnalyzeService.getProgress(repoId);
        if (progress != null) {
            return progress;
        }

        Repository repo = repositoryMapper.selectById(repoId);
        if (repo != null) {
            return repo.getAnalyzeProgress() != null ? repo.getAnalyzeProgress() : 0;
        }
        return 0;
    }

    @Override
    public RepoOverviewDTO getOverview(Long repoId) {
        Repository repo = getById(repoId);

        RepoOverviewDTO dto = new RepoOverviewDTO();
        dto.setTotalCommits(repo.getTotalCommits());

        // 获取贡献者统计
        List<Map<String, Object>> contributors = commitRecordMapper.getContributorStats(repoId, 10);
        dto.setTopContributors(contributors);
        dto.setTotalAuthors(contributors.size());

        // 计算总行数
        long totalAdditions = 0, totalDeletions = 0;
        for (Map<String, Object> c : contributors) {
            totalAdditions += ((Number) c.getOrDefault("additions", 0)).longValue();
            totalDeletions += ((Number) c.getOrDefault("deletions", 0)).longValue();
        }
        dto.setTotalAdditions(totalAdditions);
        dto.setTotalDeletions(totalDeletions);

        // 获取首次和最后提交时间
        LambdaQueryWrapper<CommitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommitRecord::getRepoId, repoId)
                .orderByAsc(CommitRecord::getCommitOrder)
                .last("LIMIT 1");
        CommitRecord first = commitRecordMapper.selectOne(wrapper);
        if (first != null) {
            dto.setFirstCommit(first.getCommitTime().toString());
        }

        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommitRecord::getRepoId, repoId)
                .orderByDesc(CommitRecord::getCommitOrder)
                .last("LIMIT 1");
        CommitRecord last = commitRecordMapper.selectOne(wrapper);
        if (last != null) {
            dto.setLastCommit(last.getCommitTime().toString());
        }

        return dto;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Repository repo = getById(id);

        // 删除相关数据
        LambdaQueryWrapper<FileChange> fcWrapper = new LambdaQueryWrapper<>();
        fcWrapper.eq(FileChange::getRepoId, id);
        fileChangeMapper.delete(fcWrapper);

        LambdaQueryWrapper<CommitRecord> crWrapper = new LambdaQueryWrapper<>();
        crWrapper.eq(CommitRecord::getRepoId, id);
        commitRecordMapper.delete(crWrapper);

        // 删除仓库记录
        repositoryMapper.deleteById(id);

        // 删除本地文件
        if (repo.getLocalPath() != null) {
            gitService.deleteLocalRepository(repo.getLocalPath());
        }
    }

    private String extractRepoName(String url) {
        // 从URL提取仓库名
        // https://github.com/owner/repo.git -> repo
        String name = url;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf("/") + 1);
        }
        return name;
    }
}
