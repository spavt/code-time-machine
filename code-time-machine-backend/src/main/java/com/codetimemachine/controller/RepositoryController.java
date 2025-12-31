package com.codetimemachine.controller;

import com.codetimemachine.common.PageResult;
import com.codetimemachine.common.Result;
import com.codetimemachine.dto.AnalyzeOptionsDTO;
import com.codetimemachine.dto.RepoOverviewDTO;
import com.codetimemachine.entity.Repository;
import com.codetimemachine.service.RepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repository")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;

    @GetMapping("/list")
    public Result<PageResult<Repository>> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(repositoryService.getList(page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<Repository> getById(@PathVariable Long id) {
        return Result.success(repositoryService.getById(id));
    }

    @PostMapping("/analyze")
    public Result<Repository> analyze(@RequestBody Map<String, Object> request) {
        String url = (String) request.get("url");
        if (url == null || url.isEmpty()) {
            return Result.error("请提供仓库URL");
        }

        AnalyzeOptionsDTO options = new AnalyzeOptionsDTO();

        if (request.containsKey("depth")) {
            Object depthObj = request.get("depth");
            if (depthObj instanceof Number) {
                options.setDepth(((Number) depthObj).intValue());
            }
        }
        if (request.containsKey("since") && request.get("since") != null) {
            String sinceStr = (String) request.get("since");
            if (!sinceStr.isEmpty()) {
                options.setSince(LocalDateTime.parse(sinceStr));
            }
        }
        if (request.containsKey("until") && request.get("until") != null) {
            String untilStr = (String) request.get("until");
            if (!untilStr.isEmpty()) {
                options.setUntil(LocalDateTime.parse(untilStr));
            }
        }
        if (request.containsKey("pathFilters")) {
            @SuppressWarnings("unchecked")
            List<String> pathFilters = (List<String>) request.get("pathFilters");
            options.setPathFilters(pathFilters);
        }
        if (request.containsKey("shallow")) {
            options.setShallow((Boolean) request.get("shallow"));
        }

        return Result.success(repositoryService.analyzeByUrl(url, options));
    }

    @GetMapping("/{id}/progress")
    public Result<Map<String, Object>> getProgress(@PathVariable Long id) {
        Repository repo = repositoryService.getById(id);
        int progress = repositoryService.getAnalyzeProgress(id);
        return Result.success(Map.of(
                "progress", progress,
                "status", repo.getStatus()));
    }

    @GetMapping("/{id}/overview")
    public Result<RepoOverviewDTO> getOverview(@PathVariable Long id) {
        return Result.success(repositoryService.getOverview(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        repositoryService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/fetch-more-history")
    public Result<Repository> fetchMoreHistory(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> request) {
        int additionalDepth = request.getOrDefault("depth", 500);
        return Result.success(repositoryService.fetchMoreHistory(id, additionalDepth));
    }
}
