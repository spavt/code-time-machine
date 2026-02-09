package com.codetimemachine.dto;

import lombok.Data;

import java.util.List;

@Data
public class LearningMethodUnitDTO {
    private String unitId;
    private String filePath;
    private String methodName;
    private String methodSignature;
    private String className;
    private Integer startLine;
    private Integer endLine;
    private Integer parameterCount;
    private String objective;
    private String importanceReason;
    private Integer estimatedMinutes;
    private Integer passThreshold;
    private List<String> learningHints;
    private List<LearningQuizQuestionDTO> quizQuestions;
}
