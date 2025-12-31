package com.codetimemachine.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.codetimemachine.entity.AiAnalysis;
import com.codetimemachine.entity.CommitRecord;
import com.codetimemachine.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.api-url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${app.ai.model:gpt-4o-mini}")
    private String model;

    @Value("${app.ai.max-tokens:800}")
    private int maxTokens;

    private final RestTemplate restTemplate;
    private final WebClient webClient;

    public AiServiceImpl() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);

        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    private static final String COMMIT_ANALYSIS_PROMPT = """
            你是一个代码审查专家。请分析这次Git提交，用中文回答。

            提交信息：
            - 作者: %s
            - 时间: %s
            - 消息: %s

            文件变更摘要：
            %s

            请按以下格式分析（每项不超过50字）：
            1. 摘要：这次改动做了什么
            2. 目的：为什么要这么改
            3. 影响：可能产生什么影响
            4. 分类：feature/bugfix/refactor/docs/style/test/chore/perf 之一
            5. 复杂度：1-10分
            6. 重要性：1-10分

            用JSON格式返回，字段：summary, purpose, impact, category, complexity, importance
            """;

    private static final String QUESTION_PROMPT = """
            你是一个编程助手。根据以下代码变更上下文回答用户问题。

            上下文信息可能包含：
            - 文件路径和提交信息
            - Diff 格式的代码变更（以 + 开头表示新增行，以 - 开头表示删除行）
            - 当前版本的代码片段

            代码上下文：
            ```
            %s
            ```

            用户问题：%s

            请用中文简洁回答。如果上下文包含 diff，请结合变更内容分析影响；如果需要可以使用代码示例。
            """;

    @Override
    public AiAnalysis analyzeCommit(CommitRecord commit, String diffSummary) {
        AiAnalysis analysis = new AiAnalysis();

        if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("sk-xxx")) {
            log.warn("AI API Key未配置，使用模拟数据");
            return mockAnalysis(commit);
        }

        try {
            String prompt = String.format(COMMIT_ANALYSIS_PROMPT,
                    commit.getAuthorName(),
                    commit.getCommitTime(),
                    commit.getCommitMessage(),
                    truncate(diffSummary, 2000));

            String response = callAiApi(prompt);

            JSONObject json = parseJsonFromResponse(response);
            if (json != null) {
                analysis.setSummary(json.getString("summary"));
                analysis.setPurpose(json.getString("purpose"));
                analysis.setImpact(json.getString("impact"));
                analysis.setChangeCategory(json.getString("category"));
                analysis.setComplexityScore(json.getInteger("complexity"));
                analysis.setImportanceScore(json.getInteger("importance"));
            }

            analysis.setModelUsed(model);

        } catch (Exception e) {
            log.error("AI分析失败: {}", e.getMessage());
            return mockAnalysis(commit);
        }

        return analysis;
    }

    @Override
    public String askQuestion(String question, String context) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("sk-xxx")) {
            log.warn("AI API Key未配置，使用模拟回答");
            return mockAnswer(question, context);
        }

        try {
            String codeContext = (context != null && !context.trim().isEmpty())
                    ? context
                    : "（当前没有选中的代码）";

            String prompt = String.format(QUESTION_PROMPT,
                    truncate(codeContext, 6000),
                    question);

            return callAiApi(prompt);
        } catch (Exception e) {
            log.error("AI问答失败: {}", e.getMessage());
            return "抱歉，AI服务暂时不可用。请稍后再试。";
        }
    }

    @Override
    public Flux<String> askQuestionStream(String question, String context) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("sk-xxx")) {
            log.warn("AI API Key未配置，使用模拟流式回答");
            return mockStreamAnswer(question, context);
        }

        try {
            String codeContext = (context != null && !context.trim().isEmpty())
                    ? context
                    : "（当前没有选中的代码）";

            String prompt = String.format(QUESTION_PROMPT,
                    truncate(codeContext, 6000),
                    question);

            return callAiApiStream(prompt);
        } catch (Exception e) {
            log.error("AI流式问答失败: {}", e.getMessage());
            return Flux.just("抱歉，AI服务暂时不可用。请稍后再试。");
        }
    }

    private Flux<String> callAiApiStream(String prompt) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("stream", true);

        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);

        return webClient.post()
                .uri(apiUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .bodyValue(requestBody.toString())
                .retrieve()
                .bodyToFlux(String.class)
                .filter(data -> !data.equals("[DONE]") && !data.trim().isEmpty())
                .map(this::extractDeltaContent)
                .filter(content -> content != null && !content.isEmpty())
                .onErrorResume(e -> {
                    log.error("流式API调用失败: {}", e.getMessage());
                    return Flux.just("抱歉，AI服务暂时不可用。请稍后再试。");
                });
    }

    private String extractDeltaContent(String data) {
        try {
            String jsonStr = data.startsWith("data:") ? data.substring(5).trim() : data.trim();
            if (jsonStr.isEmpty() || jsonStr.equals("[DONE]")) {
                return "";
            }

            JSONObject json = JSON.parseObject(jsonStr);
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject delta = choice.getJSONObject("delta");
                if (delta != null) {
                    String content = delta.getString("content");
                    return content != null ? content : "";
                }
            }
        } catch (Exception e) {
            log.trace("解析SSE数据失败: {}", data);
        }
        return "";
    }

    private Flux<String> mockStreamAnswer(String question, String context) {
        String fullAnswer = mockAnswer(question, context);

        return Flux.fromArray(fullAnswer.split(""))
                .delayElements(Duration.ofMillis(30));
    }

    private String callAiApi(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);

        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

        long startTime = System.currentTimeMillis();
        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, entity, String.class);
        long elapsed = System.currentTimeMillis() - startTime;
        log.debug("AI API响应时间: {}ms", elapsed);

        JSONObject responseJson = JSON.parseObject(response.getBody());
        JSONArray choices = responseJson.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            return choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        }

        return null;
    }

    private JSONObject parseJsonFromResponse(String response) {
        if (response == null)
            return null;

        try {
            return JSON.parseObject(response);
        } catch (Exception e) {
            int start = response.indexOf("{");
            int end = response.lastIndexOf("}");
            if (start >= 0 && end > start) {
                try {
                    return JSON.parseObject(response.substring(start, end + 1));
                } catch (Exception e2) {
                    log.warn("无法解析AI响应JSON: {}", response);
                }
            }
        }
        return null;
    }

    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "...";
    }

    private AiAnalysis mockAnalysis(CommitRecord commit) {
        AiAnalysis analysis = new AiAnalysis();

        String message = commit.getCommitMessage().toLowerCase();

        if (message.contains("fix") || message.contains("bug") || message.contains("修复")) {
            analysis.setChangeCategory("bugfix");
            analysis.setSummary("修复了一个bug问题");
            analysis.setPurpose("解决用户反馈的问题或系统异常");
            analysis.setImpact("提升了系统稳定性");
            analysis.setComplexityScore(4);
            analysis.setImportanceScore(6);
        } else if (message.contains("feat") || message.contains("add") || message.contains("新增")
                || message.contains("添加")) {
            analysis.setChangeCategory("feature");
            analysis.setSummary("添加了新功能");
            analysis.setPurpose("满足新的业务需求");
            analysis.setImpact("扩展了系统能力");
            analysis.setComplexityScore(6);
            analysis.setImportanceScore(7);
        } else if (message.contains("refactor") || message.contains("优化") || message.contains("重构")) {
            analysis.setChangeCategory("refactor");
            analysis.setSummary("代码重构优化");
            analysis.setPurpose("提升代码可维护性和可读性");
            analysis.setImpact("改善了代码质量");
            analysis.setComplexityScore(5);
            analysis.setImportanceScore(5);
        } else if (message.contains("doc") || message.contains("readme") || message.contains("文档")) {
            analysis.setChangeCategory("docs");
            analysis.setSummary("更新了文档");
            analysis.setPurpose("完善项目文档");
            analysis.setImpact("便于其他开发者理解项目");
            analysis.setComplexityScore(2);
            analysis.setImportanceScore(4);
        } else if (message.contains("perf") || message.contains("性能") || message.contains("优化")) {
            analysis.setChangeCategory("perf");
            analysis.setSummary("性能优化");
            analysis.setPurpose("提升系统性能");
            analysis.setImpact("改善用户体验");
            analysis.setComplexityScore(5);
            analysis.setImportanceScore(6);
        } else if (message.contains("test") || message.contains("测试")) {
            analysis.setChangeCategory("test");
            analysis.setSummary("添加或更新测试");
            analysis.setPurpose("提升代码覆盖率");
            analysis.setImpact("增强代码可靠性");
            analysis.setComplexityScore(3);
            analysis.setImportanceScore(5);
        } else if (message.contains("style") || message.contains("格式")) {
            analysis.setChangeCategory("style");
            analysis.setSummary("代码格式调整");
            analysis.setPurpose("统一代码风格");
            analysis.setImpact("提升代码可读性");
            analysis.setComplexityScore(1);
            analysis.setImportanceScore(2);
        } else {
            analysis.setChangeCategory("chore");
            analysis.setSummary("常规代码更新");
            analysis.setPurpose("日常维护");
            analysis.setImpact("保持代码更新");
            analysis.setComplexityScore(3);
            analysis.setImportanceScore(3);
        }

        analysis.setModelUsed("mock");
        return analysis;
    }

    private String mockAnswer(String question, String context) {
        String q = question.toLowerCase();

        boolean hasContext = context != null && !context.trim().isEmpty();
        String contextInfo = hasContext
                ? "根据当前代码（" + context.split("\n").length + "行）"
                : "";

        if (q.contains("什么") || q.contains("功能") || q.contains("作用")) {
            return contextInfo + "这段代码的主要功能是处理数据转换和业务逻辑。它接收输入参数，进行一系列处理后返回结果。核心逻辑包括数据验证、转换和持久化操作。";
        } else if (q.contains("为什么") || q.contains("原因") || q.contains("目的") || q.contains("设计") || q.contains("好处")) {
            return contextInfo + "这样设计的原因主要是为了提高代码的可维护性和可扩展性。通过分离关注点，每个模块都有明确的职责，便于后续的修改和测试。同时这种设计也符合开闭原则，对扩展开放，对修改关闭。";
        } else if (q.contains("影响") || q.contains("问题") || q.contains("风险")) {
            return contextInfo + "这个改动可能会影响到依赖此模块的其他部分。建议在修改前进行充分的测试，确保不会引入回归问题。主要需要关注的是：1）接口兼容性；2）性能影响；3）边界条件处理。";
        } else if (q.contains("优化") || q.contains("改进") || q.contains("建议")) {
            return "以下是一些优化建议：\n1. 可以考虑添加缓存来提升性能\n2. 部分重复代码可以抽取为公共方法\n3. 建议添加更多的异常处理\n4. 可以使用设计模式来提高可扩展性";
        } else if (q.contains("设计模式") || q.contains("模式")) {
            return "这段代码使用了几种常见的设计模式：\n1. 工厂模式 - 用于对象创建\n2. 策略模式 - 用于算法封装\n3. 观察者模式 - 用于事件处理\n这些模式的组合使用提高了代码的灵活性和可维护性。";
        } else if (q.contains("如何") || q.contains("怎么") || q.contains("怎样")) {
            return "根据代码上下文，你可以按照以下步骤操作：\n1. 首先确保理解当前的数据流\n2. 找到需要修改的关键位置\n3. 按照现有的代码风格进行修改\n4. 添加必要的单元测试\n5. 进行代码审查和测试验证";
        } else {
            return contextInfo + "这是一个典型的业务逻辑实现。代码结构清晰，遵循了良好的编程规范。如果你有更具体的问题，比如关于某个函数的实现细节、性能优化建议或者设计模式的应用，欢迎继续提问！";
        }
    }

    private static final String LEARNING_PATH_PROMPT = """
            你是一位有经验的开发者导师。请根据以下项目元信息，为初学者生成一个学习路径。

            %s

            请给出学习建议，包括：
            1. 项目概述（2-3句话描述这是什么项目）
            2. 推荐阅读顺序（列出5-10个最重要的文件，按学习顺序排列）
            3. 每个文件的学习要点（1句话）
            4. 学习建议（如何有效学习这个项目）

            要求：
            - 从入口文件开始
            - 优先推荐修改次数多的核心文件
            - 用中文回答
            - 使用 Markdown 格式
            """;

    @Override
    public String generateLearningPath(String projectMetadata) {
        if (apiKey == null || apiKey.isEmpty()) {
            return mockLearningPath(projectMetadata);
        }

        String prompt = String.format(LEARNING_PATH_PROMPT, projectMetadata);

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 1500);

            JSONArray messages = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一位擅长指导开发者学习开源项目的导师。");
            messages.add(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            requestBody.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            JSONObject result = JSON.parseObject(response.getBody());
            JSONArray choices = result.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                return choices.getJSONObject(0).getJSONObject("message").getString("content");
            }
        } catch (Exception e) {
            log.error("AI学习路径生成失败", e);
        }

        return mockLearningPath(projectMetadata);
    }

    private String mockLearningPath(String projectMetadata) {
        String projectName = "Java项目";
        if (projectMetadata.contains("项目名:")) {
            int start = projectMetadata.indexOf("项目名:") + 4;
            int end = projectMetadata.indexOf("\n", start);
            if (end > start) {
                projectName = projectMetadata.substring(start, end).trim();
            }
        }

        return String.format("""
                # 📚 %s 学习路径

                ## 项目概述
                这是一个典型的 Java 项目，采用了分层架构设计。项目结构清晰，适合初学者学习。

                ## 推荐学习顺序

                ### 第一阶段：了解项目结构
                1. **README.md** - 了解项目背景和使用方式
                2. **pom.xml** - 了解项目依赖和构建配置

                ### 第二阶段：从入口开始
                3. **Main.java / Application.java** - 程序入口，理解启动流程

                ### 第三阶段：核心逻辑
                4. **核心业务类** - 理解主要业务逻辑
                5. **工具类** - 学习通用工具方法

                ## 💡 学习建议

                1. **先运行起来** - 在本地跑通项目，有个直观感受
                2. **从入口追踪** - 从 main 方法开始，追踪调用链
                3. **关注核心类** - 修改次数多的文件通常是核心代码
                4. **对比历史版本** - 使用时光机功能查看代码演化过程
                5. **提问学习** - 遇到不懂的代码，使用 AI 对话功能提问

                > 祝你学习愉快！🎉
                """, projectName);
    }
}
