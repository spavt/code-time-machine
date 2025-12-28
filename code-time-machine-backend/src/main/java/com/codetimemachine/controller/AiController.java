package com.codetimemachine.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codetimemachine.common.Result;
import com.codetimemachine.entity.ChatHistory;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.entity.Repository;
import com.codetimemachine.mapper.ChatHistoryMapper;
import com.codetimemachine.mapper.CommitRecordMapper;
import com.codetimemachine.mapper.FileChangeMapper;
import com.codetimemachine.mapper.RepositoryMapper;
import com.codetimemachine.service.AiService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * AI对话接口
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final ChatHistoryMapper chatHistoryMapper;
    private final CommitRecordMapper commitRecordMapper;
    private final RepositoryMapper repositoryMapper;
    private final FileChangeMapper fileChangeMapper;

    /**
     * 问答请求
     */
    @Data
    public static class AskRequest {
        private String sessionId;
        private Long repoId;
        private Long commitId;
        private String filePath;
        private String question;
        private String context;
    }

    /**
     * 发送问题
     */
    @PostMapping("/ask")
    public Result<Map<String, Object>> ask(@RequestBody AskRequest request) {
        // 保存用户问题
        ChatHistory userMsg = new ChatHistory();
        userMsg.setSessionId(request.getSessionId());
        userMsg.setRepoId(request.getRepoId());
        userMsg.setCommitId(request.getCommitId());
        userMsg.setFilePath(request.getFilePath());
        userMsg.setRole("user");
        userMsg.setContent(request.getQuestion());
        userMsg.setCreatedAt(LocalDateTime.now());
        chatHistoryMapper.insert(userMsg);

        // 调用AI
        String answer = aiService.askQuestion(request.getQuestion(), request.getContext());

        // 保存AI回答
        ChatHistory aiMsg = new ChatHistory();
        aiMsg.setSessionId(request.getSessionId());
        aiMsg.setRepoId(request.getRepoId());
        aiMsg.setCommitId(request.getCommitId());
        aiMsg.setFilePath(request.getFilePath());
        aiMsg.setRole("assistant");
        aiMsg.setContent(answer);
        aiMsg.setCreatedAt(LocalDateTime.now());
        chatHistoryMapper.insert(aiMsg);

        // 估算 Token 使用量（问题 + 上下文 + 回答）
        int tokensUsed = estimateTokens(request.getQuestion())
                + estimateTokens(request.getContext())
                + estimateTokens(answer);

        return Result.success(Map.of(
                "answer", answer,
                "tokensUsed", tokensUsed));
    }

    /**
     * 发送问题（流式输出）
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@RequestBody AskRequest request) {
        // 保存用户问题
        ChatHistory userMsg = new ChatHistory();
        userMsg.setSessionId(request.getSessionId());
        userMsg.setRepoId(request.getRepoId());
        userMsg.setCommitId(request.getCommitId());
        userMsg.setFilePath(request.getFilePath());
        userMsg.setRole("user");
        userMsg.setContent(request.getQuestion());
        userMsg.setCreatedAt(LocalDateTime.now());
        chatHistoryMapper.insert(userMsg);

        // 用于收集完整回答
        StringBuilder fullAnswer = new StringBuilder();

        // 调用AI流式输出
        return aiService.askQuestionStream(request.getQuestion(), request.getContext())
                .doOnNext(chunk -> fullAnswer.append(chunk))
                .doOnComplete(() -> {
                    // 流结束后保存完整的AI回答
                    ChatHistory aiMsg = new ChatHistory();
                    aiMsg.setSessionId(request.getSessionId());
                    aiMsg.setRepoId(request.getRepoId());
                    aiMsg.setCommitId(request.getCommitId());
                    aiMsg.setFilePath(request.getFilePath());
                    aiMsg.setRole("assistant");
                    aiMsg.setContent(fullAnswer.toString());
                    aiMsg.setCreatedAt(LocalDateTime.now());
                    chatHistoryMapper.insert(aiMsg);
                });
    }

    /**
     * 估算文本的 Token 数量
     * 中文约 1.5 字符/token，英文约 4 字符/token
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty())
            return 0;

        int chineseCount = 0;
        int otherCount = 0;

        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }

        // 中文：约 1.5 字符/token，其他：约 4 字符/token
        return (int) Math.ceil(chineseCount / 1.5) + (int) Math.ceil(otherCount / 4.0);
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/history/{sessionId}")
    public Result<List<ChatHistory>> getHistory(@PathVariable String sessionId) {
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatHistory::getSessionId, sessionId)
                .orderByAsc(ChatHistory::getCreatedAt);
        return Result.success(chatHistoryMapper.selectList(wrapper));
    }

    /**
     * 清除对话历史
     */
    @DeleteMapping("/history/{sessionId}")
    public Result<Void> clearHistory(@PathVariable String sessionId) {
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatHistory::getSessionId, sessionId);
        chatHistoryMapper.delete(wrapper);
        return Result.success();
    }

    /**
     * 获取推荐问题 - 根据提交内容智能生成
     */
    @GetMapping("/suggestions/{commitId}")
    public Result<List<String>> getSuggestions(@PathVariable Long commitId) {
        List<String> suggestions = new ArrayList<>();

        // 获取 commit 信息
        CommitRecord commit = commitRecordMapper.selectById(commitId);
        String commitMessage = commit != null ? commit.getCommitMessage() : "";
        String lowerMessage = commitMessage.toLowerCase();

        // 根据 commit message 判断提交类型并生成对应问题
        CommitType type = detectCommitType(lowerMessage);

        switch (type) {
            case BUG_FIX:
                suggestions.add("这个 bug 是怎么产生的？");
                suggestions.add("这个修复会影响其他功能吗？");
                suggestions.add("如何避免类似 bug 再次出现？");
                suggestions.add("有没有添加相应的测试用例？");
                break;
            case NEW_FEATURE:
                suggestions.add("这个新功能的设计思路是什么？");
                suggestions.add("这个功能解决了什么问题？");
                suggestions.add("新功能的使用方式是怎样的？");
                suggestions.add("这个功能的边界情况考虑得全面吗？");
                break;
            case REFACTOR:
                suggestions.add("为什么要进行这次重构？");
                suggestions.add("重构前后有什么区别？");
                suggestions.add("这次重构提升了什么？");
                suggestions.add("重构后的代码更易于维护吗？");
                break;
            case DOCS:
                suggestions.add("这些文档的主要内容是什么？");
                suggestions.add("这些文档面向什么样的读者？");
                suggestions.add("文档是否完整覆盖了功能？");
                break;
            case TEST:
                suggestions.add("这些测试覆盖了哪些场景？");
                suggestions.add("测试用例的设计思路是什么？");
                suggestions.add("测试覆盖率如何？");
                break;
            case PERFORMANCE:
                suggestions.add("性能优化的效果如何？");
                suggestions.add("优化的原理是什么？");
                suggestions.add("优化前后的性能对比数据是？");
                break;
            case STYLE:
                suggestions.add("代码风格有哪些变化？");
                suggestions.add("这些风格改动的依据是什么？");
                break;
            default:
                suggestions.add("这次改动的主要目的是什么？");
                suggestions.add("这段代码的设计思路是什么？");
                suggestions.add("这个改动会影响哪些模块？");
                suggestions.add("有没有潜在的性能问题？");
        }

        // 添加通用问题
        suggestions.add("这个改动有什么需要注意的地方吗？");

        return Result.success(suggestions);
    }

    /**
     * 提交类型枚举
     */
    private enum CommitType {
        BUG_FIX, NEW_FEATURE, REFACTOR, DOCS, TEST, PERFORMANCE, STYLE, OTHER
    }

    /**
     * 根据 commit message 检测提交类型
     */
    private CommitType detectCommitType(String message) {
        if (message == null || message.isEmpty()) {
            return CommitType.OTHER;
        }

        // Bug 修复关键词
        if (containsAny(message, "fix", "bug", "修复", "问题", "issue", "hotfix", "patch", "解决", "修正")) {
            return CommitType.BUG_FIX;
        }

        // 新功能关键词
        if (containsAny(message, "feat", "feature", "add", "新增", "功能", "implement", "新加", "添加", "support")) {
            return CommitType.NEW_FEATURE;
        }

        // 重构关键词
        if (containsAny(message, "refactor", "重构", "优化", "improve", "clean", "restructure", "整理", "改进")) {
            return CommitType.REFACTOR;
        }

        // 文档关键词
        if (containsAny(message, "doc", "文档", "readme", "comment", "注释", "说明")) {
            return CommitType.DOCS;
        }

        // 测试关键词
        if (containsAny(message, "test", "测试", "spec", "unit", "单元", "集成")) {
            return CommitType.TEST;
        }

        // 性能关键词
        if (containsAny(message, "perf", "性能", "optimize", "加速", "speed", "fast", "cache", "缓存")) {
            return CommitType.PERFORMANCE;
        }

        // 代码风格关键词
        if (containsAny(message, "style", "format", "格式", "lint", "风格", "indent", "空格")) {
            return CommitType.STYLE;
        }

        return CommitType.OTHER;
    }

    /**
     * 检查字符串是否包含任意关键词
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成学习路径
     */
    @GetMapping("/learning-path/{repoId}")
    public Result<Map<String, Object>> generateLearningPath(@PathVariable Long repoId) {
        // 收集项目元数据
        String metadata = collectProjectMetadata(repoId);

        // 调用 AI 生成学习路径
        String learningPath = aiService.generateLearningPath(metadata);

        return Result.success(Map.of(
                "learningPath", learningPath,
                "metadata", metadata));
    }

    /**
     * 收集项目元数据用于 AI 分析
     */
    private String collectProjectMetadata(Long repoId) {
        StringBuilder sb = new StringBuilder();

        // 获取仓库信息
        com.codetimemachine.entity.Repository repo = repositoryMapper.selectById(repoId);
        if (repo != null) {
            sb.append("项目名: ").append(repo.getName()).append("\n");
            sb.append("URL: ").append(repo.getUrl()).append("\n");
            sb.append("语言: ").append(repo.getLanguage() != null ? repo.getLanguage() : "Java").append("\n");
            sb.append("总提交数: ").append(repo.getTotalCommits()).append("\n");
            sb.append("总文件数: ").append(repo.getTotalFiles()).append("\n\n");
        }

        // 获取文件修改次数统计
        sb.append("文件列表（按修改次数排序，Top 20）:\n");
        sb.append("| 文件路径 | 修改次数 |\n");
        sb.append("|---------|----------|\n");

        List<Map<String, Object>> fileCounts = fileChangeMapper.getFileModificationCounts(repoId);
        fileCounts.stream()
                .sorted((a, b) -> ((Number) b.get("modifyCount")).intValue()
                        - ((Number) a.get("modifyCount")).intValue())
                .limit(20)
                .forEach(file -> {
                    sb.append("| ").append(file.get("filePath"))
                            .append(" | ").append(file.get("modifyCount"))
                            .append(" |\n");
                });

        // 分析包结构
        sb.append("\n包/目录结构:\n");
        List<String> paths = fileChangeMapper.getDistinctFilePaths(repoId);
        paths.stream()
                .map(p -> p.contains("/") ? p.substring(0, p.lastIndexOf("/")) : "")
                .distinct()
                .limit(10)
                .forEach(dir -> {
                    if (!dir.isEmpty()) {
                        sb.append("- ").append(dir).append("/\n");
                    }
                });

        sb.append("\n入口文件特征:\n");
        sb.append("- 包含 main 方法的文件\n");
        sb.append("- 包含 @SpringBootApplication 的文件\n");
        sb.append("- 文件名包含 Application、Main 的文件\n");

        return sb.toString();
    }
}
