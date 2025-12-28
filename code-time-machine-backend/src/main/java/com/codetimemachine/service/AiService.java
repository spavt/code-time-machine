package com.codetimemachine.service;

import com.codetimemachine.entity.AiAnalysis;
import com.codetimemachine.entity.CommitRecord;
import reactor.core.publisher.Flux;

/**
 * AI服务
 */
public interface AiService {

    /**
     * 分析提交
     */
    AiAnalysis analyzeCommit(CommitRecord commit, String diffSummary);

    /**
     * 回答问题
     */
    String askQuestion(String question, String context);

    /**
     * 回答问题（流式输出）
     * 
     * @param question 用户问题
     * @param context  代码上下文
     * @return 流式响应
     */
    Flux<String> askQuestionStream(String question, String context);

    /**
     * 生成学习路径
     */
    String generateLearningPath(String projectMetadata);
}
