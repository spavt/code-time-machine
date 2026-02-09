package com.codetimemachine.dto;

import lombok.Data;

import java.util.List;

@Data
public class LearningPlanDTO {
    private Long repoId;
    private String repoName;
    private Integer totalMissions;
    private Integer estimatedTotalMinutes;
    private List<LearningMissionDTO> missions;
    private List<String> globalSuggestions;
}
