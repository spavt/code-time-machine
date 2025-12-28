package com.codetimemachine.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 仓库分析选项 DTO
 * 用于控制克隆深度、时间范围、路径过滤等
 */
@Data
public class AnalyzeOptionsDTO {

    /**
     * 分析深度（提交数量）
     * -1 表示全部历史
     * 推荐值: 100(快速), 500(推荐), 2000(深度)
     */
    private Integer depth = 500;

    /**
     * 开始时间 - 只分析此时间之后的提交
     */
    private LocalDateTime since;

    /**
     * 结束时间 - 只分析此时间之前的提交
     */
    private LocalDateTime until;

    /**
     * 路径过滤器列表 - 只分析匹配的文件/目录
     * 例如: ["src/main/java/", "*.java"]
     */
    private List<String> pathFilters;

    /**
     * 是否使用浅克隆
     * true: 只克隆指定深度的历史（省空间省时间）
     * false: 克隆完整仓库（后续可深挖）
     */
    private Boolean shallow = true;

    /**
     * 是否只克隆默认分支
     */
    private Boolean singleBranch = true;

    /**
     * 获取实际的克隆深度
     * 如果 depth=-1 且 shallow=true，返回一个较大的值
     */
    public int getEffectiveCloneDepth() {
        if (depth == null || depth <= 0) {
            return shallow ? 10000 : 0; // 0 表示完整克隆
        }
        return depth;
    }

    /**
     * 获取实际的分析深度
     */
    public int getEffectiveAnalyzeDepth() {
        if (depth == null || depth <= 0) {
            return Integer.MAX_VALUE;
        }
        return depth;
    }

    /**
     * 创建默认配置（快速模式）
     */
    public static AnalyzeOptionsDTO fast() {
        AnalyzeOptionsDTO options = new AnalyzeOptionsDTO();
        options.setDepth(100);
        options.setShallow(true);
        return options;
    }

    /**
     * 创建推荐配置
     */
    public static AnalyzeOptionsDTO recommended() {
        AnalyzeOptionsDTO options = new AnalyzeOptionsDTO();
        options.setDepth(500);
        options.setShallow(true);
        return options;
    }

    /**
     * 创建深度分析配置
     */
    public static AnalyzeOptionsDTO deep() {
        AnalyzeOptionsDTO options = new AnalyzeOptionsDTO();
        options.setDepth(2000);
        options.setShallow(true);
        return options;
    }

    /**
     * 创建完整分析配置
     */
    public static AnalyzeOptionsDTO full() {
        AnalyzeOptionsDTO options = new AnalyzeOptionsDTO();
        options.setDepth(-1);
        options.setShallow(false);
        return options;
    }
}
