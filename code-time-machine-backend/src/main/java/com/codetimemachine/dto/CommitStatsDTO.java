package com.codetimemachine.dto;

import lombok.Data;

/**
 * 提交统计信息DTO
 */
@Data
public class CommitStatsDTO {

    /** 新增行数 */
    private Integer additions;

    /** 删除行数 */
    private Integer deletions;

    /** 变更文件数 */
    private Integer filesChanged;

    /** 是否成功计算 */
    private Boolean calculated;

    public static CommitStatsDTO empty() {
        CommitStatsDTO dto = new CommitStatsDTO();
        dto.setAdditions(0);
        dto.setDeletions(0);
        dto.setFilesChanged(0);
        dto.setCalculated(false);
        return dto;
    }

    public static CommitStatsDTO of(int additions, int deletions, int filesChanged) {
        CommitStatsDTO dto = new CommitStatsDTO();
        dto.setAdditions(additions);
        dto.setDeletions(deletions);
        dto.setFilesChanged(filesChanged);
        dto.setCalculated(true);
        return dto;
    }
}
