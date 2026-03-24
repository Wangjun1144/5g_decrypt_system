package com.example.procedure.decrypt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * 基于 HTTP 的外部解密网关实现。
 *
 * 当前职责：
 * 1. 负责把解密请求发给外部 HTTP 解密服务
 * 2. 负责把返回 JSON 解析为 DecryptResponse
 * 3. 对上层隐藏底层静态工具类和网络调用细节
 *
 * 当前阶段保留的策略：
 * - 继续复用已有 DecryptClient 的 HTTP 调用逻辑
 * - 先把边界收口，再考虑更细的超时、重试、熔断治理
 */
@Service
public class HttpDecryptGateway implements DecryptGateway {

    /**
     * 用于解析响应 JSON。
     */
    private final ObjectMapper objectMapper;

    /**
     * 解密网关配置。
     */
    private final DecryptGatewayProperties properties;

    /**
     * 构造 HTTP 解密网关。
     *
     * @param objectMapper JSON 解析器
     * @param properties 网关配置
     */
    public HttpDecryptGateway(
            ObjectMapper objectMapper,
            DecryptGatewayProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 调用外部 HTTP 解密服务。
     *
     * 当前实现过程：
     * 1. 使用已有 DecryptClient 发送 HTTP 请求
     * 2. 读取响应 JSON
     * 3. 反序列化为 DecryptResponse
     *
     * @param request 解密请求对象
     * @return 解密响应对象
     * @throws Exception HTTP 调用失败或 JSON 解析失败时抛出异常
     */
    @Override
    public DecryptResponse decrypt(DecryptClient.DecryptRequest request) throws Exception {
        String responseJson = DecryptClient.decrypt(properties.getUrl(), request);
        return objectMapper.readValue(responseJson, DecryptResponse.class);
    }
}
