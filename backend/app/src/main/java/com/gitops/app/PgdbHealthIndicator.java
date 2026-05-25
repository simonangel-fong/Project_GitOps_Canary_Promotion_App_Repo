package com.gitops.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.stereotype.Component;

@Component
public class PgdbHealthIndicator {

    private final AppProperties props;

    public PgdbHealthIndicator(AppProperties props) {
        this.props = props;
    }

    public boolean isHealthy() {
        if (!props.isPgdbEnable()) {
            return true;
        }
        try (Connection conn = DriverManager.getConnection(props.getPgdbUrl())) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
