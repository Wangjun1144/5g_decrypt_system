package com.example.procedure.decrypt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部解密网关配置。
 *
 * 当前作用：
 * 1. 把解密服务地址从业务代码中抽离出来
 * 2. 让当前单体系统具备更好的配置治理能力
 * 3. 为后续不同环境、不同解密服务部署方式提供配置入口
 */
@Component
@ConfigurationProperties(prefix = "decrypt.gateway")
public class DecryptGatewayProperties {

    /**
     * 外部解密服务地址。
     *
     * 当前默认值保持与现有系统一致，
     * 避免重构过程中引入行为变化。
     */
    private String url = "http://127.0.0.1:8004/decrypt";

    /**
     * 获取解密服务地址。
     *
     * @return 解密服务 URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * 设置解密服务地址。
     *
     * @param url 解密服务 URL
     */
    public void setUrl(String url) {
        this.url = url;
    }
}
