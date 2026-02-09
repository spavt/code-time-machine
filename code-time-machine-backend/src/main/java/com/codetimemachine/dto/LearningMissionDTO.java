package com.codetimemachine.dto;

import lombok.Data;

import java.util.List;

@Data
public class LearningMissionDTO {
    private String missionId;
    private String title;
    private String module;
    private String objective;
    private String importanceReason;
    private String difficulty;
    private Integer estimatedMinutes;
    private Integer hotScore;
    private List<String> filePaths;
    private List<String> recommendationEvidence;
    private List<KeyCommitDTO> keyCommits;
    private List<LearningMethodUnitDTO> methodUnits;
    private List<String> learningSteps;
    private List<String> checkpoints;
}
