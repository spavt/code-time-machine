package com.codetimemachine.service;

import com.codetimemachine.dto.MethodInfo;
import java.util.List;

public interface MethodParserService {

    List<MethodInfo> parseMethods(String code, String language);

    String extractMethod(String code, String methodSignature, String language);
}
