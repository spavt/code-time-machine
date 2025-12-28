package com.codetimemachine.service.impl;

import com.codetimemachine.dto.AnalyzeOptionsDTO;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.entity.FileChange;
import com.codetimemachine.entity.Repository;
import com.codetimemachine.mapper.CommitRecordMapper;
import com.codetimemachine.mapper.FileChangeMapper;
import com.codetimemachine.mapper.RepositoryMapper;
import com.codetimemachine.service.GitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepositoryAnalyzeService {

    private static final int BATCH_SIZE = 200;

    private final RepositoryMapper repositoryMapper;
    private final CommitRecordMapper commitRecordMapper;
    private final GitService gitService;
    private final SqlSessionFactory sqlSessionFactory;

    @Value("${app.max-commits:1000}")
    private int maxCommits;

    private final Map<Long, Integer> progressMap = new ConcurrentHashMap<>();

    public Integer getProgress(Long repoId) {
        return progressMap.get(repoId);
    }

    @Async("taskExecutor")
    public void analyzeAsync(Repository repo) {
        analyzeAsync(repo, AnalyzeOptionsDTO.recommended());
    }

    @Async("taskExecutor")
    public void analyzeAsync(Repository repo, AnalyzeOptionsDTO options) {
        try {
            log.info("Start analyzing repository: {}, depth: {}, since: {}, pathFilters: {}",
                    repo.getUrl(),
                    options.getDepth(),
                    options.getSince(),
                    options.getPathFilters());
            progressMap.put(repo.getId(), 5);

            boolean cloned = gitService.cloneRepository(repo.getUrl(), repo.getLocalPath(), options);
            if (!cloned) {
                throw new RuntimeException("Clone repository failed");
            }
            progressMap.put(repo.getId(), 20);

            Repository info = gitService.parseRepositoryInfo(repo.getLocalPath());
            repo.setDefaultBranch(info.getDefaultBranch());
            repo.setTotalCommits(info.getTotalCommits());
            repo.setTotalFiles(info.getTotalFiles());
            repo.setRepoSize(info.getRepoSize());
            progressMap.put(repo.getId(), 30);

            List<CommitRecord> commits = gitService.parseCommitsWithOptions(
                    repo.getLocalPath(),
                    options,
                    progress -> progressMap.put(repo.getId(), 30 + (int) (progress * 0.5)));

            log.info("Parsed {} commits with options", commits.size());

            batchInsertCommits(repo.getId(), commits);
            Map<String, Long> commitIdMap = buildCommitIdMap(repo.getId(), commits);
            batchInsertFileChanges(repo, commits, commitIdMap);

            progressMap.put(repo.getId(), 95);

            repo.setStatus(2);
            repo.setAnalyzeProgress(100);
            repo.setLastAnalyzedAt(LocalDateTime.now());
            repo.setUpdatedAt(LocalDateTime.now());

            boolean canLoadMore = info.getTotalCommits() > commits.size();
            repo.setCanLoadMore(canLoadMore);

            repositoryMapper.updateById(repo);

            progressMap.put(repo.getId(), 100);
            log.info("Repository analysis completed: {}, total commits: {}, canLoadMore: {}",
                    repo.getName(), commits.size(), canLoadMore);

        } catch (Exception e) {
            log.error("Repository analysis failed: {}", e.getMessage(), e);
            repo.setStatus(3);
            repo.setUpdatedAt(LocalDateTime.now());
            repositoryMapper.updateById(repo);
            progressMap.put(repo.getId(), -1);
        }
    }

    private void batchInsertCommits(Long repoId, List<CommitRecord> commits) {
        if (commits == null || commits.isEmpty()) {
            return;
        }
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            CommitRecordMapper batchMapper = session.getMapper(CommitRecordMapper.class);
            int count = 0;
            for (CommitRecord commit : commits) {
                commit.setRepoId(repoId);
                batchMapper.insert(commit);
                if (++count % BATCH_SIZE == 0) {
                    session.flushStatements();
                }
            }
            session.flushStatements();
            session.commit();
        } catch (Exception e) {
            log.error("Batch insert commits failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    private Map<String, Long> buildCommitIdMap(Long repoId, List<CommitRecord> commits) {
        Map<String, Long> commitIdMap = new HashMap<>();
        boolean missingId = false;

        for (CommitRecord commit : commits) {
            if (commit.getCommitHash() == null) {
                continue;
            }
            if (commit.getId() != null) {
                commitIdMap.put(commit.getCommitHash(), commit.getId());
            } else {
                missingId = true;
            }
        }

        if (missingId) {
            List<String> hashes = commits.stream()
                    .map(CommitRecord::getCommitHash)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!hashes.isEmpty()) {
                List<Map<String, Object>> rows = commitRecordMapper.getCommitIdsByHashes(repoId, hashes);
                for (Map<String, Object> row : rows) {
                    String hash = (String) row.get("commitHash");
                    Long id = ((Number) row.get("id")).longValue();
                    commitIdMap.put(hash, id);
                }
            }

            for (CommitRecord commit : commits) {
                if (commit.getId() == null) {
                    Long id = commitIdMap.get(commit.getCommitHash());
                    if (id != null) {
                        commit.setId(id);
                    }
                }
            }
        }

        return commitIdMap;
    }

    private void batchInsertFileChanges(Repository repo,
                                        List<CommitRecord> commits,
                                        Map<String, Long> commitIdMap) {
        if (commits == null || commits.isEmpty()) {
            return;
        }
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            FileChangeMapper batchMapper = session.getMapper(FileChangeMapper.class);
            int count = 0;

            for (CommitRecord commit : commits) {
                Long commitId = commit.getId();
                if (commitId == null) {
                    commitId = commitIdMap.get(commit.getCommitHash());
                }
                if (commitId == null) {
                    log.warn("Missing commitId for hash: {}", commit.getCommitHash());
                    continue;
                }

                List<FileChange> changes = gitService.parseFileChanges(
                        repo.getLocalPath(),
                        commit.getCommitHash());
                for (FileChange change : changes) {
                    change.setRepoId(repo.getId());
                    change.setCommitId(commitId);
                    batchMapper.insert(change);
                    if (++count % BATCH_SIZE == 0) {
                        session.flushStatements();
                    }
                }
            }

            session.flushStatements();
            session.commit();
        } catch (Exception e) {
            log.error("Batch insert file changes failed: {}", e.getMessage(), e);
            throw e;
        }
    }
}
