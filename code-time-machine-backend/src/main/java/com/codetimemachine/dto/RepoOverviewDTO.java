package com.codetimemachine.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 仓库概览DTO
 */
@Data
public class RepoOverviewDTO {
    private Integer totalCommits;
    private Integer totalAuthors;
    private Long totalAdditions;
    private Long totalDeletions;
    private String firstCommit;
    private String lastCommit;
    private List<Map<String, Object>> topContributors;
}
