package com.codetimemachine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Blob预取请求项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrefetchRequest {

    /** 提交哈希 */
    private String commitHash;

    /** 文件路径 */
    private String filePath;
}
