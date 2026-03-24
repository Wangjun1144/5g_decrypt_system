package com.example.procedure.decrypt;

/**
 * 外部解密能力访问边界。
 *
 * 当前用途：
 * 1. 把“如何调用外部解密服务”从主业务逻辑中抽离出来
 * 2. 让 DecryptCoordinator 只关心解密业务编排，不关心 HTTP 细节
 * 3. 为后续替换成独立微服务、MQ 请求、mock 实现预留稳定接口
 */
public interface DecryptGateway {

    /**
     * 调用外部解密能力。
     *
     * 这里的语义很单纯：
     * - 输入一份解密请求
     * - 返回解密响应
     * - 至于底层是 HTTP、RPC 还是本地 mock，不由上层关心
     *
     * @param request 解密请求对象
     * @return 解密响应对象
     * @throws Exception 调用或响应解析失败时抛出异常
     */
    DecryptResponse decrypt(DecryptClient.DecryptRequest request) throws Exception;
}
