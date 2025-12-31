package com.codetimemachine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("commit_record")
public class CommitRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long repoId;
    
    private String commitHash;
    
    private String shortHash;
    
    private String parentHash;
    
    private String authorName;
    
    private String authorEmail;
    
    private String commitMessage;
    
    private LocalDateTime commitTime;
    
    private Integer additions;
    
    private Integer deletions;
    
    private Integer filesChanged;
    
    private Integer isMerge;
    
    private Integer commitOrder;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
