package com.codetimemachine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对话记录
 */
@Data
@TableName("chat_history")
public class ChatHistory {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 会话ID */
    private String sessionId;
    
    /** 关联仓库ID */
    private Long repoId;
    
    /** 关联提交ID */
    private Long commitId;
    
    /** 关联文件路径 */
    private String filePath;
    
    /** 角色: user, assistant, system */
    private String role;
    
    /** 消息内容 */
    private String content;
    
    /** 消耗Token数 */
    private Integer tokensUsed;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
