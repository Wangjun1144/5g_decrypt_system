package com.example.scene.decodersystem;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Disables Redis integration tests when the configured Redis endpoint is unavailable.
 */
class RedisAvailableCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("Redis endpoint is reachable");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String host = firstNonBlank(
                System.getProperty("test.redis.host"),
                System.getenv("TEST_REDIS_HOST"),
                System.getProperty("redis.host"),
                System.getenv("REDIS_HOST"),
                "192.168.179.132"
        );
        int port = parsePort(
                firstNonBlank(
                        System.getProperty("test.redis.port"),
                        System.getenv("TEST_REDIS_PORT"),
                        System.getProperty("redis.port"),
                        System.getenv("REDIS_PORT"),
                        "6379"
                )
        );

        if (isReachable(host, port)) {
            return ENABLED;
        }

        return ConditionEvaluationResult.disabled(
                "Redis integration test disabled because " + host + ":" + port + " is unavailable"
        );
    }

    /**
     * Performs a short TCP reachability probe before letting Redis tests execute.
     */
    private boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Returns the first non-empty value from the supplied candidates.
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Parses the configured Redis port and falls back to the default Redis port on bad input.
     */
    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 6379;
        }
    }
}
