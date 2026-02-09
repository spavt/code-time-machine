package com.codetimemachine.dto;

import lombok.Data;

import java.util.List;

@Data
public class LearningQuizQuestionDTO {
    private String questionId;
    private String question;
    private List<String> options;
    private Integer correctOptionIndex;
    private String explanation;
}
