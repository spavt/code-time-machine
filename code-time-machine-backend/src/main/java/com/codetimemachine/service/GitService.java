package com.codetimemachine.service;

import com.codetimemachine.dto.AnalyzeOptionsDTO;
import com.codetimemachine.dto.CommitStatsDTO;
import com.codetimemachine.dto.PrefetchRequest;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.entity.FileChange;
import com.codetimemachine.entity.Repository;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface GitService {

    boolean cloneRepository(String url, String localPath);

    boolean cloneRepository(String url, String localPath, AnalyzeOptionsDTO options);

    Repository parseRepositoryInfo(String localPath);

    List<CommitRecord> parseCommits(String localPath, int maxCommits, Consumer<Integer> progressCallback);

    List<CommitRecord> parseCommitsWithOptions(String localPath, AnalyzeOptionsDTO options,
            Consumer<Integer> progressCallback);

    boolean fetchMoreHistory(String localPath, int additionalDepth);

    List<FileChange> parseFileChanges(String localPath, String commitHash);

    String getFileContent(String localPath, String commitHash, String filePath);

    String getDiff(String localPath, String fromCommit, String toCommit, String filePath);

    void deleteLocalRepository(String localPath);

    CommitStatsDTO calculateCommitStats(String localPath, String commitHash);

    Map<String, String> prefetchFileContents(String localPath, List<PrefetchRequest> requests);

    Map<String, List<FileChange>> parseFileChangesBatch(String localPath, List<String> commitHashes);
}
