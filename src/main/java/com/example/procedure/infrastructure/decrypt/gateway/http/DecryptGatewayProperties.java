package com.example.procedure.infrastructure.decrypt.gateway.http;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 澶栭儴瑙ｅ瘑缃戝叧閰嶇疆銆?
 *
 * 褰撳墠浣滅敤锛?
 * 1. 鎶婅В瀵嗘湇鍔″湴鍧€浠庝笟鍔′唬鐮佷腑鎶界鍑烘潵
 * 2. 璁╁綋鍓嶅崟浣撶郴缁熷叿澶囨洿濂界殑閰嶇疆娌荤悊鑳藉姏
 * 3. 涓哄悗缁笉鍚岀幆澧冦€佷笉鍚岃В瀵嗘湇鍔￠儴缃叉柟寮忔彁渚涢厤缃叆鍙?
 */
@Component
@ConfigurationProperties(prefix = "decrypt.gateway")
public class DecryptGatewayProperties {

    /**
     * 澶栭儴瑙ｅ瘑鏈嶅姟鍦板潃銆?
     *
     * 褰撳墠榛樿鍊间繚鎸佷笌鐜版湁绯荤粺涓€鑷达紝
     * 閬垮厤閲嶆瀯杩囩▼涓紩鍏ヨ涓哄彉鍖栥€?
     */
    private String url = "http://127.0.0.1:8004/decrypt";

    /**
     * 鑾峰彇瑙ｅ瘑鏈嶅姟鍦板潃銆?
     *
     * @return 瑙ｅ瘑鏈嶅姟 URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * 璁剧疆瑙ｅ瘑鏈嶅姟鍦板潃銆?
     *
     * @param url 瑙ｅ瘑鏈嶅姟 URL
     */
    public void setUrl(String url) {
        this.url = url;
    }
}
