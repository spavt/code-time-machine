package com.codetimemachine.dto;

import lombok.Data;

/**
 * 方法信息DTO
 */
@Data
public class MethodInfo {

    /**
     * 方法名
     */
    private String name;

    /**
     * 完整签名（如：public void run(String... args)）
     */
    private String signature;

    /**
     * 起始行
     */
    private int startLine;

    /**
     * 结束行
     */
    private int endLine;

    /**
     * 方法体代码
     */
    private String content;

    /**
     * 所属类名（可选）
     */
    private String className;

    /**
     * 参数数量
     */
    private int parameterCount;
}
