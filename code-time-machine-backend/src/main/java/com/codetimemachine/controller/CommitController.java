package com.codetimemachine.controller;

import com.codetimemachine.common.PageResult;
import com.codetimemachine.common.Result;
import com.codetimemachine.dto.CommitStatsDTO;
import com.codetimemachine.entity.AiAnalysis;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.entity.FileChange;
import com.codetimemachine.service.CommitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提交记录接口
 */
@RestController
@RequestMapping("/api/commit")
@RequiredArgsConstructor
public class CommitController {

    private final CommitService commitService;

    /**
     * 获取提交列表
     */
    @GetMapping("/list/{repoId}")
    public Result<PageResult<CommitRecord>> getList(
            @PathVariable Long repoId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(commitService.getList(repoId, page, pageSize, keyword));
    }

    /**
     * 获取提交详情
     */
    @GetMapping("/{id}")
    public Result<CommitRecord> getById(@PathVariable Long id) {
        return Result.success(commitService.getById(id));
    }

    /**
     * 获取提交的文件变更
     */
    @GetMapping("/{commitId}/files")
    public Result<List<FileChange>> getFileChanges(@PathVariable Long commitId) {
        return Result.success(commitService.getFileChanges(commitId));
    }

    /**
     * 获取提交的统计信息（按需计算）
     */
    @GetMapping("/{commitId}/stats")
    public Result<CommitStatsDTO> getStats(@PathVariable Long commitId) {
        return Result.success(commitService.getStats(commitId));
    }

    /**
     * 获取提交的AI分析
     */
    @GetMapping("/{commitId}/analysis")
    public Result<AiAnalysis> getAiAnalysis(@PathVariable Long commitId) {
        AiAnalysis analysis = commitService.getAiAnalysis(commitId);
        if (analysis == null) {
            return Result.error(404, "暂无AI分析");
        }
        return Result.success(analysis);
    }

    /**
     * 触发AI分析
     */
    @PostMapping("/{commitId}/analyze")
    public Result<AiAnalysis> triggerAnalysis(@PathVariable Long commitId) {
        return Result.success(commitService.triggerAnalysis(commitId));
    }
}
