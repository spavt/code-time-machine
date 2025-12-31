package com.codetimemachine.common;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Git 命令执行工具类
 * 封装跨平台的 Git 命令构建和执行逻辑
 */
@Slf4j
public class GitCommandHelper {

    private static final boolean IS_WINDOWS = System.getProperty("os.name")
            .toLowerCase().contains("win");

    /**
     * 获取当前操作系统是否为 Windows
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * 创建基础 Git 命令 List
     * 自动处理 Windows 平台的 cmd.exe 包装
     * 
     * @return 预填充平台前缀的命令列表
     */
    public static List<String> createCommand() {
        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS) {
            cmd.add("cmd.exe");
            cmd.add("/c");
        }
        cmd.add("git");
        return cmd;
    }

    /**
     * 创建带 longpaths 支持的 Git 命令（用于 Windows 超长路径）
     * 
     * @return 预填充平台前缀和 longpaths 配置的命令列表
     */
    public static List<String> createCommandWithLongPaths() {
        List<String> cmd = createCommand();
        if (IS_WINDOWS) {
            cmd.add("-c");
            cmd.add("core.longpaths=true");
        }
        return cmd;
    }

    /**
     * 执行 Git 命令并返回输出
     * 
     * @param cmd        命令列表
     * @param workDir    工作目录
     * @param timeoutSec 超时时间（秒），0 表示不限时
     * @return 命令输出，失败返回 null
     */
    public static String execute(List<String> cmd, File workDir, int timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workDir != null) {
                pb.directory(workDir);
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = process.getInputStream().read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }

            boolean finished;
            if (timeoutSec > 0) {
                finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("Git 命令执行超时: {}", String.join(" ", cmd));
                    return null;
                }
            } else {
                process.waitFor();
            }

            int exitCode = process.exitValue();
            String output = baos.toString(StandardCharsets.UTF_8);

            if (exitCode != 0) {
                log.warn("Git 命令执行失败 (exit {}): {}", exitCode, String.join(" ", cmd));
                return null;
            }

            return output;
        } catch (Exception e) {
            log.error("Git 命令执行异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行 Git 命令并按行返回输出
     * 
     * @param cmd     命令列表
     * @param workDir 工作目录
     * @return 输出行列表，失败返回空列表
     */
    public static List<String> executeAndReadLines(List<String> cmd, File workDir) {
        List<String> lines = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workDir != null) {
                pb.directory(workDir);
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();
            process.waitFor();

            return lines;
        } catch (Exception e) {
            log.error("Git 命令执行异常: {}", e.getMessage());
            return lines;
        }
    }
}
