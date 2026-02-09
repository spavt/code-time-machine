package com.codetimemachine.service.impl;

import com.codetimemachine.common.BusinessException;
import com.codetimemachine.dto.KeyCommitDTO;
import com.codetimemachine.dto.LearningMethodUnitDTO;
import com.codetimemachine.dto.LearningMissionDTO;
import com.codetimemachine.dto.LearningPlanDTO;
import com.codetimemachine.dto.LearningQuizQuestionDTO;
import com.codetimemachine.dto.MethodInfo;
import com.codetimemachine.entity.Repository;
import com.codetimemachine.mapper.FileChangeMapper;
import com.codetimemachine.mapper.RepositoryMapper;
import com.codetimemachine.service.FileService;
import com.codetimemachine.service.LearningService;
import com.codetimemachine.service.MethodParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningServiceImpl implements LearningService {

    private static final int MAX_MISSIONS = 5;
    private static final int MAX_FILES_PER_MISSION = 4;
    private static final int MAX_COMMITS_PER_MISSION = 3;
    private static final int MAX_CLUSTER_FILES = 80;
    private static final int MIN_CO_CHANGE_COUNT = 2;
    private static final double MIN_CO_CHANGE_RATIO = 0.15;
    private static final int MAX_COMMIT_CANDIDATES = 180;
    private static final int MAX_METHOD_UNITS_PER_MISSION = 3;
    private static final int MAX_METHODS_PER_FILE = 2;
    private static final int QUIZ_OPTIONS_PER_QUESTION = 4;
    private static final int METHOD_PASS_THRESHOLD = 2;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RepositoryMapper repositoryMapper;
    private final FileChangeMapper fileChangeMapper;
    private final FileService fileService;
    private final MethodParserService methodParserService;

    @Override
    public LearningPlanDTO buildLearningPlan(Long repoId) {
        Repository repo = repositoryMapper.selectById(repoId);
        if (repo == null) {
            throw new BusinessException("仓库不存在");
        }

        List<FileHotStat> fileStats = loadFileStats(repoId);
        if (fileStats.isEmpty()) {
            return buildEmptyPlan(repo);
        }

        List<ModuleStat> moduleStats = buildModuleStats(repoId, fileStats);
        List<LearningMissionDTO> missions = moduleStats.stream()
                .limit(MAX_MISSIONS)
                .map(module -> buildMission(repoId, module))
                .collect(Collectors.toList());

        LearningPlanDTO plan = new LearningPlanDTO();
        plan.setRepoId(repoId);
        plan.setRepoName(repo.getName());
        plan.setMissions(missions);
        plan.setTotalMissions(missions.size());
        plan.setEstimatedTotalMinutes(missions.stream()
                .map(LearningMissionDTO::getEstimatedMinutes)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum));
        plan.setGlobalSuggestions(List.of(
                "先从任务证据卡理解‘为什么推荐’，再进入具体文件和关键提交。",
                "关键提交按‘引入-转折-稳定’三阶段组织，建议按顺序阅读。",
                "每个任务完成后，用一句话总结模块职责、边界和最近演进方向。",
                "新增方法级掌握单元：先吃透一个方法，再完成轻量测验并解锁下一个方法。"));
        return plan;
    }

    private List<FileHotStat> loadFileStats(Long repoId) {
        List<Map<String, Object>> rows = fileChangeMapper.getFileModificationCounts(repoId);
        List<FileHotStat> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String filePath = stringValue(row.get("filePath"));
            int modifyCount = numberValue(row.get("modifyCount"));
            if (filePath == null || filePath.isBlank() || modifyCount <= 0) {
                continue;
            }
            result.add(new FileHotStat(filePath, modifyCount));
        }

        result.sort(Comparator.comparingInt((FileHotStat f) -> f.modifyCount).reversed());
        return result;
    }

    private List<ModuleStat> buildModuleStats(Long repoId, List<FileHotStat> fileStats) {
        if (fileStats.isEmpty()) {
            return List.of();
        }

        List<FileHotStat> clusterSeeds = fileStats.stream()
                .limit(MAX_CLUSTER_FILES)
                .collect(Collectors.toList());

        List<String> seedPaths = clusterSeeds.stream()
                .map(f -> f.filePath)
                .collect(Collectors.toList());

        Map<String, FileHotStat> statMap = clusterSeeds.stream()
                .collect(Collectors.toMap(f -> f.filePath, f -> f, (a, b) -> a, LinkedHashMap::new));

        Map<String, Map<String, Integer>> coChangeGraph = buildCoChangeGraph(repoId, seedPaths);
        List<List<String>> clusters = buildClusters(clusterSeeds, statMap, coChangeGraph);

        if (clusters.isEmpty()) {
            return buildPathBasedModules(fileStats);
        }

        List<ModuleStat> modules = new ArrayList<>();
        int index = 1;
        for (List<String> clusterPaths : clusters) {
            List<FileHotStat> files = clusterPaths.stream()
                    .map(statMap::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt((FileHotStat f) -> f.modifyCount).reversed())
                    .collect(Collectors.toList());

            if (files.isEmpty()) {
                continue;
            }

            ModuleStat module = new ModuleStat();
            module.moduleKey = inferModuleKey(files.stream().map(f -> f.filePath).collect(Collectors.toList()));
            module.displayName = buildModuleDisplayName(module.moduleKey, index++);
            module.fileCount = files.size();
            module.hotScore = files.stream().mapToInt(f -> f.modifyCount).sum();
            module.topFiles = files.stream()
                    .limit(MAX_FILES_PER_MISSION)
                    .map(f -> f.filePath)
                    .collect(Collectors.toList());
            module.avgCoChange = computeClusterCoChange(files, coChangeGraph);
            modules.add(module);
        }

        modules.sort(Comparator.comparingInt((ModuleStat m) -> m.hotScore).reversed()
                .thenComparingInt(m -> m.fileCount));
        return modules;
    }

    private List<ModuleStat> buildPathBasedModules(List<FileHotStat> fileStats) {
        Map<String, List<FileHotStat>> grouped = new LinkedHashMap<>();
        for (FileHotStat stat : fileStats) {
            String key = extractModuleKey(stat.filePath);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(stat);
        }

        int index = 1;
        List<ModuleStat> modules = new ArrayList<>();
        for (Map.Entry<String, List<FileHotStat>> entry : grouped.entrySet()) {
            List<FileHotStat> files = entry.getValue().stream()
                    .sorted(Comparator.comparingInt((FileHotStat f) -> f.modifyCount).reversed())
                    .collect(Collectors.toList());

            ModuleStat module = new ModuleStat();
            module.moduleKey = entry.getKey();
            module.displayName = buildModuleDisplayName(entry.getKey(), index++);
            module.fileCount = files.size();
            module.hotScore = files.stream().mapToInt(f -> f.modifyCount).sum();
            module.topFiles = files.stream()
                    .limit(MAX_FILES_PER_MISSION)
                    .map(f -> f.filePath)
                    .collect(Collectors.toList());
            module.avgCoChange = 0;
            modules.add(module);
        }

        modules.sort(Comparator.comparingInt((ModuleStat m) -> m.hotScore).reversed()
                .thenComparingInt(m -> m.fileCount));
        return modules;
    }

    private Map<String, Map<String, Integer>> buildCoChangeGraph(Long repoId, List<String> seedPaths) {
        Map<String, Map<String, Integer>> graph = new HashMap<>();
        if (seedPaths.size() < 2) {
            return graph;
        }

        List<Map<String, Object>> pairs = fileChangeMapper.getCoChangePairs(repoId, seedPaths, MIN_CO_CHANGE_COUNT);
        for (Map<String, Object> row : pairs) {
            String fileA = stringValue(row.get("fileA"));
            String fileB = stringValue(row.get("fileB"));
            int coCount = numberValue(row.get("coChangeCount"));
            if (fileA == null || fileB == null || coCount < MIN_CO_CHANGE_COUNT) {
                continue;
            }

            graph.computeIfAbsent(fileA, k -> new HashMap<>()).put(fileB, coCount);
            graph.computeIfAbsent(fileB, k -> new HashMap<>()).put(fileA, coCount);
        }
        return graph;
    }

    private List<List<String>> buildClusters(List<FileHotStat> orderedSeeds,
                                             Map<String, FileHotStat> statMap,
                                             Map<String, Map<String, Integer>> coChangeGraph) {
        List<List<String>> clusters = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (FileHotStat seed : orderedSeeds) {
            if (visited.contains(seed.filePath)) {
                continue;
            }

            Set<String> clusterSet = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(seed.filePath);
            clusterSet.add(seed.filePath);
            visited.add(seed.filePath);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                Map<String, Integer> neighbors = coChangeGraph.getOrDefault(current, Map.of());
                for (Map.Entry<String, Integer> entry : neighbors.entrySet()) {
                    String neighbor = entry.getKey();
                    if (visited.contains(neighbor)) {
                        continue;
                    }

                    if (isStrongEdge(current, neighbor, entry.getValue(), statMap)) {
                        visited.add(neighbor);
                        clusterSet.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }

            List<String> cluster = clusterSet.stream()
                    .sorted(Comparator.comparingInt((String path) -> statMap.get(path).modifyCount).reversed())
                    .collect(Collectors.toList());
            clusters.add(cluster);
        }

        return clusters;
    }

    private boolean isStrongEdge(String fileA,
                                 String fileB,
                                 int coCount,
                                 Map<String, FileHotStat> statMap) {
        if (coCount >= 5) {
            return true;
        }
        FileHotStat a = statMap.get(fileA);
        FileHotStat b = statMap.get(fileB);
        if (a == null || b == null) {
            return false;
        }
        int minModify = Math.max(1, Math.min(a.modifyCount, b.modifyCount));
        double ratio = coCount * 1.0 / minModify;
        return coCount >= MIN_CO_CHANGE_COUNT && ratio >= MIN_CO_CHANGE_RATIO;
    }

    private double computeClusterCoChange(List<FileHotStat> files,
                                          Map<String, Map<String, Integer>> coChangeGraph) {
        if (files.size() <= 1) {
            return 0;
        }
        int edgeCount = 0;
        int edgeSum = 0;

        for (int i = 0; i < files.size(); i++) {
            for (int j = i + 1; j < files.size(); j++) {
                String a = files.get(i).filePath;
                String b = files.get(j).filePath;
                int score = coChangeGraph.getOrDefault(a, Map.of()).getOrDefault(b, 0);
                if (score > 0) {
                    edgeCount++;
                    edgeSum += score;
                }
            }
        }

        if (edgeCount == 0) {
            return 0;
        }
        return edgeSum * 1.0 / edgeCount;
    }

    private LearningMissionDTO buildMission(Long repoId, ModuleStat module) {
        LearningMissionDTO mission = new LearningMissionDTO();
        mission.setMissionId("mission-" + sanitizeId(module.moduleKey));
        mission.setTitle("理解模块：" + module.displayName);
        mission.setModule(module.moduleKey);
        mission.setObjective("围绕模块职责、依赖关系和演进拐点，形成可复述的功能理解。");
        mission.setImportanceReason("该模块热度为 " + module.hotScore + "，且存在明显的跨文件共变更行为。");
        mission.setDifficulty(resolveDifficulty(module.hotScore, module.fileCount));
        mission.setHotScore(module.hotScore);
        mission.setFilePaths(module.topFiles);

        List<KeyCommitCandidate> commitCandidates = loadModuleCommitCandidates(repoId, module.topFiles);
        List<KeyCommitDTO> keyCommits = loadStageKeyCommits(commitCandidates);
        List<LearningMethodUnitDTO> methodUnits = buildMethodUnits(repoId, module.topFiles, commitCandidates, keyCommits);
        mission.setMethodUnits(methodUnits);
        mission.setEstimatedMinutes(estimateMinutes(module, methodUnits.size()));
        mission.setKeyCommits(keyCommits);
        mission.setRecommendationEvidence(buildEvidence(module, keyCommits));
        mission.setLearningSteps(buildLearningSteps(module, keyCommits, methodUnits));
        mission.setCheckpoints(buildCheckpoints(module, methodUnits));
        return mission;
    }

    private List<KeyCommitCandidate> loadModuleCommitCandidates(Long repoId, List<String> topFiles) {
        if (topFiles == null || topFiles.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows = fileChangeMapper.getModuleCommitCandidates(repoId, topFiles);
        return rows.stream()
                .map(this::toCommitCandidate)
                .filter(Objects::nonNull)
                .filter(c -> c.commitOrder != null)
                .collect(Collectors.toList());
    }

    private List<KeyCommitDTO> loadStageKeyCommits(List<KeyCommitCandidate> allCandidates) {
        if (allCandidates == null || allCandidates.isEmpty()) {
            return List.of();
        }

        List<KeyCommitCandidate> candidates = reduceCandidates(allCandidates);
        List<KeyCommitCandidate> selected = selectStageCommits(candidates);
        return buildCommitDTOs(selected);
    }

    private KeyCommitCandidate toCommitCandidate(Map<String, Object> row) {
        Long commitId = longValue(row.get("commitId"));
        Integer commitOrder = integerValue(row.get("commitOrder"));
        if (commitId == null || commitOrder == null) {
            return null;
        }

        KeyCommitCandidate candidate = new KeyCommitCandidate();
        candidate.commitId = commitId;
        candidate.commitOrder = commitOrder;
        candidate.shortHash = stringValue(row.get("shortHash"));
        candidate.commitMessage = stringValue(row.get("commitMessage"));
        candidate.authorName = stringValue(row.get("authorName"));
        Object commitTime = row.get("commitTime");
        if (commitTime instanceof java.time.LocalDateTime localDateTime) {
            candidate.commitTime = localDateTime.format(TIME_FORMATTER);
        } else if (commitTime != null) {
            candidate.commitTime = commitTime.toString();
        }
        candidate.touchedFiles = numberValue(row.get("touchedFiles"));
        candidate.additions = numberValue(row.get("additions"));
        candidate.deletions = numberValue(row.get("deletions"));
        candidate.hitFiles = splitList(stringValue(row.get("hitFiles")), "\\|\\|");
        candidate.changeTypes = splitList(stringValue(row.get("changeTypes")), ",");
        candidate.impactScore = computeImpactScore(candidate);
        return candidate;
    }

    private List<KeyCommitCandidate> reduceCandidates(List<KeyCommitCandidate> allCandidates) {
        List<KeyCommitCandidate> byOrder = allCandidates.stream()
                .sorted(Comparator.comparingInt(c -> c.commitOrder))
                .collect(Collectors.toList());

        if (byOrder.size() <= MAX_COMMIT_CANDIDATES) {
            return byOrder;
        }

        LinkedHashMap<Long, KeyCommitCandidate> map = new LinkedHashMap<>();
        addRange(byOrder, map, 0, 60);
        int middleStart = Math.max(0, byOrder.size() / 2 - 30);
        addRange(byOrder, map, middleStart, middleStart + 60);
        addRange(byOrder, map, Math.max(0, byOrder.size() - 60), byOrder.size());

        List<KeyCommitCandidate> byImpact = allCandidates.stream()
                .sorted(Comparator.comparingDouble((KeyCommitCandidate c) -> c.impactScore).reversed())
                .collect(Collectors.toList());
        for (KeyCommitCandidate candidate : byImpact) {
            if (map.size() >= MAX_COMMIT_CANDIDATES) {
                break;
            }
            map.putIfAbsent(candidate.commitId, candidate);
        }

        return map.values().stream()
                .sorted(Comparator.comparingInt(c -> c.commitOrder))
                .collect(Collectors.toList());
    }

    private void addRange(List<KeyCommitCandidate> source,
                          LinkedHashMap<Long, KeyCommitCandidate> target,
                          int startInclusive,
                          int endExclusive) {
        int start = Math.max(0, startInclusive);
        int end = Math.min(source.size(), endExclusive);
        for (int i = start; i < end; i++) {
            KeyCommitCandidate candidate = source.get(i);
            target.putIfAbsent(candidate.commitId, candidate);
        }
    }

    private List<KeyCommitCandidate> selectStageCommits(List<KeyCommitCandidate> candidates) {
        List<KeyCommitCandidate> ordered = candidates.stream()
                .sorted(Comparator.comparingInt(c -> c.commitOrder))
                .collect(Collectors.toList());

        if (ordered.size() <= MAX_COMMITS_PER_MISSION) {
            return ordered;
        }

        int size = ordered.size();
        int firstBoundary = Math.max(1, size / 3);
        int secondBoundary = Math.max(firstBoundary + 1, (size * 2) / 3);

        List<KeyCommitCandidate> introRange = ordered.subList(0, firstBoundary);
        List<KeyCommitCandidate> turningRange = ordered.subList(firstBoundary, secondBoundary);
        List<KeyCommitCandidate> stableRange = ordered.subList(secondBoundary, size);

        List<KeyCommitCandidate> selected = new ArrayList<>();
        addBestCandidate(selected, introRange);
        addBestCandidate(selected, turningRange);
        addBestCandidate(selected, stableRange);

        Set<Long> selectedIds = selected.stream().map(c -> c.commitId).collect(Collectors.toSet());
        if (selected.size() < MAX_COMMITS_PER_MISSION) {
            List<KeyCommitCandidate> byImpact = ordered.stream()
                    .sorted(Comparator.comparingDouble((KeyCommitCandidate c) -> c.impactScore).reversed())
                    .collect(Collectors.toList());
            for (KeyCommitCandidate candidate : byImpact) {
                if (selected.size() >= MAX_COMMITS_PER_MISSION) {
                    break;
                }
                if (selectedIds.add(candidate.commitId)) {
                    selected.add(candidate);
                }
            }
        }

        selected.sort(Comparator.comparingInt(c -> c.commitOrder));
        if (selected.size() > MAX_COMMITS_PER_MISSION) {
            return selected.subList(0, MAX_COMMITS_PER_MISSION);
        }
        return selected;
    }

    private void addBestCandidate(List<KeyCommitCandidate> selected, List<KeyCommitCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        KeyCommitCandidate best = candidates.stream()
                .max(Comparator.comparingDouble(c -> c.impactScore))
                .orElse(null);
        if (best != null && selected.stream().noneMatch(c -> c.commitId.equals(best.commitId))) {
            selected.add(best);
        }
    }

    private List<KeyCommitDTO> buildCommitDTOs(List<KeyCommitCandidate> selected) {
        List<KeyCommitDTO> result = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            KeyCommitCandidate candidate = selected.get(i);
            String phase = resolvePhaseLabel(i, selected.size());

            KeyCommitDTO dto = new KeyCommitDTO();
            dto.setCommitId(candidate.commitId);
            dto.setCommitOrder(candidate.commitOrder);
            dto.setShortHash(candidate.shortHash);
            dto.setCommitMessage(candidate.commitMessage);
            dto.setAuthorName(candidate.authorName);
            dto.setCommitTime(candidate.commitTime);
            dto.setPhase(phase);
            dto.setTouchedFiles(candidate.touchedFiles);
            dto.setAdditions(candidate.additions);
            dto.setDeletions(candidate.deletions);
            dto.setHitFiles(candidate.hitFiles.stream().limit(3).collect(Collectors.toList()));
            dto.setChangeTypes(candidate.changeTypes);
            dto.setFocusReason(buildFocusReason(phase, candidate));
            result.add(dto);
        }
        return result;
    }

    private String resolvePhaseLabel(int index, int total) {
        if (total == 1) {
            return "关键提交";
        }
        if (total == 2) {
            return index == 0 ? "引入期" : "稳定期";
        }
        if (index == 0) {
            return "引入期";
        }
        if (index == total - 1) {
            return "稳定期";
        }
        return "转折期";
    }

    private String buildFocusReason(String phase, KeyCommitCandidate candidate) {
        String typeFocus;
        if (containsAny(candidate.changeTypes, "RENAME", "DELETE")) {
            typeFocus = "出现结构调整或重构信号";
        } else if (containsAny(candidate.changeTypes, "ADD")) {
            typeFocus = "体现了模块能力扩展";
        } else {
            typeFocus = "以优化与稳定性调整为主";
        }

        if ("引入期".equals(phase)) {
            return "在演进早期集中修改核心文件，" + typeFocus + "。";
        }
        if ("转折期".equals(phase)) {
            return "在中期形成明显拐点，" + typeFocus + "。";
        }
        if ("稳定期".equals(phase)) {
            return "在近期进行收敛优化，" + typeFocus + "。";
        }
        return "这是当前模块最具代表性的关键提交，" + typeFocus + "。";
    }

    private boolean containsAny(List<String> values, String... targets) {
        Set<String> valueSet = values.stream().map(String::trim).collect(Collectors.toSet());
        for (String target : targets) {
            if (valueSet.contains(target)) {
                return true;
            }
        }
        return false;
    }

    private List<LearningMethodUnitDTO> buildMethodUnits(Long repoId,
                                                         List<String> topFiles,
                                                         List<KeyCommitCandidate> commitCandidates,
                                                         List<KeyCommitDTO> keyCommits) {
        if (topFiles == null || topFiles.isEmpty()) {
            return List.of();
        }

        Long referenceCommitId = resolveReferenceCommitId(commitCandidates, keyCommits);
        Map<String, Long> latestCommitByFile = buildLatestCommitByFile(commitCandidates);

        List<LearningMethodUnitDTO> units = new ArrayList<>();
        for (String filePath : topFiles) {
            if (units.size() >= MAX_METHOD_UNITS_PER_MISSION) {
                break;
            }
            if (filePath == null || filePath.isBlank()) {
                continue;
            }
            if (!isSourceLikeFile(filePath)) {
                continue;
            }

            Long commitId = latestCommitByFile.getOrDefault(filePath, referenceCommitId);
            if (commitId == null) {
                continue;
            }

            String content = fileService.getFileContent(repoId, commitId, filePath);
            if (content == null || content.isBlank()) {
                continue;
            }

            String language = detectLanguageByPath(filePath);
            List<MethodInfo> methods = methodParserService.parseMethods(content, language);
            if (methods == null || methods.isEmpty()) {
                continue;
            }

            List<MethodInfo> selectedMethods = methods.stream()
                    .filter(this::isMethodCandidate)
                    .sorted(Comparator.comparingInt(this::scoreMethod).reversed())
                    .limit(MAX_METHODS_PER_FILE)
                    .collect(Collectors.toList());

            for (MethodInfo method : selectedMethods) {
                if (units.size() >= MAX_METHOD_UNITS_PER_MISSION) {
                    break;
                }
                units.add(toMethodUnit(filePath, method, topFiles));
            }
        }

        if (units.isEmpty()) {
            units.addAll(buildFallbackMethodUnits(repoId, topFiles, referenceCommitId));
        }

        if (units.size() > MAX_METHOD_UNITS_PER_MISSION) {
            return units.subList(0, MAX_METHOD_UNITS_PER_MISSION);
        }
        return units;
    }

    private Map<String, Long> buildLatestCommitByFile(List<KeyCommitCandidate> commitCandidates) {
        if (commitCandidates == null || commitCandidates.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> latestByFile = new HashMap<>();
        List<KeyCommitCandidate> ordered = commitCandidates.stream()
                .sorted(Comparator.comparingInt((KeyCommitCandidate c) -> c.commitOrder == null ? Integer.MIN_VALUE : c.commitOrder).reversed())
                .collect(Collectors.toList());

        for (KeyCommitCandidate candidate : ordered) {
            if (candidate.commitId == null || candidate.hitFiles == null) {
                continue;
            }
            for (String hitFile : candidate.hitFiles) {
                if (hitFile == null || hitFile.isBlank()) {
                    continue;
                }
                latestByFile.putIfAbsent(hitFile, candidate.commitId);
            }
        }
        return latestByFile;
    }

    private List<LearningMethodUnitDTO> buildFallbackMethodUnits(Long repoId,
                                                                 List<String> topFiles,
                                                                 Long defaultCommitId) {
        List<FileHotStat> allStats = loadFileStats(repoId);
        if (allStats.isEmpty()) {
            return List.of();
        }

        Set<String> excluded = topFiles == null ? Set.of() : new HashSet<>(topFiles);
        List<String> sourceCandidates = allStats.stream()
                .map(f -> f.filePath)
                .filter(path -> !excluded.contains(path))
                .filter(this::isSourceLikeFile)
                .limit(30)
                .collect(Collectors.toList());
        if (sourceCandidates.isEmpty()) {
            return List.of();
        }

        List<KeyCommitCandidate> candidateCommits = fileChangeMapper.getModuleCommitCandidates(repoId, sourceCandidates).stream()
                .map(this::toCommitCandidate)
                .filter(Objects::nonNull)
                .filter(c -> c.commitOrder != null)
                .collect(Collectors.toList());

        Map<String, Long> latestByFile = buildLatestCommitByFile(candidateCommits);
        List<LearningMethodUnitDTO> units = new ArrayList<>();
        for (String filePath : sourceCandidates) {
            if (units.size() >= MAX_METHOD_UNITS_PER_MISSION) {
                break;
            }
            Long commitId = latestByFile.getOrDefault(filePath, defaultCommitId);
            if (commitId == null) {
                continue;
            }

            String content = fileService.getFileContent(repoId, commitId, filePath);
            if (content == null || content.isBlank()) {
                continue;
            }

            String language = detectLanguageByPath(filePath);
            List<MethodInfo> methods = methodParserService.parseMethods(content, language);
            if (methods == null || methods.isEmpty()) {
                continue;
            }

            MethodInfo selected = methods.stream()
                    .filter(this::isMethodCandidate)
                    .max(Comparator.comparingInt(this::scoreMethod))
                    .orElse(null);
            if (selected != null) {
                units.add(toMethodUnit(filePath, selected, sourceCandidates));
            }
        }
        return units;
    }

    private Long resolveReferenceCommitId(List<KeyCommitCandidate> commitCandidates, List<KeyCommitDTO> keyCommits) {
        if (commitCandidates != null && !commitCandidates.isEmpty()) {
            return commitCandidates.stream()
                    .filter(c -> c.commitId != null)
                    .max(Comparator.comparingInt((KeyCommitCandidate c) -> c.commitOrder == null ? Integer.MIN_VALUE : c.commitOrder))
                    .map(c -> c.commitId)
                    .orElse(null);
        }

        if (keyCommits != null && !keyCommits.isEmpty()) {
            return keyCommits.stream()
                    .filter(c -> c.getCommitId() != null)
                    .max(Comparator.comparingInt((KeyCommitDTO c) -> c.getCommitOrder() == null ? Integer.MIN_VALUE : c.getCommitOrder()))
                    .map(KeyCommitDTO::getCommitId)
                    .orElse(null);
        }

        return null;
    }

    private LearningMethodUnitDTO toMethodUnit(String filePath, MethodInfo method, List<String> moduleFiles) {
        LearningMethodUnitDTO unit = new LearningMethodUnitDTO();
        unit.setUnitId("unit-" + sanitizeId(filePath + "-" + method.getName() + "-" + method.getStartLine()));
        unit.setFilePath(filePath);
        unit.setMethodName(method.getName());
        unit.setMethodSignature(method.getSignature());
        unit.setClassName(method.getClassName());
        unit.setStartLine(method.getStartLine() > 0 ? method.getStartLine() : null);
        unit.setEndLine(method.getEndLine() > 0 ? method.getEndLine() : null);
        unit.setParameterCount(Math.max(0, method.getParameterCount()));
        unit.setObjective("理解该方法的输入、输出、分支和异常处理路径。");
        unit.setImportanceReason("该方法来自当前任务的热点文件，先吃透一个方法再扩展到模块更适合新手。");

        int span = methodSpan(method);
        int estimatedMinutes = Math.max(6, Math.min(16, 5 + span / 6 + Math.max(0, method.getParameterCount())));
        unit.setEstimatedMinutes(estimatedMinutes);
        unit.setLearningHints(List.of(
                "先看方法签名，写出输入参数和可能的返回结果。",
                "按 if/switch/循环分支梳理主路径和边界条件。",
                "结合关键提交，判断该方法最近是扩展、重构还是稳定性修复。"));

        List<LearningQuizQuestionDTO> quizQuestions = buildMethodQuiz(method, filePath, moduleFiles);
        unit.setQuizQuestions(quizQuestions);
        unit.setPassThreshold(Math.min(METHOD_PASS_THRESHOLD, Math.max(1, quizQuestions.size())));
        return unit;
    }

    private List<LearningQuizQuestionDTO> buildMethodQuiz(MethodInfo method,
                                                          String filePath,
                                                          List<String> moduleFiles) {
        List<LearningQuizQuestionDTO> questions = new ArrayList<>();
        questions.add(buildParameterCountQuestion(method));
        questions.add(buildStartLineQuestion(method));
        questions.add(buildFileLocationQuestion(method, filePath, moduleFiles));
        return questions;
    }

    private LearningQuizQuestionDTO buildParameterCountQuestion(MethodInfo method) {
        int correct = Math.max(0, method.getParameterCount());
        return createQuizQuestion(
                "q-params-" + sanitizeId(method.getName()),
                "方法 `" + method.getName() + "` 声明了几个参数？",
                numericOptions(correct, 0),
                String.valueOf(correct),
                "参数个数来自当前快照中的方法签名。",
                method.getName().hashCode());
    }

    private LearningQuizQuestionDTO buildStartLineQuestion(MethodInfo method) {
        int startLine = Math.max(1, method.getStartLine());
        List<String> options = new ArrayList<>();
        options.add(String.valueOf(startLine));
        options.add(String.valueOf(Math.max(1, startLine - 3)));
        options.add(String.valueOf(startLine + 3));
        options.add(String.valueOf(startLine + 6));
        return createQuizQuestion(
                "q-line-" + sanitizeId(method.getName()),
                "在当前快照中，方法 `" + method.getName() + "` 从哪一行开始？",
                options,
                String.valueOf(startLine),
                "行号来自方法解析结果，可在代码中快速定位验证。",
                method.getName().hashCode() + 17);
    }

    private LearningQuizQuestionDTO buildFileLocationQuestion(MethodInfo method,
                                                              String filePath,
                                                              List<String> moduleFiles) {
        List<String> options = new ArrayList<>();
        options.add(filePath);
        if (moduleFiles != null) {
            for (String path : moduleFiles) {
                if (path != null && !path.isBlank() && !path.equals(filePath)) {
                    options.add(path);
                }
            }
        }
        options.add("README.md");
        options.add("package.json");

        return createQuizQuestion(
                "q-file-" + sanitizeId(method.getName()),
                "方法 `" + method.getName() + "` 当前位于哪个文件？",
                options,
                filePath,
                "方法级学习单元要求先建立“方法-文件-模块”的定位关系。",
                method.getName().hashCode() + 31);
    }

    private LearningQuizQuestionDTO createQuizQuestion(String questionId,
                                                       String question,
                                                       List<String> rawOptions,
                                                       String correctOption,
                                                       String explanation,
                                                       int shuffleSeed) {
        List<String> options = normalizeOptions(rawOptions, correctOption);
        List<String> shuffled = deterministicShuffle(options, shuffleSeed);
        int correctIndex = shuffled.indexOf(correctOption);
        if (correctIndex < 0) {
            correctIndex = 0;
            shuffled.set(0, correctOption);
        }

        LearningQuizQuestionDTO questionDTO = new LearningQuizQuestionDTO();
        questionDTO.setQuestionId(questionId);
        questionDTO.setQuestion(question);
        questionDTO.setOptions(shuffled);
        questionDTO.setCorrectOptionIndex(correctIndex);
        questionDTO.setExplanation(explanation);
        return questionDTO;
    }

    private List<String> normalizeOptions(List<String> rawOptions, String correctOption) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (correctOption != null && !correctOption.isBlank()) {
            unique.add(correctOption);
        }
        if (rawOptions != null) {
            for (String option : rawOptions) {
                if (option != null && !option.isBlank()) {
                    unique.add(option);
                }
            }
        }

        List<String> options = new ArrayList<>(unique);
        if (options.size() <= 1) {
            options.add("无法判断");
        }
        if (options.size() > QUIZ_OPTIONS_PER_QUESTION) {
            List<String> trimmed = new ArrayList<>();
            trimmed.add(correctOption);
            for (String option : options) {
                if (trimmed.size() >= QUIZ_OPTIONS_PER_QUESTION) {
                    break;
                }
                if (!option.equals(correctOption)) {
                    trimmed.add(option);
                }
            }
            options = trimmed;
        }
        return options;
    }

    private List<String> deterministicShuffle(List<String> options, int seed) {
        List<String> shuffled = new ArrayList<>(options);
        if (shuffled.size() <= 1) {
            return shuffled;
        }

        for (int i = 0; i < shuffled.size(); i++) {
            int swapIndex = Math.floorMod(seed + i * 31, shuffled.size());
            String temp = shuffled.get(i);
            shuffled.set(i, shuffled.get(swapIndex));
            shuffled.set(swapIndex, temp);
        }
        return shuffled;
    }

    private List<String> numericOptions(int correctValue, int minValue) {
        LinkedHashSet<Integer> options = new LinkedHashSet<>();
        options.add(Math.max(minValue, correctValue));
        options.add(Math.max(minValue, correctValue + 1));
        options.add(Math.max(minValue, correctValue + 2));
        options.add(Math.max(minValue, correctValue - 1));

        int cursor = correctValue + 3;
        while (options.size() < QUIZ_OPTIONS_PER_QUESTION) {
            options.add(Math.max(minValue, cursor++));
        }

        return options.stream()
                .limit(QUIZ_OPTIONS_PER_QUESTION)
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    private boolean isMethodCandidate(MethodInfo method) {
        if (method == null || method.getName() == null || method.getName().isBlank()) {
            return false;
        }
        String lowerName = method.getName().toLowerCase(Locale.ROOT);
        if (Set.of("if", "for", "while", "switch", "catch").contains(lowerName)) {
            return false;
        }
        return methodSpan(method) >= 2 || method.getParameterCount() > 0;
    }

    private int scoreMethod(MethodInfo method) {
        int span = methodSpan(method);
        int parameterWeight = Math.max(0, method.getParameterCount()) * 3;
        int signatureWeight = method.getSignature() != null && method.getSignature().contains("throws") ? 2 : 0;
        return span + parameterWeight + signatureWeight;
    }

    private int methodSpan(MethodInfo method) {
        if (method == null) {
            return 1;
        }
        int start = Math.max(1, method.getStartLine());
        int end = Math.max(start, method.getEndLine());
        return Math.max(1, end - start + 1);
    }

    private String detectLanguageByPath(String filePath) {
        if (filePath == null || !filePath.contains(".")) {
            return "plaintext";
        }
        String ext = filePath.substring(filePath.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "jsx", "tsx" -> "jsx";
            case "py" -> "python";
            case "java" -> "java";
            case "go" -> "go";
            case "rs" -> "rust";
            case "c", "h" -> "c";
            case "cpp", "cc", "hpp" -> "cpp";
            default -> "plaintext";
        };
    }

    private boolean isSourceLikeFile(String filePath) {
        if (filePath == null || !filePath.contains(".")) {
            return false;
        }
        String ext = filePath.substring(filePath.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
        return Set.of(
                "java", "js", "jsx", "ts", "tsx", "py", "go", "rs", "c", "cc", "cpp", "cxx",
                "h", "hpp", "cs", "php", "rb", "kt", "swift", "m", "mm", "scala", "sh", "bash", "sql")
                .contains(ext);
    }

    private List<String> buildEvidence(ModuleStat module, List<KeyCommitDTO> keyCommits) {
        List<String> evidence = new ArrayList<>();
        evidence.add("由共变更聚类生成：聚合了 " + module.fileCount + " 个高关联文件。");
        evidence.add("核心文件累计 " + module.hotScore + " 次变更，属于项目演进热点。");

        if (module.avgCoChange > 0) {
            evidence.add(String.format(Locale.ROOT, "模块内文件平均共变更强度 %.1f，说明跨文件联动明显。", module.avgCoChange));
        }

        if (!keyCommits.isEmpty()) {
            KeyCommitDTO maxTouch = keyCommits.stream()
                    .max(Comparator.comparingInt(c -> c.getTouchedFiles() == null ? 0 : c.getTouchedFiles()))
                    .orElse(null);
            if (maxTouch != null) {
                evidence.add("关键提交中最多一次覆盖 " + maxTouch.getTouchedFiles() + " 个核心文件（" + maxTouch.getShortHash() + "）。");
            }
        }

        if (keyCommits.size() >= 3) {
            evidence.add("关键提交覆盖‘引入-转折-稳定’三个阶段，便于形成完整演进认知。");
        }
        return evidence;
    }

    private List<String> buildLearningSteps(ModuleStat module,
                                            List<KeyCommitDTO> keyCommits,
                                            List<LearningMethodUnitDTO> methodUnits) {
        List<String> steps = new ArrayList<>();
        String firstFile = module.topFiles.isEmpty() ? "README.md" : module.topFiles.get(0);
        steps.add("先阅读 `" + firstFile + "`，写下模块输入、输出和边界。");
        if (methodUnits != null && !methodUnits.isEmpty()) {
            steps.add("进入方法级掌握单元，按顺序学习并完成每个方法的小测验。");
        } else {
            steps.add("当前任务未提取到可解析方法（多为文档/配置文件），建议切换到包含源码文件的任务继续方法级学习。");
        }

        for (KeyCommitDTO commit : keyCommits) {
            String hash = commit.getShortHash() == null ? "-" : commit.getShortHash();
            String message = truncate(commit.getCommitMessage(), 48);
            String phase = commit.getPhase() == null ? "关键提交" : commit.getPhase();
            steps.add("解剖 " + phase + " 提交 `" + hash + "`：`" + message + "`，回答‘为什么改’。");
        }

        steps.add("在时间线中对比核心文件的前后变化，画出调用链或数据流。");
        return steps;
    }

    private List<String> buildCheckpoints(ModuleStat module, List<LearningMethodUnitDTO> methodUnits) {
        List<String> checkpoints = new ArrayList<>();
        if (methodUnits != null && !methodUnits.isEmpty()) {
            checkpoints.add("至少通过 1 个方法单元测验，并能解释该方法的职责与边界条件。");
        }
        checkpoints.add("能用 3 句话解释模块 `" + module.displayName + "` 的职责。");
        checkpoints.add("能指出该模块最近一次‘转折型’变化的原因与影响。");
        checkpoints.add("能说明该模块与上下游模块的关键依赖关系。");
        return checkpoints;
    }

    private String resolveDifficulty(int hotScore, int fileCount) {
        if (hotScore >= 120 || fileCount >= 16) {
            return "advanced";
        }
        if (hotScore >= 60 || fileCount >= 8) {
            return "intermediate";
        }
        return "beginner";
    }

    private int estimateMinutes(ModuleStat module, int methodUnitCount) {
        int minutes = 12 + module.topFiles.size() * 5 + module.fileCount / 2 + methodUnitCount * 6;
        return Math.max(15, Math.min(75, minutes));
    }

    private LearningPlanDTO buildEmptyPlan(Repository repo) {
        LearningMissionDTO mission = new LearningMissionDTO();
        mission.setMissionId("mission-bootstrap");
        mission.setTitle("初始化学习任务");
        mission.setModule("root");
        mission.setObjective("仓库提交量较少，先从入口和 README 建立整体认知。");
        mission.setImportanceReason("当前仓库历史数据不足，建议先补充分析深度。");
        mission.setDifficulty("beginner");
        mission.setEstimatedMinutes(15);
        mission.setHotScore(0);
        mission.setFilePaths(List.of("README.md"));
        mission.setRecommendationEvidence(List.of("当前缺少足够提交历史，暂时无法生成高置信度关键提交。"));
        mission.setKeyCommits(List.of());
        mission.setMethodUnits(List.of());
        mission.setLearningSteps(List.of(
                "阅读 README，确认项目目标和目录结构。",
                "找到入口文件并运行一次核心流程。",
                "补充分析更多历史后再开启任务模式。"));
        mission.setCheckpoints(List.of(
                "能说清项目启动方式。",
                "能指出入口文件位置。",
                "能描述项目的核心业务目标。"));

        LearningPlanDTO plan = new LearningPlanDTO();
        plan.setRepoId(repo.getId());
        plan.setRepoName(repo.getName());
        plan.setTotalMissions(1);
        plan.setEstimatedTotalMinutes(15);
        plan.setMissions(List.of(mission));
        plan.setGlobalSuggestions(List.of("建议在分析页点击‘拉取更多历史’以获得更准确的学习任务。"));
        return plan;
    }

    private double computeImpactScore(KeyCommitCandidate candidate) {
        int churn = candidate.additions + candidate.deletions;
        int typeDiversity = candidate.changeTypes.size();
        return candidate.touchedFiles * 20.0 + Math.min(churn, 400) * 0.08 + typeDiversity * 4.0;
    }

    private List<String> splitList(String source, String delimiterRegex) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        return Arrays.stream(source.split(delimiterRegex))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private String inferModuleKey(List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return "root";
        }

        List<String[]> dirTokensList = new ArrayList<>();
        for (String path : filePaths) {
            String normalized = path.replace("\\", "/");
            String[] tokens = normalized.split("/");
            if (tokens.length <= 1) {
                continue;
            }
            dirTokensList.add(Arrays.copyOf(tokens, tokens.length - 1));
        }

        if (dirTokensList.isEmpty()) {
            return extractModuleKey(filePaths.get(0));
        }

        List<String> prefix = new ArrayList<>(Arrays.asList(dirTokensList.get(0)));
        for (int i = 1; i < dirTokensList.size(); i++) {
            String[] current = dirTokensList.get(i);
            int common = 0;
            while (common < prefix.size() && common < current.length && prefix.get(common).equals(current[common])) {
                common++;
            }
            prefix = new ArrayList<>(prefix.subList(0, common));
            if (prefix.isEmpty()) {
                break;
            }
        }

        if (prefix.isEmpty()) {
            return extractModuleKey(filePaths.get(0));
        }
        if (prefix.size() >= 2) {
            return prefix.get(0) + "/" + prefix.get(1);
        }
        return prefix.get(0);
    }

    private String extractModuleKey(String filePath) {
        String normalized = filePath.replace("\\", "/");
        String[] parts = normalized.split("/");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        if (tokens.isEmpty()) {
            return "root";
        }
        if (tokens.size() == 1) {
            return "root";
        }

        String first = tokens.get(0).toLowerCase(Locale.ROOT);
        if (List.of("src", "app", "lib", "packages", "modules").contains(first) && tokens.size() >= 2) {
            return tokens.get(0) + "/" + tokens.get(1);
        }
        return tokens.get(0);
    }

    private String buildModuleDisplayName(String moduleKey, int fallbackIndex) {
        if (moduleKey == null || moduleKey.isBlank()) {
            return "模块 " + fallbackIndex;
        }
        if ("root".equals(moduleKey)) {
            return "工程骨架";
        }
        return moduleKey.replace("/", " / ");
    }

    private String sanitizeId(String text) {
        if (text == null || text.isBlank()) {
            return "module";
        }
        return text.replace("\\", "-")
                .replace("/", "-")
                .replace(" ", "-")
                .toLowerCase(Locale.ROOT);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static class FileHotStat {
        private final String filePath;
        private final int modifyCount;

        private FileHotStat(String filePath, int modifyCount) {
            this.filePath = filePath;
            this.modifyCount = modifyCount;
        }
    }

    private static class ModuleStat {
        private String moduleKey;
        private String displayName;
        private int hotScore;
        private int fileCount;
        private double avgCoChange;
        private List<String> topFiles;
    }

    private static class KeyCommitCandidate {
        private Long commitId;
        private Integer commitOrder;
        private String shortHash;
        private String commitMessage;
        private String authorName;
        private String commitTime;
        private int touchedFiles;
        private int additions;
        private int deletions;
        private List<String> hitFiles = List.of();
        private List<String> changeTypes = List.of();
        private double impactScore;
    }
}
