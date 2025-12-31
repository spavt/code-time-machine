package com.codetimemachine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_history")
public class ChatHistory {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String sessionId;
    
    private Long repoId;
    
    private Long commitId;
    
    private String filePath;
    
    private String role;
    
    private String content;
    
    private Integer tokensUsed;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
