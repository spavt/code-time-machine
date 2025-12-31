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

@Slf4j
@Service
public class MethodParserServiceImpl implements MethodParserService {

    private static final Pattern JS_FUNCTION_PATTERN = Pattern.compile(
            "(?:(?:export\\s+)?(?:async\\s+)?function\\s+(\\w+)|" +
                    "(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s+)?(?:function|\\([^)]*\\)\\s*=>)|" +
                    "(\\w+)\\s*\\([^)]*\\)\\s*\\{)"
    );

    private static final Pattern JS_METHOD_PATTERN = Pattern.compile(
            "(?:(?:public|private|protected|static|async)\\s+)*" +
                    "(\\w+)\\s*\\([^)]*\\)\\s*(?::\\s*[\\w<>\\[\\]|&\\s]+)?\\s*\\{");

    private static final Pattern PYTHON_FUNCTION_PATTERN = Pattern.compile(
            "^[ \\t]*(?:async\\s+)?def\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:->\\s*[^:]+)?\\s*:",
            Pattern.MULTILINE);

    private static final Pattern PYTHON_METHOD_PATTERN = Pattern.compile(
            "^[ \\t]+(?:async\\s+)?def\\s+(\\w+)\\s*\\(\\s*(?:self|cls)[^)]*\\)\\s*(?:->\\s*[^:]+)?\\s*:",
            Pattern.MULTILINE);

    private static final Pattern GO_FUNCTION_PATTERN = Pattern.compile(
            "^func\\s+(?:\\([^)]+\\)\\s+)?(\\w+)\\s*\\([^)]*\\)",
            Pattern.MULTILINE);

    private static final Pattern RUST_FUNCTION_PATTERN = Pattern.compile(
            "^\\s*(?:pub\\s+)?(?:async\\s+)?fn\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*\\([^)]*\\)",
            Pattern.MULTILINE);

    private static final Pattern C_FUNCTION_PATTERN = Pattern.compile(
            "^[\\w\\s*&]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:const)?\\s*\\{",
            Pattern.MULTILINE);

    private final JavaParser javaParser = new JavaParser();

    @Override
    public List<MethodInfo> parseMethods(String code, String language) {
        if (code == null || code.isEmpty()) {
            return new ArrayList<>();
        }

        return switch (language.toLowerCase()) {
            case "java" -> parseJavaMethods(code);
            case "javascript", "js", "jsx" -> parseJavaScriptMethods(code);
            case "typescript", "ts", "tsx" -> parseTypeScriptMethods(code);
            case "python", "py" -> parsePythonMethods(code);
            case "go", "golang" -> parseGoMethods(code);
            case "rust", "rs" -> parseRustMethods(code);
            case "c", "cpp", "cc", "cxx", "h", "hpp" -> parseCMethods(code);
            default -> parseGenericMethods(code);
        };
    }

    @Override
    public String extractMethod(String code, String methodSignature, String language) {
        List<MethodInfo> methods = parseMethods(code, language);

        for (MethodInfo method : methods) {
            if (method.getSignature().contains(methodSignature) ||
                    method.getName().equals(methodSignature)) {
                return method.getContent();
            }
        }

        return null;
    }

    private List<MethodInfo> parseJavaMethods(String code) {
        List<MethodInfo> methods = new ArrayList<>();

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(code);

            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();

                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                    String className = classDecl.getNameAsString();

                    classDecl.findAll(MethodDeclaration.class).forEach(methodDecl -> {
                        MethodInfo info = new MethodInfo();
                        info.setName(methodDecl.getNameAsString());
                        info.setSignature(methodDecl.getDeclarationAsString(false, false, false));
                        info.setClassName(className);
                        info.setParameterCount(methodDecl.getParameters().size());

                        if (methodDecl.getBegin().isPresent()) {
                            info.setStartLine(methodDecl.getBegin().get().line);
                        }
                        if (methodDecl.getEnd().isPresent()) {
                            info.setEndLine(methodDecl.getEnd().get().line);
                        }

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

    private List<MethodInfo> parseJavaScriptMethods(String code) {
        return parseWithRegex(code, JS_FUNCTION_PATTERN, JS_METHOD_PATTERN);
    }

    private List<MethodInfo> parseTypeScriptMethods(String code) {
        return parseWithRegex(code, JS_FUNCTION_PATTERN, JS_METHOD_PATTERN);
    }

    private List<MethodInfo> parsePythonMethods(String code) {
        List<MethodInfo> methods = new ArrayList<>();
        String[] lines = code.split("\n");

        Pattern[] patterns = { PYTHON_FUNCTION_PATTERN, PYTHON_METHOD_PATTERN };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(code);
            while (matcher.find()) {
                String methodName = matcher.group(1);
                if (methodName != null) {
                    MethodInfo info = new MethodInfo();
                    info.setName(methodName);

                    int startPos = matcher.start();
                    int startLine = countNewlines(code.substring(0, startPos)) + 1;
                    info.setStartLine(startLine);

                    int endLine = findPythonMethodEndLine(lines, startLine);
                    info.setEndLine(endLine);

                    info.setSignature(matcher.group().trim());
                    info.setContent(extractMethodContent(lines, startLine, endLine));

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

    private int findPythonMethodEndLine(String[] lines, int startLine) {
        if (startLine < 1 || startLine > lines.length) {
            return startLine;
        }

        String defLine = lines[startLine - 1];
        int defIndent = getIndentLevel(defLine);
        int bodyIndent = -1;

        for (int i = startLine; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int currentIndent = getIndentLevel(line);

            if (bodyIndent < 0) {
                bodyIndent = currentIndent;
                continue;
            }

            if (currentIndent <= defIndent) {
                return i;
            }
        }

        return lines.length;
    }

    private int getIndentLevel(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ')
                indent++;
            else if (c == '\t')
                indent += 4;
            else
                break;
        }
        return indent;
    }

    private List<MethodInfo> parseGoMethods(String code) {
        return parseWithRegex(code, GO_FUNCTION_PATTERN);
    }

    private List<MethodInfo> parseRustMethods(String code) {
        return parseWithRegex(code, RUST_FUNCTION_PATTERN);
    }

    private List<MethodInfo> parseCMethods(String code) {
        return parseWithRegex(code, C_FUNCTION_PATTERN);
    }

    private List<MethodInfo> parseGenericMethods(String code) {
        Pattern genericPattern = Pattern.compile(
                "(?:function|def|fn|func)\\s+(\\w+)\\s*\\([^)]*\\)");
        return parseWithRegex(code, genericPattern, null);
    }

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

                    int startPos = matcher.start();
                    int startLine = countNewlines(code.substring(0, startPos)) + 1;
                    info.setStartLine(startLine);

                    int endLine = findMethodEndLine(code, matcher.end());
                    info.setEndLine(endLine);

                    info.setSignature(matcher.group().trim());
                    info.setContent(extractMethodContent(lines, startLine, endLine));

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
