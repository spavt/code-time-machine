package com.codetimemachine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI分析结果
 */
@Data
@TableName("ai_analysis")
public class AiAnalysis {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 提交ID */
    private Long commitId;
    
    /** 仓库ID */
    private Long repoId;
    
    /** 分析类型: COMMIT, FILE, QUESTION */
    private String analysisType;
    
    /** 改动摘要 */
    private String summary;
    
    /** 改动目的 */
    private String purpose;
    
    /** 影响分析 */
    private String impact;
    
    /** 技术细节 */
    private String technicalDetails;
    
    /** AI建议 */
    private String suggestions;
    
    /** 变更分类: feature/bugfix/refactor/docs等 */
    private String changeCategory;
    
    /** 复杂度评分(1-10) */
    private Integer complexityScore;
    
    /** 重要性评分(1-10) */
    private Integer importanceScore;
    
    /** Prompt哈希(用于缓存) */
    private String promptHash;
    
    /** 使用的AI模型 */
    private String modelUsed;
    
    /** 消耗的Token数 */
    private Integer tokensUsed;
    
    /** 响应时间(毫秒) */
    private Integer responseTime;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
