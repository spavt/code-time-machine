package com.codetimemachine.dto;

import lombok.Data;

@Data
public class CommitStatsDTO {

    private Integer additions;

    private Integer deletions;

    private Integer filesChanged;

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
