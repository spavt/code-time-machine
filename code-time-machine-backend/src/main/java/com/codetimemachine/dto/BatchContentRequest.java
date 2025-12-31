package com.codetimemachine.dto;

import lombok.Data;
import java.util.List;

/**
 * 批量获取文件内容请求
 */
@Data
public class BatchContentRequest {
    private Long repoId;
    private List<Long> commitIds;
    private String filePath;
}
