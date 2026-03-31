package com.distributed.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles loading configuration from a .env file.
 * Defaults to system properties or provided defaults if .env is missing.
 */
public class ConfigLoader {
    /** Stores key-value pairs from the environment file */
    private static final Map<String, String> config = new HashMap<>();

    static {
        // Automatically load config when the class is first accessed
        load();
    }

    private static void load() {
        try (BufferedReader reader = new BufferedReader(new FileReader(".env"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    config.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: .env file not found or could not be read. Using code defaults.");
        }
    }

    /**
     * Retrieves a configuration value as a String.
     * @param key The configuration key
     * @param defaultValue Value to return if key is not found
     * @return The configured value or default
     */
    public static String get(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }

    /**
     * Retrieves a configuration value as an integer.
     * @param key The configuration key
     * @param defaultValue Value to return if key is not found or invalid
     * @return The configured integer or default
     */
    public static int getInt(String key, int defaultValue) {
        String value = config.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.err.println("Invalid integer for key " + key + ": " + value);
            }
        }
        return defaultValue;
    }
}
