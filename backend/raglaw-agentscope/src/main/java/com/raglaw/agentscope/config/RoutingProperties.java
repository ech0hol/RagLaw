package com.raglaw.agentscope.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@org.springframework.stereotype.Component
@ConfigurationProperties(prefix="raglaw.routing")
public class RoutingProperties { private RoutingMode mode = RoutingMode.SHADOW; public RoutingMode getMode(){return mode;} public void setMode(RoutingMode v){mode=v==null?RoutingMode.SHADOW:v;} }
