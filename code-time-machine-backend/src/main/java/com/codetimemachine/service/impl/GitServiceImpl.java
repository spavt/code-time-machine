package com.codetimemachine.service.impl;

import com.codetimemachine.dto.AnalyzeOptionsDTO;
import com.codetimemachine.dto.CommitStatsDTO;
import com.codetimemachine.dto.PrefetchRequest;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.entity.FileChange;
import com.codetimemachine.entity.Repository;
import com.codetimemachine.service.GitService;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitServiceImpl implements GitService {

    private final Cache<String, String> fileContentCache;

    @Override
    public boolean cloneRepository(String url, String localPath) {
        return cloneRepository(url, localPath, AnalyzeOptionsDTO.recommended());
    }

    @Override
    public boolean cloneRepository(String url, String localPath, AnalyzeOptionsDTO options) {
        log.info("开始克隆仓库: {} -> {}, 深度: {}", url, localPath,
                options.getDepth() == -1 ? "全部" : options.getDepth());

        File localDir = new File(localPath);
        if (localDir.exists()) {
            File gitDir = new File(localDir, ".git");
            if (gitDir.exists()) {
                log.info("目录已存在，跳过克隆");
                return true;
            }
            deleteDirectory(localDir);
        }

        if (cloneWithNativeGit(url, localPath, options)) {
            return true;
        }

        log.info("原生Git不可用，回退到JGit");
        return cloneWithJGit(url, localPath, options);
    }

    private boolean cloneWithNativeGit(String url, String localPath, AnalyzeOptionsDTO options) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            log.info("操作系统: {}, isWindows: {}", System.getProperty("os.name"), isWindows);

            List<String> checkCmd = new ArrayList<>();
            if (isWindows) {
                checkCmd.add("cmd.exe");
                checkCmd.add("/c");
            }
            checkCmd.add("git");
            checkCmd.add("--version");

            log.info("检查Git命令: {}", String.join(" ", checkCmd));

            ProcessBuilder checkGit = new ProcessBuilder(checkCmd);
            checkGit.redirectErrorStream(true);
            Process checkProcess = checkGit.start();
            boolean finished = checkProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                log.warn("Git版本检查超时");
                checkProcess.destroyForcibly();
                return false;
            }
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(checkProcess.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            reader.close();

            int checkResult = checkProcess.exitValue();
            if (checkResult != 0) {
                log.warn("原生Git检测失败，退出码: {}, 输出: {}", checkResult, output);
                return false;
            }
            log.info("检测到Git: {}", output.toString().trim());
            List<String> cmd = new ArrayList<>();
            if (isWindows) {
                cmd.add("cmd.exe");
                cmd.add("/c");
            }
            cmd.add("git");
            cmd.add("-c");
            cmd.add("core.longpaths=true");
            cmd.add("clone");

            int cloneDepth = options.getEffectiveCloneDepth();
            if (cloneDepth > 0 && options.getShallow()) {
                cmd.add("--depth=" + cloneDepth);
                cmd.add("--filter=blob:none");
                log.info("使用原生Git浅克隆，深度: {}, 启用blob过滤", cloneDepth);
            }

            if (options.getSingleBranch()) {
                cmd.add("--single-branch");
            }

            cmd.add("--progress");
            cmd.add(url.endsWith(".git") ? url : url + ".git");
            cmd.add(localPath.replace("\\", "/"));

            log.info("执行克隆命令: {}", String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            java.io.BufferedReader cloneReader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String cloneLine;
            StringBuilder fullOutput = new StringBuilder();
            while ((cloneLine = cloneReader.readLine()) != null) {
                log.info("git: {}", cloneLine);
                fullOutput.append(cloneLine).append("\n");
            }
            cloneReader.close();

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("原生Git克隆成功");
                return true;
            } else {
                log.warn("原生Git克隆失败，退出码: {}, 输出:\n{}", exitCode, fullOutput);
                deleteDirectory(new File(localPath));
                return false;
            }
        } catch (Exception e) {
            log.warn("原生Git克隆异常: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean cloneWithJGit(String url, String localPath, AnalyzeOptionsDTO options) {
        File localDir = new File(localPath);
        String[] candidates = url.endsWith(".git") ? new String[] { url } : new String[] { url, url + ".git" };

        for (String candidate : candidates) {
            try {
                var cloneCmd = Git.cloneRepository()
                        .setURI(candidate)
                        .setDirectory(localDir)
                        .setCloneAllBranches(!options.getSingleBranch());

                int cloneDepth = options.getEffectiveCloneDepth();
                if (cloneDepth > 0 && options.getShallow()) {
                    cloneCmd.setDepth(cloneDepth);
                    log.info("JGit浅克隆，深度: {}", cloneDepth);
                } else {
                    log.info("JGit完整克隆");
                }

                cloneCmd.call().close();

                log.info("JGit克隆成功: {}", candidate);
                return true;
            } catch (GitAPIException e) {
                log.error("JGit克隆失败: {}", candidate, e);
                deleteDirectory(localDir);
            }
        }

        return false;
    }

    @Override
    public boolean fetchMoreHistory(String localPath, int additionalDepth) {
        log.info("增量获取历史: {}, 额外深度: {}", localPath, additionalDepth);
        try (Git git = Git.open(new File(localPath))) {
            git.fetch()
                    .setDepth(additionalDepth)
                    .call();
            log.info("成功获取更多历史");
            return true;
        } catch (Exception e) {
            log.error("获取更多历史失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Repository parseRepositoryInfo(String localPath) {
        Repository repo = new Repository();

        try (Git git = Git.open(new File(localPath))) {
            Ref head = git.getRepository().exactRef("HEAD");
            if (head != null && head.getTarget() != null) {
                String branch = head.getTarget().getName();
                repo.setDefaultBranch(branch.replace("refs/heads/", ""));
            }

            int commitCount = 0;
            for (RevCommit commit : git.log().call()) {
                commitCount++;
            }
            repo.setTotalCommits(commitCount);

            RevCommit headCommit = git.log().setMaxCount(1).call().iterator().next();
            int fileCount = countFiles(git, headCommit);
            repo.setTotalFiles(fileCount);

            File repoDir = new File(localPath);
            repo.setRepoSize(calculateDirectorySize(repoDir));

        } catch (Exception e) {
            log.error("解析仓库信息失败: {}", e.getMessage());
        }

        return repo;
    }

    @Override
    public List<CommitRecord> parseCommits(String localPath, int maxCommits, Consumer<Integer> progressCallback) {
        List<CommitRecord> commits = new ArrayList<>();

        try (Git git = Git.open(new File(localPath))) {
            Iterable<RevCommit> logs = git.log().setMaxCount(maxCommits).call();

            List<RevCommit> allCommits = new ArrayList<>();
            for (RevCommit commit : logs) {
                allCommits.add(commit);
            }

            Collections.reverse(allCommits);

            int total = allCommits.size();
            int processed = 0;

            for (RevCommit revCommit : allCommits) {
                CommitRecord record = convertToCommitRecord(git, revCommit, processed + 1);
                commits.add(record);

                processed++;
                if (progressCallback != null && processed % 10 == 0) {
                    int progress = (int) ((processed * 100.0) / total);
                    progressCallback.accept(progress);
                }
            }

            log.info("解析完成，共 {} 次提交", commits.size());
        } catch (Exception e) {
            log.error("解析提交记录失败: {}", e.getMessage());
        }

        return commits;
    }

    @Override
    public List<CommitRecord> parseCommitsWithOptions(String localPath, AnalyzeOptionsDTO options,
            Consumer<Integer> progressCallback) {
        List<CommitRecord> commits = new ArrayList<>();

        try (Git git = Git.open(new File(localPath))) {
            LogCommand logCmd = git.log();

            int maxCommits = options.getEffectiveAnalyzeDepth();
            if (maxCommits < Integer.MAX_VALUE) {
                logCmd.setMaxCount(maxCommits);
            }

            if (options.getSince() != null || options.getUntil() != null) {
                Date since = options.getSince() != null
                        ? Date.from(options.getSince().atZone(ZoneId.systemDefault()).toInstant())
                        : new Date(0);
                Date until = options.getUntil() != null
                        ? Date.from(options.getUntil().atZone(ZoneId.systemDefault()).toInstant())
                        : new Date();

                RevFilter timeFilter = CommitTimeRevFilter.between(since, until);
                logCmd.setRevFilter(timeFilter);
                log.info("应用时间过滤: {} 到 {}", since, until);
            }

            if (options.getPathFilters() != null && !options.getPathFilters().isEmpty()) {
                for (String pathFilter : options.getPathFilters()) {
                    logCmd.addPath(pathFilter);
                }
                log.info("应用路径过滤: {}", options.getPathFilters());
            }

            Iterable<RevCommit> logs = logCmd.call();

            List<RevCommit> allCommits = new ArrayList<>();
            for (RevCommit commit : logs) {
                allCommits.add(commit);
            }

            Collections.reverse(allCommits);

            int total = allCommits.size();
            int processed = 0;

            log.info("开始解析 {} 次提交（深度限制: {}, 时间过滤: {}, 路径过滤: {}）",
                    total,
                    options.getDepth(),
                    options.getSince() != null || options.getUntil() != null,
                    options.getPathFilters() != null && !options.getPathFilters().isEmpty());

            for (RevCommit revCommit : allCommits) {
                CommitRecord record = convertToCommitRecord(git, revCommit, processed + 1);
                commits.add(record);

                processed++;
                if (progressCallback != null && processed % 10 == 0) {
                    int progress = (int) ((processed * 100.0) / total);
                    progressCallback.accept(progress);
                }
            }

            log.info("解析完成，共 {} 次提交", commits.size());
        } catch (Exception e) {
            log.error("解析提交记录失败: {}", e.getMessage(), e);
        }

        return commits;
    }

    @Override
    public List<FileChange> parseFileChanges(String localPath, String commitHash) {
        List<FileChange> changes = new ArrayList<>();

        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            List<String> cmd = new ArrayList<>();
            if (isWindows) {
                cmd.add("cmd.exe");
                cmd.add("/c");
            }
            cmd.add("git");
            cmd.add("diff-tree");
            cmd.add("--no-commit-id");
            cmd.add("--name-status");
            cmd.add("-r");
            cmd.add(commitHash);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(localPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    FileChange change = new FileChange();
                    String status = parts[0];
                    String filePath;

                    if (status.startsWith("R") || status.startsWith("C")) {
                        change.setChangeType(status.startsWith("R") ? "RENAME" : "COPY");
                        change.setOldPath(parts[1]);
                        filePath = parts.length > 2 ? parts[2] : parts[1];
                    } else {
                        filePath = parts[1];
                        change.setChangeType(switch (status) {
                            case "A" -> "ADD";
                            case "D" -> "DELETE";
                            case "M" -> "MODIFY";
                            default -> "MODIFY";
                        });
                    }

                    change.setFilePath(filePath);
                    change.setFileName(filePath.contains("/")
                            ? filePath.substring(filePath.lastIndexOf("/") + 1)
                            : filePath);

                    if (filePath.contains(".")) {
                        String ext = filePath.substring(filePath.lastIndexOf(".") + 1);
                        if (ext.length() > 10)
                            ext = ext.substring(0, 10);
                        change.setFileExtension(ext);
                    }

                    change.setAdditions(null);
                    change.setDeletions(null);

                    changes.add(change);
                }
            }
            reader.close();
            process.waitFor();

        } catch (Exception e) {
            log.error("解析文件变更失败: {}", e.getMessage());
        }

        return changes;
    }

    private void fillFileStatsWithNativeGit(String localPath, String commitHash, List<FileChange> changes) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            List<String> cmd = new ArrayList<>();
            if (isWindows) {
                cmd.add("cmd.exe");
                cmd.add("/c");
            }
            cmd.add("git");
            cmd.add("show");
            cmd.add("--numstat");
            cmd.add("--format=");
            cmd.add(commitHash);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(localPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String line;

            Map<String, FileChange> pathMap = new HashMap<>();
            for (FileChange change : changes) {
                pathMap.put(change.getFilePath(), change);
            }

            int matchedCount = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 3) {
                    try {
                        int additions = parts[0].equals("-") ? 0 : Integer.parseInt(parts[0]);
                        int deletions = parts[1].equals("-") ? 0 : Integer.parseInt(parts[1]);
                        String filePath = parts[2];

                        FileChange change = pathMap.get(filePath);
                        if (change != null) {
                            change.setAdditions(additions);
                            change.setDeletions(deletions);
                            matchedCount++;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            reader.close();
            process.waitFor();

            if (matchedCount == 0 && !changes.isEmpty()) {
            }

            for (FileChange change : changes) {
                if (change.getAdditions() == null) {
                    change.setAdditions(0);
                }
                if (change.getDeletions() == null) {
                    change.setDeletions(0);
                }
            }

        } catch (Exception e) {
            log.warn("原生 git 获取文件统计失败: {}", e.getMessage());
            for (FileChange change : changes) {
                if (change.getAdditions() == null)
                    change.setAdditions(0);
                if (change.getDeletions() == null)
                    change.setDeletions(0);
            }
        }
    }

    @Override
    public String getFileContent(String localPath, String commitHash, String filePath) {
        String cacheKey = localPath + ":" + commitHash + ":" + filePath;

        String cached = fileContentCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        try (Git git = Git.open(new File(localPath))) {
            org.eclipse.jgit.lib.Repository repository = git.getRepository();

            ObjectId commitId = repository.resolve(commitHash);
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                RevTree tree = commit.getTree();

                try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, tree)) {
                    if (treeWalk != null) {
                        ObjectId blobId = treeWalk.getObjectId(0);
                        byte[] bytes = repository.open(blobId).getBytes();
                        String content = new String(bytes, StandardCharsets.UTF_8);
                        putToCache(cacheKey, content);
                        return content;
                    }
                }
            }
        } catch (org.eclipse.jgit.errors.MissingObjectException e) {
            String content = getFileContentWithNativeGit(localPath, commitHash, filePath);
            if (content != null) {
                putToCache(cacheKey, content);
            }
            return content;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Missing")) {
                String content = getFileContentWithNativeGit(localPath, commitHash, filePath);
                if (content != null) {
                    putToCache(cacheKey, content);
                }
                return content;
            }
            log.error("获取文件内容失败: {}", e.getMessage());
        }
        return null;
    }

    private void putToCache(String key, String value) {
        fileContentCache.put(key, value);
    }

    private String getFileContentWithNativeGit(String localPath, String commitHash, String filePath) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            List<String> cmd = new ArrayList<>();
            if (isWindows) {
                cmd.add("cmd.exe");
                cmd.add("/c");
            }
            cmd.add("git");
            if (isWindows) {
                cmd.add("-c");
                cmd.add("core.longpaths=true");
            }
            cmd.add("show");
            cmd.add(commitHash + ":" + filePath);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(localPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = process.getInputStream().read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }

            int exitCode = process.waitFor();

            String output = baos.toString(StandardCharsets.UTF_8);
            if (exitCode == 0) {
                return output;
            }

            if (!fileExistsInCommit(localPath, commitHash, filePath)) {
                return null;
            }

            log.warn("原生git获取文件失败(可能缺blob): {} @ {}{}", filePath, commitHash,
                    output.isBlank() ? "" : " | " + output.trim());
        } catch (Exception e) {
            log.error("原生git获取文件异常: {}", e.getMessage());
        }
        return null;
    }

    private boolean fileExistsInCommit(String localPath, String commitHash, String filePath) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            List<String> cmd = new ArrayList<>();
            if (isWindows) {
                cmd.add("cmd.exe");
                cmd.add("/c");
            }
            cmd.add("git");
            if (isWindows) {
                cmd.add("-c");
                cmd.add("core.longpaths=true");
            }
            cmd.add("ls-tree");
            cmd.add("-r");
            cmd.add("--name-only");
            cmd.add(commitHash);
            cmd.add("--");
            cmd.add(filePath);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(localPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = process.getInputStream().read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return false;
            }

            String output = baos.toString(StandardCharsets.UTF_8);
            for (String line : output.split("\\R")) {
                if (line.trim().equals(filePath)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDiff(String localPath, String fromCommit, String toCommit, String filePath) {
        try (Git git = Git.open(new File(localPath))) {
            org.eclipse.jgit.lib.Repository repository = git.getRepository();

            ObjectId oldId = repository.resolve(fromCommit);
            ObjectId newId = repository.resolve(toCommit);

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit oldCommit = revWalk.parseCommit(oldId);
                RevCommit newCommit = revWalk.parseCommit(newId);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (DiffFormatter diffFormatter = new DiffFormatter(out)) {
                    diffFormatter.setRepository(repository);

                    AbstractTreeIterator oldTree = prepareTreeParser(repository, oldCommit);
                    AbstractTreeIterator newTree = prepareTreeParser(repository, newCommit);

                    List<DiffEntry> diffs = diffFormatter.scan(oldTree, newTree);
                    for (DiffEntry diff : diffs) {
                        String path = diff.getNewPath().equals("/dev/null")
                                ? diff.getOldPath()
                                : diff.getNewPath();
                        if (path.equals(filePath)) {
                            diffFormatter.format(diff);
                            break;
                        }
                    }
                }
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("获取diff失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void deleteLocalRepository(String localPath) {
        File dir = new File(localPath);
        if (dir.exists()) {
            deleteDirectory(dir);
            log.info("已删除本地仓库: {}", localPath);
        }
    }

    private CommitRecord convertToCommitRecord(Git git, RevCommit revCommit, int order) {
        CommitRecord record = new CommitRecord();
        record.setCommitHash(revCommit.getName());
        record.setShortHash(revCommit.getName().substring(0, 7));
        record.setAuthorName(revCommit.getAuthorIdent().getName());
        record.setAuthorEmail(revCommit.getAuthorIdent().getEmailAddress());
        record.setCommitMessage(revCommit.getShortMessage());
        record.setCommitTime(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(revCommit.getCommitTime()),
                ZoneId.systemDefault()));
        record.setCommitOrder(order);
        record.setIsMerge(revCommit.getParentCount() > 1 ? 1 : 0);

        if (revCommit.getParentCount() > 0) {
            record.setParentHash(revCommit.getParent(0).getName());
        }

        record.setAdditions(null);
        record.setDeletions(null);
        record.setFilesChanged(null);

        return record;
    }

    private List<FileChange> parseFileChangesForCommit(Git git, RevCommit commit) throws IOException {
        List<FileChange> changes = new ArrayList<>();
        org.eclipse.jgit.lib.Repository repository = git.getRepository();

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit parent = null;
            if (commit.getParentCount() > 0) {
                parent = revWalk.parseCommit(commit.getParent(0).getId());
            }

            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(repository);

                AbstractTreeIterator oldTree = parent != null
                        ? prepareTreeParser(repository, parent)
                        : new CanonicalTreeParser();
                AbstractTreeIterator newTree = prepareTreeParser(repository, commit);

                List<DiffEntry> diffs = diffFormatter.scan(oldTree, newTree);
                for (DiffEntry diff : diffs) {
                    FileChange change = convertToFileChange(diff, diffFormatter);
                    changes.add(change);
                }
            }
        }

        return changes;
    }

    private FileChange convertToFileChange(DiffEntry diff, DiffFormatter formatter) throws IOException {
        FileChange change = new FileChange();

        String path = diff.getNewPath().equals("/dev/null")
                ? diff.getOldPath()
                : diff.getNewPath();
        change.setFilePath(path);
        change.setFileName(path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path);

        if (path.contains(".")) {
            String ext = path.substring(path.lastIndexOf(".") + 1);
            if (ext.length() > 10) {
                ext = ext.substring(0, 10);
            }
            change.setFileExtension(ext);
        }

        switch (diff.getChangeType()) {
            case ADD -> change.setChangeType("ADD");
            case MODIFY -> change.setChangeType("MODIFY");
            case DELETE -> change.setChangeType("DELETE");
            case RENAME -> {
                change.setChangeType("RENAME");
                change.setOldPath(diff.getOldPath());
            }
            case COPY -> change.setChangeType("COPY");
        }

        change.setAdditions(null);
        change.setDeletions(null);

        return change;
    }

    private AbstractTreeIterator prepareTreeParser(org.eclipse.jgit.lib.Repository repository,
            RevCommit commit) throws IOException {
        RevTree tree = commit.getTree();
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (ObjectReader reader = repository.newObjectReader()) {
            treeParser.reset(reader, tree.getId());
        }
        return treeParser;
    }

    private int countFiles(Git git, RevCommit commit) throws IOException {
        int count = 0;
        try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                count++;
            }
        }
        return count;
    }

    private long calculateDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else {
                    size += calculateDirectorySize(file);
                }
            }
        }
        return size;
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    @Override
    public CommitStatsDTO calculateCommitStats(String localPath, String commitHash) {

        try (Git git = Git.open(new File(localPath))) {
            org.eclipse.jgit.lib.Repository repository = git.getRepository();
            ObjectId commitId = repository.resolve(commitHash);

            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                List<FileChange> changes = parseFileChangesForCommit(git, commit);

                int additions = 0, deletions = 0;
                for (FileChange change : changes) {
                    additions += change.getAdditions() != null ? change.getAdditions() : 0;
                    deletions += change.getDeletions() != null ? change.getDeletions() : 0;
                }

                return CommitStatsDTO.of(additions, deletions, changes.size());
            }
        } catch (org.eclipse.jgit.errors.MissingObjectException e) {
            return calculateCommitStatsWithNativeGit(localPath, commitHash);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Missing")) {
                return calculateCommitStatsWithNativeGit(localPath, commitHash);
            }
            log.error("计算提交统计失败: {}", e.getMessage());
            return CommitStatsDTO.empty();
        }
    }

    private CommitStatsDTO calculateCommitStatsWithNativeGit(String localPath, String commitHash) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            List<String> cmd = new ArrayList<>();
            if (isWindows) {
                cmd.add("cmd.exe");
                cmd.add("/c");
            }
            cmd.add("git");
            cmd.add("diff");
            cmd.add("--stat");
            cmd.add("--numstat");
            cmd.add(commitHash + "^.." + commitHash);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(localPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String line;

            int additions = 0, deletions = 0, filesChanged = 0;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 3) {
                    try {
                        if (!parts[0].equals("-")) {
                            additions += Integer.parseInt(parts[0]);
                        }
                        if (!parts[1].equals("-")) {
                            deletions += Integer.parseInt(parts[1]);
                        }
                        filesChanged++;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            reader.close();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return CommitStatsDTO.of(additions, deletions, filesChanged);
            }
        } catch (Exception e) {
            log.error("原生 git 计算统计失败: {}", e.getMessage());
        }
        return CommitStatsDTO.empty();
    }

    @Override
    public Map<String, String> prefetchFileContents(String localPath, List<PrefetchRequest> requests) {
        log.info("批量预取文件内容: {} 个请求", requests.size());
        Map<String, String> results = new HashMap<>();

        if (requests.isEmpty()) {
            return results;
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        try {
            for (PrefetchRequest req : requests) {
                String key = req.getCommitHash() + ":" + req.getFilePath();

                List<String> cmd = new ArrayList<>();
                if (isWindows) {
                    cmd.add("cmd.exe");
                    cmd.add("/c");
                }
                cmd.add("git");
                cmd.add("show");
                cmd.add(req.getCommitHash() + ":" + req.getFilePath());

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(new File(localPath));
                pb.redirectErrorStream(true);

                Process process = pb.start();

                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = process.getInputStream().read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    results.put(key, baos.toString(StandardCharsets.UTF_8));
                }
            }

            log.info("批量预取完成: {} / {} 成功", results.size(), requests.size());
        } catch (Exception e) {
            log.error("批量预取文件失败: {}", e.getMessage());
        }

        return results;
    }

    @Override
    public Map<String, List<FileChange>> parseFileChangesBatch(String localPath, List<String> commitHashes) {
        Map<String, List<FileChange>> results = new HashMap<>();

        if (commitHashes == null || commitHashes.isEmpty()) {
            return results;
        }

        log.info("解析文件变更: {} 个 commit", commitHashes.size());
        long startTime = System.currentTimeMillis();

        int processed = 0;
        for (String hash : commitHashes) {
            List<FileChange> changes = parseFileChanges(localPath, hash);
            results.put(hash, changes);
            processed++;

            if (processed % 50 == 0) {
                log.info("文件变更解析进度: {}/{}", processed, commitHashes.size());
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        int totalChanges = results.values().stream().mapToInt(List::size).sum();
        log.info("文件变更解析完成: {} 个 commit, {} 个文件变更, 耗时 {}ms (平均 {}ms/commit)",
                commitHashes.size(), totalChanges, elapsed,
                commitHashes.isEmpty() ? 0 : elapsed / commitHashes.size());

        return results;
    }
}
