package com.codetimemachine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件变更
 */
@Data
@TableName("file_change")
public class FileChange {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 提交ID */
    private Long commitId;
    
    /** 仓库ID */
    private Long repoId;
    
    /** 文件路径 */
    private String filePath;
    
    /** 文件名 */
    private String fileName;
    
    /** 文件扩展名 */
    private String fileExtension;
    
    /** 变更类型: ADD, MODIFY, DELETE, RENAME, COPY */
    private String changeType;
    
    /** 原路径(重命名时) */
    private String oldPath;
    
    /** 新增行数 */
    private Integer additions;
    
    /** 删除行数 */
    private Integer deletions;
    
    /** Diff内容 */
    private String diffText;
    
    /** 文件完整内容 */
    private String fileContent;
    
    /** 内容哈希 */
    private String contentHash;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
