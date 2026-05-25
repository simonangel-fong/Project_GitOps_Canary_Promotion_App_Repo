package com.gitops.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String frontendBgColor = "green";
    private String backendVersion = "0.1.0";
    private boolean pgdbEnable = false;
    private String pgdbUrl = "jdbc:postgresql://postgres:5432/demo_db";
    private boolean oomEnable = false;
    private long oomTime = 0L;

    public String getFrontendBgColor() {
        return frontendBgColor;
    }

    public void setFrontendBgColor(String frontendBgColor) {
        this.frontendBgColor = frontendBgColor;
    }

    public String getBackendVersion() {
        return backendVersion;
    }

    public void setBackendVersion(String backendVersion) {
        this.backendVersion = backendVersion;
    }

    public boolean isPgdbEnable() {
        return pgdbEnable;
    }

    public void setPgdbEnable(boolean pgdbEnable) {
        this.pgdbEnable = pgdbEnable;
    }

    public String getPgdbUrl() {
        return pgdbUrl;
    }

    public void setPgdbUrl(String pgdbUrl) {
        this.pgdbUrl = pgdbUrl;
    }

    public boolean isOomEnable() {
        return oomEnable;
    }

    public void setOomEnable(boolean oomEnable) {
        this.oomEnable = oomEnable;
    }

    public long getOomTime() {
        return oomTime;
    }

    public void setOomTime(long oomTime) {
        this.oomTime = oomTime;
    }
}
