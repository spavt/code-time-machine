package com.codetimemachine.service;

import com.codetimemachine.dto.MethodInfo;
import java.util.List;

/**
 * 方法解析服务接口
 */
public interface MethodParserService {

    /**
     * 解析代码中的方法列表
     * 
     * @param code     源代码
     * @param language 语言类型 (java, javascript, typescript, etc.)
     * @return 方法列表
     */
    List<MethodInfo> parseMethods(String code, String language);

    /**
     * 根据方法签名提取方法代码
     * 
     * @param code            完整源代码
     * @param methodSignature 方法签名
     * @param language        语言类型
     * @return 方法代码，未找到返回null
     */
    String extractMethod(String code, String methodSignature, String language);
}
