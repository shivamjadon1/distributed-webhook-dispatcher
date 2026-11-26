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
 * Handles cloud platform database environment variables (Railway, Render, Fly.io, Heroku).
 * Converts DATABASE_URL / DATABASE_PUBLIC_URL or PGHOST variables into Spring JDBC properties.
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = environment.getProperty("DATABASE_PUBLIC_URL");
        }

        Map<String, Object> props = new HashMap<>();

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
                if (dbUri.getQuery() != null && !dbUri.getQuery().isBlank()) {
                    jdbcUrl += "?" + dbUri.getQuery();
                }

                props.put("spring.datasource.url", jdbcUrl);
                props.put("spring.datasource.username", username);
                props.put("spring.datasource.password", password);

                environment.getPropertySources().addFirst(new MapPropertySource("cloudDatabaseProperties", props));
                log.info("Successfully configured cloud PostgreSQL DataSource for host: {}", host);
                return;
            } catch (Exception e) {
                log.warn("Could not parse DATABASE_URL environment variable: {}", e.getMessage());
            }
        }

        // Fallback for Railway PG* variables
        String pgHost = environment.getProperty("PGHOST");
        if (pgHost != null && !pgHost.isBlank()) {
            String pgPort = environment.getProperty("PGPORT", "5432");
            String pgDatabase = environment.getProperty("PGDATABASE", "railway");
            String pgUser = environment.getProperty("PGUSER", "postgres");
            String pgPassword = environment.getProperty("PGPASSWORD", "");

            String jdbcUrl = "jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDatabase;
            props.put("spring.datasource.url", jdbcUrl);
            props.put("spring.datasource.username", pgUser);
            props.put("spring.datasource.password", pgPassword);

            environment.getPropertySources().addFirst(new MapPropertySource("cloudPgProperties", props));
            log.info("Successfully configured PostgreSQL DataSource from PGHOST: {}", pgHost);
        }
    }
}
