package com.raglaw.agentscope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raglaw.langfuse")
public class LangfuseProperties {

    private boolean enabled = false;
    private String host = "http://localhost:3001";
    private String publicKey = "";
    private String secretKey = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isConfigured() {
        return enabled
                && publicKey != null && !publicKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
