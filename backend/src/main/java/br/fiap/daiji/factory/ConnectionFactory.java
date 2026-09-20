package br.fiap.daiji.factory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConnectionFactory {
    private final String url;
    private final String user;
    private final String password;

    public ConnectionFactory(@Value("${db.url}") String url,
                             @Value("${db.user}") String user,
                             @Value("${db.password}") String password) {
        if (url == null || !url.startsWith("jdbc:oracle:")
                || user == null || user.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Configure ORACLE_URL, ORACLE_USER e ORACLE_PASSWORD.");
        }
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection conectar() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
