package com.codetimemachine.dto;

import lombok.Data;

import java.util.List;

@Data
public class KeyCommitDTO {
    private Long commitId;
    private Integer commitOrder;
    private String shortHash;
    private String commitMessage;
    private String authorName;
    private String commitTime;
    private String phase;
    private String focusReason;
    private Integer touchedFiles;
    private Integer additions;
    private Integer deletions;
    private List<String> hitFiles;
    private List<String> changeTypes;
}
