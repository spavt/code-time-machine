package com.codetimemachine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("repository")
public class Repository {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String url;

    private String localPath;

    private String description;

    private String defaultBranch;

    private String language;

    private Integer stars;

    private Integer totalCommits;

    private Integer totalFiles;

    private Long repoSize;

    private Integer status;

    private Integer analyzeProgress;

    private Integer analyzeDepth;

    private LocalDateTime analyzeSince;

    private String analyzePathFilters;

    @TableField(exist = false)
    private Boolean canLoadMore;

    private LocalDateTime lastAnalyzedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
