package com.example.procedure.support.logging;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.util.SignalingMessagePrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * 阶段 1：
 * 把“日志文件输出”包装成 support 层能力，
 * 避免 application 层直接依赖 util 中的打印细节。
 */
public final class SignalingDumpWriter {

    private SignalingDumpWriter() {
    }

    public static void write(SignalingMessage msg, Path output, boolean append) {
        SignalingMessagePrinter.printAndWriteToFile(msg, output, append);
    }

    /**
     * 删除整个日志目录（包含目录下所有文件和子目录）。
     */
    public static void deleteLogDirectory(Path logDir) {
        if (logDir == null || Files.notExists(logDir)) {
            return;
        }

        try {
            Files.walk(logDir)
                    .sorted(Comparator.reverseOrder()) // 先删文件，再删目录
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException("删除日志文件失败: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("删除日志目录失败: " + logDir, e);
        }
    }
}