package com.codetimemachine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_analysis")
public class AiAnalysis {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long commitId;
    
    private Long repoId;
    
    private String analysisType;
    
    private String summary;
    
    private String purpose;
    
    private String impact;
    
    private String technicalDetails;
    
    private String suggestions;
    
    private String changeCategory;
    
    private Integer complexityScore;
    
    private Integer importanceScore;
    
    private String promptHash;
    
    private String modelUsed;
    
    private Integer tokensUsed;
    
    private Integer responseTime;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
