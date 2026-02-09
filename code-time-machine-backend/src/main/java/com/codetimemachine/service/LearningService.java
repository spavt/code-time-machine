package com.codetimemachine.service;

import com.codetimemachine.dto.LearningPlanDTO;

public interface LearningService {
    LearningPlanDTO buildLearningPlan(Long repoId);
}
