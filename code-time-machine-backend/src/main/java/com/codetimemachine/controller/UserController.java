package com.codetimemachine.controller;

import com.codetimemachine.common.Result;
import com.codetimemachine.dto.RecommendedRepoDTO;
import com.codetimemachine.entity.UserPreference;
import com.codetimemachine.service.RecommendedRepoService;
import com.codetimemachine.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserPreferenceService userPreferenceService;
    private final RecommendedRepoService recommendedRepoService;

    @GetMapping("/preference")
    public Result<UserPreference> getPreference(@RequestParam String sessionId) {
        UserPreference preference = userPreferenceService.getBySessionId(sessionId);
        return Result.success(preference);
    }

    @PutMapping("/skill-level")
    public Result<Void> saveSkillLevel(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String skillLevel = request.get("skillLevel");

        if (sessionId == null || sessionId.isEmpty()) {
            return Result.error("sessionId is required");
        }
        if (skillLevel == null || skillLevel.isEmpty()) {
            return Result.error("skillLevel is required");
        }

        // Validate skill level
        if (!List.of("beginner", "intermediate", "advanced").contains(skillLevel)) {
            return Result.error("Invalid skill level. Must be one of: beginner, intermediate, advanced");
        }

        userPreferenceService.saveSkillLevel(sessionId, skillLevel);
        return Result.success();
    }

    @GetMapping("/recommended-repos")
    public Result<List<RecommendedRepoDTO>> getRecommendedRepos(
            @RequestParam(required = false) String level) {
        if (level != null && !level.isEmpty()) {
            return Result.success(recommendedRepoService.getByLevel(level));
        }
        return Result.success(recommendedRepoService.getAll());
    }
}
