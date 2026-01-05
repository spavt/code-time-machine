package com.codetimemachine.config;

import com.codetimemachine.dto.RecommendedRepoDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class RecommendedReposConfig {

    @Bean
    public Map<String, List<RecommendedRepoDTO>> recommendedReposByLevel() {
        List<RecommendedRepoDTO> allRepos = Arrays.asList(
                // ========== 新手级别 ==========
                RecommendedRepoDTO.builder()
                        .name("javascript-algorithms")
                        .url("https://github.com/trekhleb/javascript-algorithms")
                        .description("用 JavaScript 实现的算法和数据结构，配有详细解释和示例")
                        .level("beginner")
                        .tags(Arrays.asList("算法", "JavaScript", "入门"))
                        .stars("180k+")
                        .language("JavaScript")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("build-your-own-x")
                        .url("https://github.com/codecrafters-io/build-your-own-x")
                        .description("从零构建各种技术的教程合集，边做边学")
                        .level("beginner")
                        .tags(Arrays.asList("教程", "实践", "入门"))
                        .stars("250k+")
                        .language("多语言")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("realworld")
                        .url("https://github.com/gothinkster/realworld")
                        .description("使用各种前后端框架实现的真实应用，学习最佳实践")
                        .level("beginner")
                        .tags(Arrays.asList("全栈", "最佳实践", "Demo"))
                        .stars("77k+")
                        .language("多语言")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("30-seconds-of-code")
                        .url("https://github.com/Chalarangelo/30-seconds-of-code")
                        .description("短小精悍的代码片段集合，每个都能快速理解")
                        .level("beginner")
                        .tags(Arrays.asList("代码片段", "JavaScript", "速学"))
                        .stars("118k+")
                        .language("JavaScript")
                        .build(),

                // ========== 有基础级别 ==========
                RecommendedRepoDTO.builder()
                        .name("axios")
                        .url("https://github.com/axios/axios")
                        .description("最流行的 HTTP 客户端库，代码结构清晰")
                        .level("intermediate")
                        .tags(Arrays.asList("HTTP", "网络请求", "工具库"))
                        .stars("103k+")
                        .language("JavaScript")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("dayjs")
                        .url("https://github.com/iamkun/dayjs")
                        .description("轻量级日期处理库，代码仅 2KB，适合学习")
                        .level("intermediate")
                        .tags(Arrays.asList("日期", "轻量", "插件系统"))
                        .stars("45k+")
                        .language("JavaScript")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("zustand")
                        .url("https://github.com/pmndrs/zustand")
                        .description("极简状态管理库，核心代码不到 100 行")
                        .level("intermediate")
                        .tags(Arrays.asList("状态管理", "React", "极简"))
                        .stars("40k+")
                        .language("TypeScript")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("express")
                        .url("https://github.com/expressjs/express")
                        .description("Node.js 经典 Web 框架，中间件模式范例")
                        .level("intermediate")
                        .tags(Arrays.asList("Node.js", "Web框架", "中间件"))
                        .stars("62k+")
                        .language("JavaScript")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("lodash")
                        .url("https://github.com/lodash/lodash")
                        .description("工具函数库经典，学习函数式编程思想")
                        .level("intermediate")
                        .tags(Arrays.asList("工具库", "函数式", "最佳实践"))
                        .stars("58k+")
                        .language("JavaScript")
                        .build(),

                // ========== 高手级别 ==========
                RecommendedRepoDTO.builder()
                        .name("vue-core")
                        .url("https://github.com/vuejs/core")
                        .description("Vue 3 核心源码，响应式系统和编译器实现")
                        .level("advanced")
                        .tags(Arrays.asList("Vue", "框架源码", "响应式"))
                        .stars("44k+")
                        .language("TypeScript")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("react")
                        .url("https://github.com/facebook/react")
                        .description("React 源码，Fiber 架构和调度器")
                        .level("advanced")
                        .tags(Arrays.asList("React", "框架源码", "Fiber"))
                        .stars("218k+")
                        .language("JavaScript")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("vite")
                        .url("https://github.com/vitejs/vite")
                        .description("下一代前端构建工具，ESM 和 Rollup 集成")
                        .level("advanced")
                        .tags(Arrays.asList("构建工具", "ESM", "Rollup"))
                        .stars("63k+")
                        .language("TypeScript")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("node")
                        .url("https://github.com/nodejs/node")
                        .description("Node.js 运行时源码，libuv 事件循环")
                        .level("advanced")
                        .tags(Arrays.asList("Node.js", "运行时", "C++"))
                        .stars("102k+")
                        .language("C++")
                        .build(),
                RecommendedRepoDTO.builder()
                        .name("typescript")
                        .url("https://github.com/microsoft/TypeScript")
                        .description("TypeScript 编译器源码，类型系统实现")
                        .level("advanced")
                        .tags(Arrays.asList("编译器", "类型系统", "语言设计"))
                        .stars("96k+")
                        .language("TypeScript")
                        .build());

        return allRepos.stream()
                .collect(Collectors.groupingBy(RecommendedRepoDTO::getLevel));
    }
}
