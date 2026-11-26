package com.web3task.webhook.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles standard cloud platform database environment variables (Render / Railway / Heroku).
 * Converts postgres:// or postgresql:// DATABASE_URL into Spring JDBC properties.
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl != null && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            try {
                URI dbUri = URI.create(databaseUrl.replace("postgres://", "http://").replace("postgresql://", "http://"));
                String username = "";
                String password = "";

                if (dbUri.getUserInfo() != null) {
                    String[] userInfo = dbUri.getUserInfo().split(":");
                    username = userInfo[0];
                    if (userInfo.length > 1) {
                        password = userInfo[1];
                    }
                }

                String host = dbUri.getHost();
                int port = dbUri.getPort() != -1 ? dbUri.getPort() : 5432;
                String path = dbUri.getPath();
                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;

                Map<String, Object> props = new HashMap<>();
                props.put("spring.datasource.url", jdbcUrl);
                props.put("spring.datasource.username", username);
                props.put("spring.datasource.password", password);

                environment.getPropertySources().addFirst(new MapPropertySource("cloudDatabaseProperties", props));
                log.info("Successfully configured cloud PostgreSQL DataSource for host: {}", host);
            } catch (Exception e) {
                log.warn("Could not parse DATABASE_URL environment variable: {}", e.getMessage());
            }
        }
    }
}
