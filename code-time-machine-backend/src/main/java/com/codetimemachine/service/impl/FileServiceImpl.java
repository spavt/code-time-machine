package com.codetimemachine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codetimemachine.dto.FileTimelineDTO;
import com.codetimemachine.entity.AiAnalysis;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.entity.FileChange;
import com.codetimemachine.entity.Repository;
import com.codetimemachine.mapper.AiAnalysisMapper;
import com.codetimemachine.mapper.CommitRecordMapper;
import com.codetimemachine.mapper.FileChangeMapper;
import com.codetimemachine.mapper.RepositoryMapper;
import com.codetimemachine.service.FileService;
import com.codetimemachine.service.GitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private final RepositoryMapper repositoryMapper;
    private final CommitRecordMapper commitRecordMapper;
    private final FileChangeMapper fileChangeMapper;
    private final AiAnalysisMapper aiAnalysisMapper;
    private final GitService gitService;
    private final Executor taskExecutor;

    public FileServiceImpl(
            RepositoryMapper repositoryMapper,
            CommitRecordMapper commitRecordMapper,
            FileChangeMapper fileChangeMapper,
            AiAnalysisMapper aiAnalysisMapper,
            GitService gitService,
            @Qualifier("taskExecutor") Executor taskExecutor) {
        this.repositoryMapper = repositoryMapper;
        this.commitRecordMapper = commitRecordMapper;
        this.fileChangeMapper = fileChangeMapper;
        this.aiAnalysisMapper = aiAnalysisMapper;
        this.gitService = gitService;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public List<Map<String, Object>> getFileTree(Long repoId) {
        List<String> filePaths = fileChangeMapper.getDistinctFilePaths(repoId);

        Map<String, Long> modifyCountMap = new HashMap<>();
        List<Map<String, Object>> modifyCounts = fileChangeMapper.getFileModificationCounts(repoId);
        for (Map<String, Object> item : modifyCounts) {
            String path = (String) item.get("filePath");
            Long count = ((Number) item.get("modifyCount")).longValue();
            modifyCountMap.put(path, count);
        }

        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();

        for (String path : filePaths) {
            String[] parts = path.split("/");
            StringBuilder currentPath = new StringBuilder();
            Map<String, Object> parentNode = null;

            for (int i = 0; i < parts.length; i++) {
                if (currentPath.length() > 0) {
                    currentPath.append("/");
                }
                currentPath.append(parts[i]);
                String fullPath = currentPath.toString();

                if (!nodeMap.containsKey(fullPath)) {
                    Map<String, Object> node = new HashMap<>();
                    node.put("path", parts[i]);
                    node.put("fullPath", fullPath);
                    node.put("type", i == parts.length - 1 ? "file" : "folder");
                    node.put("children", new ArrayList<Map<String, Object>>());

                    if (i == parts.length - 1) {
                        node.put("modifyCount", modifyCountMap.getOrDefault(path, 0L));
                    }

                    nodeMap.put(fullPath, node);

                    if (parentNode == null) {
                        roots.add(node);
                    } else {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> children = (List<Map<String, Object>>) parentNode.get("children");
                        children.add(node);
                    }
                }

                parentNode = nodeMap.get(fullPath);
            }
        }

        return roots;
    }

    @Override
    public FileTimelineDTO getTimeline(Long repoId, String filePath, boolean includeContent) {
        Repository repo = repositoryMapper.selectById(repoId);
        if (repo == null) {
            return null;
        }

        FileTimelineDTO dto = new FileTimelineDTO();
        dto.setRepoId(repoId);
        dto.setFilePath(filePath);
        dto.setFileName(filePath.contains("/")
                ? filePath.substring(filePath.lastIndexOf("/") + 1)
                : filePath);

        LambdaQueryWrapper<FileChange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileChange::getRepoId, repoId)
                .eq(FileChange::getFilePath, filePath);
        List<FileChange> changes = fileChangeMapper.selectList(wrapper);

        List<Long> commitIds = changes.stream()
                .map(FileChange::getCommitId)
                .distinct()
                .collect(Collectors.toList());

        if (commitIds.isEmpty()) {
            dto.setCommits(new ArrayList<>());
            return dto;
        }

        Map<Long, CommitRecord> commitMap = new HashMap<>();
        LambdaQueryWrapper<CommitRecord> crWrapper = new LambdaQueryWrapper<>();
        crWrapper.in(CommitRecord::getId, commitIds);
        List<CommitRecord> commits = commitRecordMapper.selectList(crWrapper);
        for (CommitRecord c : commits) {
            commitMap.put(c.getId(), c);
        }

        Map<Long, AiAnalysis> analysisMap = new HashMap<>();
        LambdaQueryWrapper<AiAnalysis> aaWrapper = new LambdaQueryWrapper<>();
        aaWrapper.in(AiAnalysis::getCommitId, commitIds);
        List<AiAnalysis> analyses = aiAnalysisMapper.selectList(aaWrapper);
        for (AiAnalysis a : analyses) {
            analysisMap.put(a.getCommitId(), a);
        }

        List<FileTimelineDTO.TimelineCommitDTO> timelineCommits = new ArrayList<>();

        changes.sort((a, b) -> {
            CommitRecord ca = commitMap.get(a.getCommitId());
            CommitRecord cb = commitMap.get(b.getCommitId());
            if (ca == null || cb == null) {
                return 0;
            }
            return Integer.compare(ca.getCommitOrder(), cb.getCommitOrder());
        });

        for (FileChange change : changes) {
            CommitRecord commit = commitMap.get(change.getCommitId());
            if (commit == null) {
                continue;
            }

            FileTimelineDTO.TimelineCommitDTO tc = new FileTimelineDTO.TimelineCommitDTO();
            tc.setId(commit.getId());
            tc.setCommitHash(commit.getCommitHash());
            tc.setShortHash(commit.getShortHash());
            tc.setCommitMessage(commit.getCommitMessage());
            tc.setAuthorName(commit.getAuthorName());
            tc.setCommitTime(commit.getCommitTime().toString());
            tc.setCommitOrder(commit.getCommitOrder());
            tc.setChangeType(change.getChangeType());
            tc.setAdditions(change.getAdditions());
            tc.setDeletions(change.getDeletions());

            if (includeContent && repo.getLocalPath() != null && !"DELETE".equals(change.getChangeType())) {
                String content = gitService.getFileContent(
                        repo.getLocalPath(),
                        commit.getCommitHash(),
                        filePath);
                tc.setContent(content);
            }

            AiAnalysis analysis = analysisMap.get(commit.getId());
            if (analysis != null) {
                tc.setAiSummary(analysis.getSummary());
                tc.setChangeCategory(analysis.getChangeCategory());
            }

            timelineCommits.add(tc);
        }

        dto.setCommits(timelineCommits);
        return dto;
    }

    @Override
    public String getFileContent(Long repoId, Long commitId, String filePath) {
        Repository repo = repositoryMapper.selectById(repoId);
        CommitRecord commit = commitRecordMapper.selectById(commitId);

        if (repo == null || commit == null || repo.getLocalPath() == null) {
            return null;
        }

        String content = gitService.getFileContent(repo.getLocalPath(), commit.getCommitHash(), filePath);
        if (content != null) {
            return content;
        }

        FileChange renameChange = fileChangeMapper.findRenameForCommit(commitId, filePath);
        if (renameChange == null) {
            return null;
        }

        String altPath = filePath.equals(renameChange.getFilePath())
                ? renameChange.getOldPath()
                : renameChange.getFilePath();
        if (altPath == null || altPath.isBlank() || altPath.equals(filePath)) {
            return null;
        }

        return gitService.getFileContent(repo.getLocalPath(), commit.getCommitHash(), altPath);
    }

    @Override
    public List<Map<String, Object>> searchFiles(Long repoId, String keyword) {
        List<Map<String, Object>> rows = fileChangeMapper.searchFilePathsWithCommitCounts(repoId, keyword);
        return rows.stream()
                .map(row -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("filePath", row.get("filePath"));
                    result.put("commits", ((Number) row.getOrDefault("commitCount", 0)).longValue());
                    return result;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getEvolutionStory(Long repoId, String filePath) {
        Repository repo = repositoryMapper.selectById(repoId);
        if (repo == null) {
            return Map.of("story", "仓库不存在", "keyMilestones", List.of());
        }

        LambdaQueryWrapper<FileChange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileChange::getRepoId, repoId)
                .eq(FileChange::getFilePath, filePath);
        List<FileChange> changes = fileChangeMapper.selectList(wrapper);

        if (changes.isEmpty()) {
            return Map.of("story", "该文件没有变更历史", "keyMilestones", List.of());
        }

        List<Long> commitIds = changes.stream()
                .map(FileChange::getCommitId)
                .distinct()
                .collect(Collectors.toList());

        LambdaQueryWrapper<CommitRecord> crWrapper = new LambdaQueryWrapper<>();
        crWrapper.in(CommitRecord::getId, commitIds)
                .orderByAsc(CommitRecord::getCommitOrder);
        List<CommitRecord> commits = commitRecordMapper.selectList(crWrapper);

        Map<Long, AiAnalysis> analysisMap = new HashMap<>();
        LambdaQueryWrapper<AiAnalysis> aaWrapper = new LambdaQueryWrapper<>();
        aaWrapper.in(AiAnalysis::getCommitId, commitIds);
        List<AiAnalysis> analyses = aiAnalysisMapper.selectList(aaWrapper);
        for (AiAnalysis a : analyses) {
            analysisMap.put(a.getCommitId(), a);
        }

        StringBuilder storyBuilder = new StringBuilder();
        storyBuilder.append("## ").append(filePath).append(" ")
                .append("的演进故事\n\n");
        storyBuilder.append("这个文件经历了 **")
                .append(commits.size())
                .append("** ")
                .append("次提交变更。\n\n");

        if (!commits.isEmpty()) {
            CommitRecord firstCommit = commits.get(0);
            CommitRecord lastCommit = commits.get(commits.size() - 1);
            storyBuilder.append("**创建时间**: ")
                    .append(firstCommit.getCommitTime())
                    .append("\n\n");
            storyBuilder.append("**最后更新**: ")
                    .append(lastCommit.getCommitTime())
                    .append("\n\n");
        }

        Map<String, Long> changeTypeCounts = changes.stream()
                .collect(Collectors.groupingBy(FileChange::getChangeType, Collectors.counting()));

        storyBuilder.append("### 变更统计\n\n");
        changeTypeCounts.forEach((type, count) -> {
            String typeLabel = switch (type) {
                case "ADD" -> "新增";
                case "MODIFY" -> "修改";
                case "DELETE" -> "删除";
                case "RENAME" -> "重命名";
                default -> type;
            };
            storyBuilder.append("- ").append(typeLabel).append(": ").append(count)
                    .append(" 次\n");
        });

        int totalAdditions = changes.stream()
                .mapToInt(c -> c.getAdditions() != null ? c.getAdditions() : 0)
                .sum();
        int totalDeletions = changes.stream()
                .mapToInt(c -> c.getDeletions() != null ? c.getDeletions() : 0)
                .sum();
        storyBuilder.append("\n**总计**: +")
                .append(totalAdditions)
                .append(" / -")
                .append(totalDeletions)
                .append(" 行\n");

        List<Map<String, String>> keyMilestones = new ArrayList<>();
        for (CommitRecord commit : commits) {
            AiAnalysis analysis = analysisMap.get(commit.getId());
            FileChange change = changes.stream()
                    .filter(c -> c.getCommitId().equals(commit.getId()))
                    .findFirst()
                    .orElse(null);

            boolean isKeyMilestone = false;
            String summary = commit.getCommitMessage();

            if (analysis != null && analysis.getSummary() != null) {
                isKeyMilestone = true;
                summary = analysis.getSummary();
            } else if (change != null) {
                int adds = change.getAdditions() != null ? change.getAdditions() : 0;
                int dels = change.getDeletions() != null ? change.getDeletions() : 0;
                if (adds + dels > 50) {
                    isKeyMilestone = true;
                }
            }

            if (isKeyMilestone && keyMilestones.size() < 10) {
                keyMilestones.add(Map.of(
                        "commitHash", commit.getShortHash(),
                        "summary", summary));
            }
        }

        return Map.of(
                "story", storyBuilder.toString(),
                "keyMilestones", keyMilestones);
    }

    @Override
    public String getDiff(Long repoId, String fromCommit, String toCommit, String filePath) {
        Repository repo = repositoryMapper.selectById(repoId);
        if (repo == null || repo.getLocalPath() == null) {
            return null;
        }
        return gitService.getDiff(repo.getLocalPath(), fromCommit, toCommit, filePath);
    }

    @Override
    public List<Map<String, Object>> getMethodTimeline(Long repoId, String filePath, String methodName) {
        List<Map<String, Object>> timeline = new ArrayList<>();

        Repository repo = repositoryMapper.selectById(repoId);
        if (repo == null || repo.getLocalPath() == null) {
            return timeline;
        }

        LambdaQueryWrapper<FileChange> fcWrapper = new LambdaQueryWrapper<>();
        fcWrapper.eq(FileChange::getRepoId, repoId)
                .eq(FileChange::getFilePath, filePath);
        List<FileChange> fileChanges = fileChangeMapper.selectList(fcWrapper);

        Set<Long> commitIds = fileChanges.stream()
                .map(FileChange::getCommitId)
                .collect(Collectors.toSet());

        if (commitIds.isEmpty()) {
            return timeline;
        }

        LambdaQueryWrapper<CommitRecord> crWrapper = new LambdaQueryWrapper<>();
        crWrapper.eq(CommitRecord::getRepoId, repoId)
                .in(CommitRecord::getId, commitIds)
                .orderByAsc(CommitRecord::getCommitOrder);
        List<CommitRecord> commits = commitRecordMapper.selectList(crWrapper);

        String language = detectLanguage(filePath);
        String previousContent = null;

        for (CommitRecord commit : commits) {
            String fileContent = gitService.getFileContent(repo.getLocalPath(), commit.getCommitHash(), filePath);
            if (fileContent == null) {
                continue;
            }

            boolean methodExists = fileContent.contains(methodName + "(") ||
                    fileContent.contains(methodName + " (");

            if (methodExists) {
                String methodContent = extractMethodContent(fileContent, methodName, language);

                String contentToUse = (methodContent != null) ? methodContent : fileContent;

                if (previousContent == null || !contentToUse.equals(previousContent)) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("commitId", commit.getId());
                    entry.put("commitHash", commit.getCommitHash());
                    entry.put("shortHash", commit.getShortHash());
                    entry.put("authorName", commit.getAuthorName());
                    entry.put("commitMessage", commit.getCommitMessage());
                    entry.put("commitTime", commit.getCommitTime().toString());
                    entry.put("additions", commit.getAdditions());
                    entry.put("deletions", commit.getDeletions());
                    entry.put("content", contentToUse);
                    entry.put("extracted", methodContent != null);

                    timeline.add(entry);
                }
                previousContent = contentToUse;
            }
        }

        return timeline;
    }

    private String extractMethodContent(String fileContent, String methodName, String language) {
        String[] lines = fileContent.split("\n");
        int startLine = -1;
        int braceCount = 0;
        boolean foundStart = false;
        int endLine = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null)
                continue;

            if (!foundStart && (line.contains(methodName + "(") ||
                    line.contains(methodName + " (") ||
                    line.matches(".*\\b" + methodName + "\\s*\\(.*"))) {
                String trimmed = line.trim();
                boolean isDefinition = false;

                switch (language) {
                    case "java":
                        isDefinition = trimmed
                                .matches("^(public|private|protected|static|final|synchronized|abstract|native|\\s)+.*"
                                        + methodName + "\\s*\\(.*");
                        break;
                    case "typescript":
                    case "javascript":
                    case "jsx":
                        isDefinition = trimmed.startsWith("function ") ||
                                trimmed.startsWith("async function ") ||
                                trimmed.startsWith("async ") ||
                                trimmed.matches("^(public|private|protected|static|readonly|async|\\s)*" + methodName
                                        + "\\s*[(<].*")
                                ||
                                trimmed.matches("^(const|let|var)\\s+" + methodName + "\\s*=.*") ||
                                trimmed.matches("^" + methodName + "\\s*\\(.*\\)\\s*\\{?$") ||
                                trimmed.matches("^" + methodName + "\\s*=\\s*\\(.*");
                        break;
                    case "python":
                        isDefinition = trimmed.startsWith("def ") || trimmed.startsWith("async def ");
                        break;
                    case "go":
                        isDefinition = trimmed.startsWith("func ");
                        break;
                    default:
                        isDefinition = trimmed.matches("^(function|def|fn|func|public|private|protected)\\s+.*");
                }

                if (isDefinition) {
                    startLine = i;
                    foundStart = true;
                }
            }

            if (foundStart) {
                if ("python".equals(language)) {
                    if (i > startLine) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            int currentIndent = getIndent(line);
                            int defIndent = getIndent(lines[startLine]);
                            if (currentIndent <= defIndent) {
                                endLine = i - 1;
                                break;
                            }
                        }
                    }
                } else {
                    for (char c : line.toCharArray()) {
                        if (c == '{')
                            braceCount++;
                        if (c == '}')
                            braceCount--;
                    }

                    if (braceCount == 0 && line.contains("}")) {
                        endLine = i;
                        break;
                    }
                }
            }
        }

        if ("python".equals(language) && foundStart && endLine < 0) {
            endLine = lines.length - 1;
        }

        if (startLine >= 0 && endLine >= startLine) {
            StringBuilder sb = new StringBuilder();
            for (int i = startLine; i <= endLine && i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
            return sb.toString();
        }

        return null;
    }

    private int getIndent(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ')
                indent++;
            else if (c == '\t')
                indent += 4;
            else
                break;
        }
        return indent;
    }

    @Override
    public Map<Long, Map<String, Object>> getBatchFileContent(Long repoId, List<Long> commitIds, String filePath) {
        if (commitIds == null || commitIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Repository repo = repositoryMapper.selectById(repoId);
        if (repo == null || repo.getLocalPath() == null) {
            return Collections.emptyMap();
        }

        LambdaQueryWrapper<CommitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CommitRecord::getId, commitIds);
        List<CommitRecord> commits = commitRecordMapper.selectList(wrapper);

        Map<Long, CommitRecord> commitMap = commits.stream()
                .collect(Collectors.toMap(CommitRecord::getId, c -> c));

        String localPath = repo.getLocalPath();
        String language = detectLanguage(filePath);

        List<CompletableFuture<Map.Entry<Long, Map<String, Object>>>> futures = commitIds.stream()
                .filter(commitMap::containsKey)
                .map(commitId -> CompletableFuture.supplyAsync(() -> {
                    CommitRecord commit = commitMap.get(commitId);
                    String content = gitService.getFileContent(localPath, commit.getCommitHash(), filePath);

                    Map<String, Object> result = new HashMap<>();
                    result.put("content", content != null ? content : "");
                    result.put("language", language);
                    result.put("lineCount", content != null ? content.split("\n").length : 0);

                    return Map.entry(commitId, result);
                }, taskExecutor))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private String detectLanguage(String filePath) {
        if (filePath == null)
            return "plaintext";
        String ext = filePath.contains(".")
                ? filePath.substring(filePath.lastIndexOf(".") + 1).toLowerCase()
                : "";

        return switch (ext) {
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "jsx", "tsx" -> "jsx";
            case "vue" -> "vue";
            case "py" -> "python";
            case "java" -> "java";
            case "go" -> "go";
            case "rs" -> "rust";
            case "c", "h" -> "c";
            case "cpp", "cc", "hpp" -> "cpp";
            case "cs" -> "csharp";
            case "rb" -> "ruby";
            case "php" -> "php";
            case "sql" -> "sql";
            case "sh", "bash" -> "bash";
            case "yaml", "yml" -> "yaml";
            case "json" -> "json";
            case "xml" -> "xml";
            case "html" -> "html";
            case "css" -> "css";
            case "scss", "less" -> "scss";
            case "md" -> "markdown";
            default -> "plaintext";
        };
    }
}
