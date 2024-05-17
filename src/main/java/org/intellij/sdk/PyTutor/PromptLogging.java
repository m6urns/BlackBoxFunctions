package org.intellij.sdk.PyTutor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

public class PromptLogging {
    private static final String LOGGING_API_URL_PROPERTY = "LOGGING_API_URL";
    private static final String LOGGING_API_KEY_PROPERTY = "LOGGING_API_KEY";
    private final String loggingApiUrl;
    private final String loggingApiKey;
    private String logId;

    public PromptLogging() {
        Properties properties = readPropertiesFromResources();
        this.loggingApiUrl = properties.getProperty(LOGGING_API_URL_PROPERTY);
        this.loggingApiKey = properties.getProperty(LOGGING_API_KEY_PROPERTY);
        if (loggingApiUrl == null || loggingApiUrl.isEmpty() || loggingApiKey == null || loggingApiKey.isEmpty()) {
            throw new IllegalStateException("Logging API URL or API key not found in pytutor.properties file");
        }
    }

    public void logPrompt(String id, String prompt) {
        logId = id;
        String requestBody = String.format("{\"id\": \"%s\", \"entry\": \"Prompt: %s\"}", escapeJson(id), escapeJson(prompt));
        sendLogRequest(requestBody);
    }

    public void logResponse(String id, String response) {
        logId = id;
        String requestBody = String.format("{\"id\": \"%s\", \"entry\": \"Response: %s\"}", escapeJson(id), escapeJson(response));
        sendLogRequest(requestBody);
    }

    public void logInteraction(String id, String interaction) {
        logId = id;
        String requestBody = String.format("{\"id\": \"%s\", \"entry\": \"Response: %s\"}", escapeJson(id), escapeJson(interaction));
        sendLogRequest(requestBody);
    }

    public void logDeletion(String id, String deletion) {
        logId = id;
        String requestBody = String.format("{\"id\": \"%s\", \"entry\": \"Deletion: %s\"}", escapeJson(id), escapeJson(deletion));
        sendLogRequest(requestBody);
    }

    private void sendLogRequest(String requestBody) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(loggingApiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + loggingApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        System.out.println("Sending log request:");
        System.out.println("URL: " + loggingApiUrl);
        System.out.println("Request Body: " + requestBody);

        try {
            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Log response received:");
            System.out.println("Status Code: " + httpResponse.statusCode());
            System.out.println("Response Body: " + httpResponse.body());
            if (httpResponse.statusCode() != 200) {
                System.out.println("Failed to log entry. Status code: " + httpResponse.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error occurred while logging entry: " + e.getMessage());
        }
    }

    private static String escapeJson(String input) {
        // Escape backslashes and double quotes
        String escaped = input.replace("\\", "\\\\").replace("\"", "\\\"");
        // Remove line breaks
        escaped = escaped.replace("\n", "\\n");
        return escaped;
    }

    private Properties readPropertiesFromResources() {
        try (InputStream inputStream = getClass().getResourceAsStream("/pytutor.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Error reading pytutor.properties file", e);
        }
    }
}