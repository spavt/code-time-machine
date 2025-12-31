package com.codetimemachine.dto;

import lombok.Data;
import java.util.List;

@Data
public class FileTimelineDTO {
    private Long repoId;
    private String filePath;
    private String fileName;
    private List<TimelineCommitDTO> commits;

    @Data
    public static class TimelineCommitDTO {
        private Long id;
        private String commitHash;
        private String shortHash;
        private String commitMessage;
        private String authorName;
        private String commitTime;
        private Integer commitOrder;
        private String changeType;
        private Integer additions;
        private Integer deletions;
        private String aiSummary;
        private String changeCategory;
        private String content;
    }
}
