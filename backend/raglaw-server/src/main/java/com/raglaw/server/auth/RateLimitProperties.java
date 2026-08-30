package com.raglaw.server.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raglaw.rate-limit")
public class RateLimitProperties {

    private int loginPerMinute = 5;
    private int aguiPerMinute = 20;

    public int getLoginPerMinute() {
        return loginPerMinute;
    }

    public void setLoginPerMinute(int loginPerMinute) {
        this.loginPerMinute = loginPerMinute;
    }

    public int getAguiPerMinute() {
        return aguiPerMinute;
    }

    public void setAguiPerMinute(int aguiPerMinute) {
        this.aguiPerMinute = aguiPerMinute;
    }
}
