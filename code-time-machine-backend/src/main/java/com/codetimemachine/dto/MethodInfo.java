package com.codetimemachine.dto;

import lombok.Data;

@Data
public class MethodInfo {

    private String name;

    private String signature;

    private int startLine;

    private int endLine;

    private String content;

    private String className;

    private int parameterCount;
}
