package com.example.procedure.support.logging;

import com.example.procedure.model.SignalingMessage;
import com.example.procedure.util.SignalingMessagePrinter;

import java.nio.file.Path;

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
}