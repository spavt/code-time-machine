package com.codetimemachine.controller;

import com.codetimemachine.common.Result;
import com.codetimemachine.dto.FileTimelineDTO;
import com.codetimemachine.dto.MethodInfo;
import com.codetimemachine.service.FileService;
import com.codetimemachine.service.MethodParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文件接口
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final MethodParserService methodParserService;

    /**
     * 获取文件树
     */
    @GetMapping("/tree/{repoId}")
    public Result<List<Map<String, Object>>> getFileTree(@PathVariable Long repoId) {
        return Result.success(fileService.getFileTree(repoId));
    }

    /**
     * 获取文件演化时间线
     */
    @GetMapping("/timeline/{repoId}")
    public Result<FileTimelineDTO> getTimeline(
            @PathVariable Long repoId,
            @RequestParam String filePath,
            @RequestParam(defaultValue = "false") boolean includeContent) {
        return Result.success(fileService.getTimeline(repoId, filePath, includeContent));
    }

    /**
     * 获取文件内容
     */
    @GetMapping("/content")
    public Result<Map<String, Object>> getContent(
            @RequestParam Long repoId,
            @RequestParam Long commitId,
            @RequestParam String filePath) {
        String content = fileService.getFileContent(repoId, commitId, filePath);

        // 检测语言
        String language = detectLanguage(filePath);
        int lineCount = content != null ? content.split("\n").length : 0;

        return Result.success(Map.of(
                "content", content != null ? content : "",
                "language", language,
                "lineCount", lineCount));
    }

    /**
     * 搜索文件
     */
    @GetMapping("/search/{repoId}")
    public Result<List<Map<String, Object>>> searchFiles(
            @PathVariable Long repoId,
            @RequestParam String keyword) {
        return Result.success(fileService.searchFiles(repoId, keyword));
    }

    /**
     * 获取文件演进故事
     */
    @GetMapping("/evolution-story/{repoId}")
    public Result<Map<String, Object>> getEvolutionStory(
            @PathVariable Long repoId,
            @RequestParam String filePath) {
        return Result.success(fileService.getEvolutionStory(repoId, filePath));
    }

    /**
     * 获取文件在两个提交之间的diff
     */
    @GetMapping("/diff/{repoId}")
    public Result<Map<String, Object>> getDiff(
            @PathVariable Long repoId,
            @RequestParam String fromCommit,
            @RequestParam String toCommit,
            @RequestParam String filePath) {
        String diff = fileService.getDiff(repoId, fromCommit, toCommit, filePath);
        return Result.success(Map.of("diff", diff != null ? diff : ""));
    }

    /**
     * 获取文件中的方法列表
     */
    @GetMapping("/methods/{repoId}")
    public Result<List<MethodInfo>> getMethods(
            @PathVariable Long repoId,
            @RequestParam Long commitId,
            @RequestParam String filePath) {
        // 获取文件内容
        String content = fileService.getFileContent(repoId, commitId, filePath);
        if (content == null) {
            return Result.success(List.of());
        }

        // 检测语言并解析方法
        String language = detectLanguage(filePath);
        List<MethodInfo> methods = methodParserService.parseMethods(content, language);
        return Result.success(methods);
    }

    /**
     * 获取方法演化时间线
     */
    @GetMapping("/method-timeline/{repoId}")
    public Result<List<Map<String, Object>>> getMethodTimeline(
            @PathVariable Long repoId,
            @RequestParam String filePath,
            @RequestParam String methodName) {
        return Result.success(fileService.getMethodTimeline(repoId, filePath, methodName));
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
