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
        return analyzeByUrl(url, AnalyzeOptionsDTO.recommended());
    }

    @Override
    @Transactional
    public Repository analyzeByUrl(String url, AnalyzeOptionsDTO options) {
        LambdaQueryWrapper<Repository> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Repository::getUrl, url);
        Repository existing = repositoryMapper.selectOne(wrapper);

        if (existing != null) {
            if (existing.getStatus() == 1) {
                return existing;
            }
            if (existing.getStatus() == 2) {
                return existing;
            }
        }

        Repository repo = new Repository();
        repo.setUrl(url);
        repo.setName(extractRepoName(url));
        repo.setStatus(1);
        repo.setAnalyzeProgress(0);
        repo.setCreatedAt(LocalDateTime.now());
        repo.setUpdatedAt(LocalDateTime.now());

        repo.setAnalyzeDepth(options.getDepth());
        repo.setAnalyzeSince(options.getSince());
        if (options.getPathFilters() != null && !options.getPathFilters().isEmpty()) {
            repo.setAnalyzePathFilters(JSON.toJSONString(options.getPathFilters()));
        }

        String localPath = repoStoragePath + File.separator + UUID.randomUUID().toString();
        repo.setLocalPath(localPath);

        if (existing != null) {
            repo.setId(existing.getId());
            repositoryMapper.updateById(repo);
        } else {
            repositoryMapper.insert(repo);
        }

        repositoryAnalyzeService.analyzeAsync(repo, options);

        return repo;
    }

    @Override
    @Transactional
    public Repository fetchMoreHistory(Long repoId, int additionalDepth) {
        Repository repo = getById(repoId);

        boolean success = gitService.fetchMoreHistory(repo.getLocalPath(), additionalDepth);
        if (!success) {
            throw new BusinessException("获取更多历史失败");
        }

        Repository info = gitService.parseRepositoryInfo(repo.getLocalPath());
        repo.setTotalCommits(info.getTotalCommits());

        int currentDepth = repo.getAnalyzeDepth() != null ? repo.getAnalyzeDepth() : 500;
        repo.setAnalyzeDepth(currentDepth + additionalDepth);

        repo.setUpdatedAt(LocalDateTime.now());
        repositoryMapper.updateById(repo);

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

        List<Map<String, Object>> contributors = commitRecordMapper.getContributorStats(repoId, 10);
        dto.setTopContributors(contributors);
        dto.setTotalAuthors(contributors.size());

        long totalAdditions = 0, totalDeletions = 0;
        for (Map<String, Object> c : contributors) {
            totalAdditions += ((Number) c.getOrDefault("additions", 0)).longValue();
            totalDeletions += ((Number) c.getOrDefault("deletions", 0)).longValue();
        }
        dto.setTotalAdditions(totalAdditions);
        dto.setTotalDeletions(totalDeletions);

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

        LambdaQueryWrapper<FileChange> fcWrapper = new LambdaQueryWrapper<>();
        fcWrapper.eq(FileChange::getRepoId, id);
        fileChangeMapper.delete(fcWrapper);

        LambdaQueryWrapper<CommitRecord> crWrapper = new LambdaQueryWrapper<>();
        crWrapper.eq(CommitRecord::getRepoId, id);
        commitRecordMapper.delete(crWrapper);

        repositoryMapper.deleteById(id);

        if (repo.getLocalPath() != null) {
            gitService.deleteLocalRepository(repo.getLocalPath());
        }
    }

    private String extractRepoName(String url) {
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
