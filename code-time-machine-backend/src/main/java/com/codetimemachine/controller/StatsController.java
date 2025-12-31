package com.codetimemachine.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codetimemachine.common.Result;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.mapper.CommitRecordMapper;
import com.codetimemachine.mapper.FileChangeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final CommitRecordMapper commitRecordMapper;
    private final FileChangeMapper fileChangeMapper;

    @GetMapping("/lines-trend/{repoId}")
    public Result<Map<String, Object>> getLinesTrend(@PathVariable Long repoId) {
        LambdaQueryWrapper<CommitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommitRecord::getRepoId, repoId)
                .orderByAsc(CommitRecord::getCommitOrder)
                .select(CommitRecord::getCommitTime, CommitRecord::getAdditions, CommitRecord::getDeletions, CommitRecord::getCommitOrder);
        List<CommitRecord> commits = commitRecordMapper.selectList(wrapper);

        List<String> dates = new ArrayList<>();
        List<Integer> additions = new ArrayList<>();
        List<Integer> deletions = new ArrayList<>();
        List<Integer> total = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        int runningTotal = 0;

        for (CommitRecord commit : commits) {
            dates.add(commit.getCommitTime().format(formatter));
            additions.add(commit.getAdditions() != null ? commit.getAdditions() : 0);
            deletions.add(commit.getDeletions() != null ? commit.getDeletions() : 0);

            runningTotal += (commit.getAdditions() != null ? commit.getAdditions() : 0)
                    - (commit.getDeletions() != null ? commit.getDeletions() : 0);
            total.add(Math.max(0, runningTotal));
        }

        return Result.success(Map.of(
                "dates", dates,
                "additions", additions,
                "deletions", deletions,
                "total", total));
    }

    @GetMapping("/commit-frequency/{repoId}")
    public Result<Map<String, Object>> getCommitFrequency(@PathVariable Long repoId) {
        List<Map<String, Object>> stats = commitRecordMapper.getCommitFrequency(repoId);

        List<String> dates = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (Map<String, Object> row : stats) {
            Object date = row.get("commitDate");
            dates.add(date != null ? date.toString() : "");
            counts.add(((Number) row.getOrDefault("commitCount", 0)).longValue());
        }

        return Result.success(Map.of(
                "dates", dates,
                "counts", counts));
    }

    @GetMapping("/contributors/{repoId}")
    public Result<List<Map<String, Object>>> getContributors(@PathVariable Long repoId) {
        return Result.success(commitRecordMapper.getContributorStats(repoId, 20));
    }

    @GetMapping("/file-types/{repoId}")
    public Result<List<Map<String, Object>>> getFileTypes(@PathVariable Long repoId) {
        List<Map<String, Object>> stats = fileChangeMapper.getFileTypeStats(repoId);
        long total = stats.stream()
                .mapToLong(row -> ((Number) row.getOrDefault("fileCount", 0)).longValue())
                .sum();

        return Result.success(stats.stream()
                .limit(10)
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    String extension = (String) row.get("extension");
                    long count = ((Number) row.getOrDefault("fileCount", 0)).longValue();
                    item.put("extension", extension);
                    item.put("count", count);
                    item.put("percentage", total == 0 ? 0 : Math.round(count * 100.0 / total));
                    return item;
                })
                .collect(Collectors.toList()));
    }

    @GetMapping("/change-types/{repoId}")
    public Result<List<Map<String, Object>>> getChangeTypes(@PathVariable Long repoId) {
        List<Map<String, Object>> stats = fileChangeMapper.getChangeTypeStats(repoId);
        long total = stats.stream()
                .mapToLong(row -> ((Number) row.getOrDefault("changeCount", 0)).longValue())
                .sum();

        return Result.success(stats.stream()
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    String type = (String) row.get("changeType");
                    long count = ((Number) row.getOrDefault("changeCount", 0)).longValue();
                    item.put("category", type);
                    item.put("count", count);
                    item.put("percentage", total == 0 ? 0 : Math.round(count * 100.0 / total));
                    return item;
                })
                .collect(Collectors.toList()));
    }

    @GetMapping("/file-heatmap/{repoId}")
    public Result<List<Map<String, Object>>> getFileHeatmap(@PathVariable Long repoId) {
        List<Map<String, Object>> heatmapData = fileChangeMapper.getFileModificationCounts(repoId);

        int maxCount = heatmapData.stream()
                .mapToInt(m -> ((Number) m.get("modifyCount")).intValue())
                .max()
                .orElse(1);

        heatmapData.forEach(item -> {
            int count = ((Number) item.get("modifyCount")).intValue();
            int level = (int) Math.ceil((double) count / maxCount * 4);
            item.put("heatLevel", Math.min(level, 4));
        });

        return Result.success(heatmapData);
    }

    @GetMapping("/activity-heatmap/{repoId}")
    public Result<Map<String, Object>> getActivityHeatmap(@PathVariable Long repoId) {
        List<Map<String, Object>> stats = commitRecordMapper.getActivityHeatmap(repoId);

        Map<Integer, Map<Integer, Integer>> grouped = new HashMap<>();
        Map<Integer, Integer> byDayOfWeek = new HashMap<>();
        Map<Integer, Integer> byHour = new HashMap<>();
        int totalCommits = 0;

        for (Map<String, Object> row : stats) {
            int day = ((Number) row.getOrDefault("dayOfWeek", 0)).intValue();
            int hour = ((Number) row.getOrDefault("hour", 0)).intValue();
            int count = ((Number) row.getOrDefault("commitCount", 0)).intValue();

            grouped.computeIfAbsent(day, k -> new HashMap<>()).put(hour, count);
            byDayOfWeek.put(day, byDayOfWeek.getOrDefault(day, 0) + count);
            byHour.put(hour, byHour.getOrDefault(hour, 0) + count);
            totalCommits += count;
        }

        List<List<Integer>> matrix = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            List<Integer> row = new ArrayList<>();
            Map<Integer, Integer> hourMap = grouped.getOrDefault(day, Collections.emptyMap());
            for (int hour = 0; hour < 24; hour++) {
                row.add(hourMap.getOrDefault(hour, 0));
            }
            matrix.add(row);
        }

        List<String> dayLabels = Arrays.asList("周日", "周一", "周二", "周三",
                "周四", "周五", "周六");

        List<String> hourLabels = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            hourLabels.add(String.format("%02d:00", i));
        }

        return Result.success(Map.of(
                "matrix", matrix,
                "dayLabels", dayLabels,
                "hourLabels", hourLabels,
                "byDayOfWeek", byDayOfWeek,
                "byHour", byHour,
                "totalCommits", totalCommits));
    }
}
