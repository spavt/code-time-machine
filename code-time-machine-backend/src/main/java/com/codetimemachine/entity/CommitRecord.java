package com.codetimemachine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 提交记录
 */
@Data
@TableName("commit_record")
public class CommitRecord {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 仓库ID */
    private Long repoId;
    
    /** Commit SHA哈希值 */
    private String commitHash;
    
    /** 短哈希值 */
    private String shortHash;
    
    /** 父Commit哈希 */
    private String parentHash;
    
    /** 作者名称 */
    private String authorName;
    
    /** 作者邮箱 */
    private String authorEmail;
    
    /** 提交信息 */
    private String commitMessage;
    
    /** 提交时间 */
    private LocalDateTime commitTime;
    
    /** 新增行数 */
    private Integer additions;
    
    /** 删除行数 */
    private Integer deletions;
    
    /** 变更文件数 */
    private Integer filesChanged;
    
    /** 是否是Merge提交 */
    private Integer isMerge;
    
    /** 提交顺序(从早到晚) */
    private Integer commitOrder;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
