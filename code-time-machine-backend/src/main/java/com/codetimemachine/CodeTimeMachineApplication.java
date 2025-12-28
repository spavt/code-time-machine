package com.codetimemachine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI代码时光机 - 启动类
 */
@SpringBootApplication
@MapperScan("com.codetimemachine.mapper")
@EnableAsync
public class CodeTimeMachineApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(CodeTimeMachineApplication.class, args);
        System.out.println("=================================");
        System.out.println("  AI代码时光机 启动成功!");
        System.out.println("  http://localhost:8080");
        System.out.println("=================================");
    }
}
