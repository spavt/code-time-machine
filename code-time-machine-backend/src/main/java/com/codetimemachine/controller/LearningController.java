package com.codetimemachine.controller;

import com.codetimemachine.common.Result;
import com.codetimemachine.dto.LearningPlanDTO;
import com.codetimemachine.service.LearningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {

    private final LearningService learningService;

    @GetMapping("/plan/{repoId}")
    public Result<LearningPlanDTO> getLearningPlan(@PathVariable Long repoId) {
        return Result.success(learningService.buildLearningPlan(repoId));
    }
}
