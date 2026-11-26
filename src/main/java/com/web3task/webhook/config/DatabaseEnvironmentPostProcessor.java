package com.web3task.webhook.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles cloud platform database environment variables (Railway, Render, Fly.io, Heroku).
 * Converts DATABASE_URL / DATABASE_PUBLIC_URL / DATABASE_PRIVATE_URL / PGHOST variables into Spring JDBC properties.
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DatabaseEnvironmentPostProcessor.class);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = environment.getProperty("DATABASE_PRIVATE_URL");
        }
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = environment.getProperty("DATABASE_PUBLIC_URL");
        }
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = environment.getProperty("POSTGRES_URL");
        }
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        }

        Map<String, Object> props = new HashMap<>();

        if (databaseUrl != null && !databaseUrl.isBlank()) {
            System.out.println("[DatabaseEnvironmentPostProcessor] Detected cloud database URL: " + maskUrl(databaseUrl));
            if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
                try {
                    URI dbUri = URI.create(databaseUrl.replace("postgres://", "http://").replace("postgresql://", "http://"));
                    String username = "";
                    String password = "";

                    if (dbUri.getUserInfo() != null) {
                        String[] userInfo = dbUri.getUserInfo().split(":", 2);
                        username = userInfo[0];
                        if (userInfo.length > 1) {
                            password = userInfo[1];
                        }
                    }

                    String host = dbUri.getHost();
                    int port = dbUri.getPort() != -1 ? dbUri.getPort() : 5432;
                    String path = dbUri.getPath();
                    if (path == null || path.isBlank() || path.equals("/")) {
                        path = "/railway";
                    }
                    String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                    if (dbUri.getQuery() != null && !dbUri.getQuery().isBlank()) {
                        jdbcUrl += "?" + dbUri.getQuery();
                    }

                    props.put("spring.datasource.url", jdbcUrl);
                    if (!username.isBlank()) {
                        props.put("spring.datasource.username", username);
                    }
                    if (!password.isBlank()) {
                        props.put("spring.datasource.password", password);
                    }

                    environment.getPropertySources().addFirst(new MapPropertySource("cloudDatabaseProperties", props));
                    System.out.println("[DatabaseEnvironmentPostProcessor] Successfully configured DataSource for host: " + host + ":" + port);
                    return;
                } catch (Exception e) {
                    System.err.println("[DatabaseEnvironmentPostProcessor] Error parsing DATABASE_URL: " + e.getMessage());
                }
            } else if (databaseUrl.startsWith("jdbc:postgresql://")) {
                props.put("spring.datasource.url", databaseUrl);
                environment.getPropertySources().addFirst(new MapPropertySource("cloudDatabaseProperties", props));
                System.out.println("[DatabaseEnvironmentPostProcessor] Using raw JDBC URL.");
                return;
            }
        }

        // Fallback for Railway PG* individual variables
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
            System.out.println("[DatabaseEnvironmentPostProcessor] Configured DataSource from PGHOST: " + pgHost + ":" + pgPort);
            return;
        }

        System.out.println("[DatabaseEnvironmentPostProcessor] NOTICE: No cloud database URL detected. Using application defaults (localhost:5432).");
    }

    private String maskUrl(String url) {
        return url.replaceAll(":[^:@]+@", ":****@");
    }
}
