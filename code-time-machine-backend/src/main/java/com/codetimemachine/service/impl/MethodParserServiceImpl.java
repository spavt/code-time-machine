package com.codetimemachine.service.impl;

import com.codetimemachine.dto.MethodInfo;
import com.codetimemachine.service.MethodParserService;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 方法解析服务实现
 * 支持 Java (使用 JavaParser) 和 JavaScript/TypeScript (使用正则)
 */
@Slf4j
@Service
public class MethodParserServiceImpl implements MethodParserService {

    // JavaScript/TypeScript 函数匹配正则
    private static final Pattern JS_FUNCTION_PATTERN = Pattern.compile(
            "(?:(?:export\\s+)?(?:async\\s+)?function\\s+(\\w+)|" + // function name()
                    "(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s+)?(?:function|\\([^)]*\\)\\s*=>)|" + // const name
                                                                                                          // =
                                                                                                          // function/arrow
                    "(\\w+)\\s*\\([^)]*\\)\\s*\\{)" // method shorthand in class
    );

    // TypeScript/JavaScript 方法匹配（类内部）
    private static final Pattern JS_METHOD_PATTERN = Pattern.compile(
            "(?:(?:public|private|protected|static|async)\\s+)*" +
                    "(\\w+)\\s*\\([^)]*\\)\\s*(?::\\s*[\\w<>\\[\\]|&\\s]+)?\\s*\\{");

    private final JavaParser javaParser = new JavaParser();

    @Override
    public List<MethodInfo> parseMethods(String code, String language) {
        if (code == null || code.isEmpty()) {
            return new ArrayList<>();
        }

        return switch (language.toLowerCase()) {
            case "java" -> parseJavaMethods(code);
            case "javascript", "js" -> parseJavaScriptMethods(code);
            case "typescript", "ts", "tsx" -> parseTypeScriptMethods(code);
            default -> parseGenericMethods(code);
        };
    }

    @Override
    public String extractMethod(String code, String methodSignature, String language) {
        List<MethodInfo> methods = parseMethods(code, language);

        for (MethodInfo method : methods) {
            // 匹配方法签名
            if (method.getSignature().contains(methodSignature) ||
                    method.getName().equals(methodSignature)) {
                return method.getContent();
            }
        }

        return null;
    }

    /**
     * 使用 JavaParser 解析 Java 代码
     */
    private List<MethodInfo> parseJavaMethods(String code) {
        List<MethodInfo> methods = new ArrayList<>();

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(code);

            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();

                // 遍历所有类
                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                    String className = classDecl.getNameAsString();

                    // 遍历所有方法
                    classDecl.findAll(MethodDeclaration.class).forEach(methodDecl -> {
                        MethodInfo info = new MethodInfo();
                        info.setName(methodDecl.getNameAsString());
                        info.setSignature(methodDecl.getDeclarationAsString(false, false, false));
                        info.setClassName(className);
                        info.setParameterCount(methodDecl.getParameters().size());

                        // 获取行号
                        if (methodDecl.getBegin().isPresent()) {
                            info.setStartLine(methodDecl.getBegin().get().line);
                        }
                        if (methodDecl.getEnd().isPresent()) {
                            info.setEndLine(methodDecl.getEnd().get().line);
                        }

                        // 获取方法体代码
                        info.setContent(methodDecl.toString());

                        methods.add(info);
                    });
                });
            } else {
                log.warn("JavaParser 解析失败，使用正则回退");
                return parseGenericMethods(code);
            }
        } catch (Exception e) {
            log.error("Java解析异常: {}", e.getMessage());
            return parseGenericMethods(code);
        }

        return methods;
    }

    /**
     * 使用正则解析 JavaScript 方法
     */
    private List<MethodInfo> parseJavaScriptMethods(String code) {
        return parseWithRegex(code, JS_FUNCTION_PATTERN, JS_METHOD_PATTERN);
    }

    /**
     * 使用正则解析 TypeScript 方法
     */
    private List<MethodInfo> parseTypeScriptMethods(String code) {
        return parseWithRegex(code, JS_FUNCTION_PATTERN, JS_METHOD_PATTERN);
    }

    /**
     * 通用正则解析
     */
    private List<MethodInfo> parseGenericMethods(String code) {
        // 简单的通用函数匹配
        Pattern genericPattern = Pattern.compile(
                "(?:function|def|fn|func)\\s+(\\w+)\\s*\\([^)]*\\)");
        return parseWithRegex(code, genericPattern, null);
    }

    /**
     * 使用正则表达式解析方法
     */
    private List<MethodInfo> parseWithRegex(String code, Pattern... patterns) {
        List<MethodInfo> methods = new ArrayList<>();
        String[] lines = code.split("\n");

        for (Pattern pattern : patterns) {
            if (pattern == null)
                continue;

            Matcher matcher = pattern.matcher(code);
            while (matcher.find()) {
                String foundName = null;
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    if (matcher.group(i) != null) {
                        foundName = matcher.group(i);
                        break;
                    }
                }

                if (foundName != null) {
                    final String methodName = foundName;
                    MethodInfo info = new MethodInfo();
                    info.setName(methodName);

                    // 计算行号
                    int startPos = matcher.start();
                    int startLine = countNewlines(code.substring(0, startPos)) + 1;
                    info.setStartLine(startLine);

                    // 尝试找到方法结束位置（匹配大括号）
                    int endLine = findMethodEndLine(code, matcher.end());
                    info.setEndLine(endLine);

                    // 提取方法内容
                    info.setSignature(matcher.group().trim());
                    info.setContent(extractMethodContent(lines, startLine, endLine));

                    // 避免重复
                    boolean duplicate = methods.stream()
                            .anyMatch(m -> m.getName().equals(methodName) && m.getStartLine() == startLine);
                    if (!duplicate) {
                        methods.add(info);
                    }
                }
            }
        }

        return methods;
    }

    private int countNewlines(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == '\n')
                count++;
        }
        return count;
    }

    private int findMethodEndLine(String code, int startPos) {
        int braceCount = 0;
        boolean foundFirst = false;
        int pos = startPos;
        int lineNum = countNewlines(code.substring(0, startPos)) + 1;

        while (pos < code.length()) {
            char c = code.charAt(pos);
            if (c == '\n')
                lineNum++;
            if (c == '{') {
                braceCount++;
                foundFirst = true;
            }
            if (c == '}') {
                braceCount--;
                if (foundFirst && braceCount == 0) {
                    return lineNum;
                }
            }
            pos++;
        }

        return lineNum;
    }

    private String extractMethodContent(String[] lines, int startLine, int endLine) {
        StringBuilder sb = new StringBuilder();
        for (int i = startLine - 1; i < Math.min(endLine, lines.length); i++) {
            if (i >= 0) {
                sb.append(lines[i]).append("\n");
            }
        }
        return sb.toString();
    }
}
