package com.codetimemachine.service;

import com.codetimemachine.entity.AiAnalysis;
import com.codetimemachine.entity.CommitRecord;
import reactor.core.publisher.Flux;

public interface AiService {

    AiAnalysis analyzeCommit(CommitRecord commit, String diffSummary);

    String askQuestion(String question, String context);

    Flux<String> askQuestionStream(String question, String context);

    String generateLearningPath(String projectMetadata);
}
