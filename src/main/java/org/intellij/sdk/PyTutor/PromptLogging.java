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

    public PromptLogging() {
        Properties properties = readPropertiesFromResources();
        this.loggingApiUrl = properties.getProperty(LOGGING_API_URL_PROPERTY);
        this.loggingApiKey = properties.getProperty(LOGGING_API_KEY_PROPERTY);

        if (loggingApiUrl == null || loggingApiUrl.isEmpty() || loggingApiKey == null || loggingApiKey.isEmpty()) {
            throw new IllegalStateException("Logging API URL or API key not found in pytutor.properties file");
        }
    }

    public void logPrompt(String prompt) {
        String requestBody = String.format("{\"entry\": \"Prompt: %s\"}", escapeJson(prompt));
        sendLogRequest(requestBody);
    }

    public void logResponse(String response) {
        String requestBody = String.format("{\"entry\": \"Response: %s\"}", escapeJson(response));
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