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

/**
 * Git仓库解析服务
 */
public interface GitService {

    /**
     * 克隆仓库（默认配置）
     * 
     * @param url       仓库URL
     * @param localPath 本地路径
     * @return 是否成功
     */
    boolean cloneRepository(String url, String localPath);

    /**
     * 克隆仓库（带选项）
     * 
     * @param url       仓库URL
     * @param localPath 本地路径
     * @param options   分析选项
     * @return 是否成功
     */
    boolean cloneRepository(String url, String localPath, AnalyzeOptionsDTO options);

    /**
     * 解析仓库基本信息
     */
    Repository parseRepositoryInfo(String localPath);

    /**
     * 获取所有提交记录（默认配置）
     * 
     * @param localPath        本地路径
     * @param maxCommits       最大提交数
     * @param progressCallback 进度回调
     * @return 提交列表
     */
    List<CommitRecord> parseCommits(String localPath, int maxCommits, Consumer<Integer> progressCallback);

    /**
     * 获取提交记录（带过滤选项）
     * 
     * @param localPath        本地路径
     * @param options          分析选项（包含深度、时间范围、路径过滤）
     * @param progressCallback 进度回调
     * @return 提交列表
     */
    List<CommitRecord> parseCommitsWithOptions(String localPath, AnalyzeOptionsDTO options,
            Consumer<Integer> progressCallback);

    /**
     * 增量获取更多历史
     * 
     * @param localPath       本地路径
     * @param additionalDepth 额外获取的提交数
     * @return 是否成功
     */
    boolean fetchMoreHistory(String localPath, int additionalDepth);

    /**
     * 获取指定提交的文件变更
     */
    List<FileChange> parseFileChanges(String localPath, String commitHash);

    /**
     * 获取文件在指定提交时的内容
     */
    String getFileContent(String localPath, String commitHash, String filePath);

    /**
     * 获取两个提交之间的diff
     */
    String getDiff(String localPath, String fromCommit, String toCommit, String filePath);

    /**
     * 删除本地仓库
     */
    void deleteLocalRepository(String localPath);

    /**
     * 计算提交的统计信息（新增/删除行数，变更文件数）
     * 支持 partial clone，会使用原生 git 作为 fallback
     *
     * @param localPath  本地路径
     * @param commitHash 提交哈希
     * @return 统计信息
     */
    CommitStatsDTO calculateCommitStats(String localPath, String commitHash);

    /**
     * 批量预取文件内容（用于 partial clone）
     * 一次性获取多个 commit:file 对应的内容，减少 git 进程调用
     *
     * @param localPath 本地路径
     * @param requests  预取请求列表
     * @return Map: "commitHash:filePath" -> 文件内容
     */
    Map<String, String> prefetchFileContents(String localPath, List<PrefetchRequest> requests);

    /**
     * 批量解析多个 commit 的文件变更（一次 git log 命令获取所有）
     * 性能优化：避免对每个 commit 单独调用 git diff-tree
     *
     * @param localPath    本地路径
     * @param commitHashes commit 哈希列表
     * @return Map: commitHash -> List<FileChange>
     */
    Map<String, List<FileChange>> parseFileChangesBatch(String localPath, List<String> commitHashes);
}
