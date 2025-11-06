package com.example.apiclient;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "api")
public class ApiProperties {
    // Spring maps base-url → baseUrl, dist-channel → distChannel, unit-code → unitCode
    private String baseUrl;
    private String distChannel;
    private String unitCode;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public String getDistChannel() { return distChannel; }
    public void setDistChannel(String v) { this.distChannel = v; }
    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String v) { this.unitCode = v; }
}
