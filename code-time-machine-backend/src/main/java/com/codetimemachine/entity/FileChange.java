package com.codetimemachine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("file_change")
public class FileChange {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long commitId;
    
    private Long repoId;
    
    private String filePath;
    
    private String fileName;
    
    private String fileExtension;
    
    private String changeType;
    
    private String oldPath;
    
    private Integer additions;
    
    private Integer deletions;
    
    private String diffText;
    
    private String fileContent;
    
    private String contentHash;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
