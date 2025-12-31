package com.codetimemachine.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchContentRequest {
    private Long repoId;
    private List<Long> commitIds;
    private String filePath;
}
