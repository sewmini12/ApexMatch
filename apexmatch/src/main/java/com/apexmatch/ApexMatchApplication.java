package com.apexmatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
public class ApexMatchApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(ApexMatchApplication.class, args);
    }

    private static void loadDotEnv() {
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            envPath = Paths.get("..", ".env");
        }
        if (Files.exists(envPath)) {
            try {
                List<String> lines = Files.readAllLines(envPath);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eqIdx = line.indexOf('=');
                    if (eqIdx > 0) {
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        // Auto-sanitize DB_URL if user:pass@ syntax is used
        String dbUrl = System.getProperty("DB_URL");
        if (dbUrl == null) {
            dbUrl = System.getenv("DB_URL");
        }
        if (dbUrl != null && dbUrl.contains("@")) {
            int protocolEnd = dbUrl.indexOf("://");
            if (protocolEnd != -1) {
                String rest = dbUrl.substring(protocolEnd + 3);
                int atIndex = rest.indexOf('@');
                if (atIndex != -1) {
                    String userPass = rest.substring(0, atIndex);
                    String hostAndBeyond = rest.substring(atIndex + 1);
                    int colonIndex = userPass.indexOf(':');
                    if (colonIndex != -1) {
                        String username = userPass.substring(0, colonIndex);
                        String password = userPass.substring(colonIndex + 1);
                        if (System.getProperty("DB_USERNAME") == null || "YOUR_USERNAME".equals(System.getProperty("DB_USERNAME"))) {
                            System.setProperty("DB_USERNAME", username);
                        }
                        if (System.getProperty("DB_PASSWORD") == null || "YOUR_PASSWORD".equals(System.getProperty("DB_PASSWORD"))) {
                            System.setProperty("DB_PASSWORD", password);
                        }
                    }
                    System.setProperty("DB_URL", "jdbc:postgresql://" + hostAndBeyond);
                }
            }
        }
    }
}