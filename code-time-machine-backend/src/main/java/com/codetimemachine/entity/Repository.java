package com.codetimemachine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 仓库信息
 */
@Data
@TableName("repository")
public class Repository {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仓库名称 */
    private String name;

    /** Git仓库URL */
    private String url;

    /** 本地克隆路径 */
    private String localPath;

    /** 仓库描述 */
    private String description;

    /** 默认分支 */
    private String defaultBranch;

    /** 主要编程语言 */
    private String language;

    /** Stars数量 */
    private Integer stars;

    /** 总提交数 */
    private Integer totalCommits;

    /** 文件总数 */
    private Integer totalFiles;

    /** 仓库大小(字节) */
    private Long repoSize;

    /** 状态: 0-待分析 1-分析中 2-完成 3-失败 */
    private Integer status;

    /** 分析进度(0-100) */
    private Integer analyzeProgress;

    /** 分析深度（提交数量限制）-1表示全部 */
    private Integer analyzeDepth;

    /** 分析起始时间 */
    private LocalDateTime analyzeSince;

    /** 分析路径过滤（JSON数组格式） */
    private String analyzePathFilters;

    /** 是否可以加载更多历史 */
    @TableField(exist = false)
    private Boolean canLoadMore;

    /** 最后分析时间 */
    private LocalDateTime lastAnalyzedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
