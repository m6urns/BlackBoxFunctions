package org.intellij.sdk.PyTutor;

import com.intellij.openapi.project.Project;

import java.io.*;
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
    private final String sessionId;
    private final Project project;

    public PromptLogging(Project project) {
        Properties properties = readPropertiesFromResources();
        this.loggingApiUrl = properties.getProperty(LOGGING_API_URL_PROPERTY);
        this.loggingApiKey = properties.getProperty(LOGGING_API_KEY_PROPERTY);
        if (loggingApiUrl == null || loggingApiUrl.isEmpty() || loggingApiKey == null || loggingApiKey.isEmpty()) {
            throw new IllegalStateException("Logging API URL or API key not found in pytutor.properties file");
        }
        this.sessionId = generateSessionId();
        this.project = project;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String generateSessionId() {
        return java.util.UUID.randomUUID().toString();
    }

    public void logPrompt(String id, String prompt) {
        String requestBody = String.format("{\"EventType\": \"Prompt\", \"AssignmentID\": \"%s\", \"SubjectID\": \"%s\", \"Entry\": \"Prompt\", \"ClientTimestamp\": \"%d\", \"X-Metadata\": \"%s\"}",
                escapeJson(getSessionId()), escapeJson(id), System.currentTimeMillis(), escapeJson(prompt));
//        appendToLocalLog("Prompt", "", "", "", System.currentTimeMillis(), "", "", "", prompt, "", "", "", id, getSessionId());
        sendLogRequest(requestBody);
    }

    public void logResponse(String id, String response) {
        String requestBody = String.format("{\"EventType\": \"Response\", \"AssignmentID\": \"%s\", \"SubjectID\": \"%s\", \"Entry\": \"Response\", \"ClientTimestamp\": \"%d\", \"X-Metadata\": \"%s\"}",
                escapeJson(getSessionId()), escapeJson(id), System.currentTimeMillis(), escapeJson(response));
        sendLogRequest(requestBody);
    }

    public void logInteraction(String interaction) {
        String requestBody = String.format("{\"EventType\": \"Interaction\", \"AssignmentID\": \"%s\", \"Entry\": \"Interaction\", \"ClientTimestamp\": \"%d\", \"X-Metadata\": \"%s\"}",
                escapeJson(getSessionId()), System.currentTimeMillis(), escapeJson(interaction));
//        appendToLocalLog("Interaction", "", "", "", System.currentTimeMillis(), "", "", "", interaction, "", "", "", "", getSessionId());
        sendLogRequest(requestBody);
    }

    public void logDeletion(String id, String deletion) {
        String requestBody = String.format("{\"EventType\": \"Deletion\", \"AssignmentID\": \"%s\", \"SubjectID\": \"%s\", \"Entry\": \"Deletion\", \"ClientTimestamp\": \"%d\", \"X-Metadata\": \"%s\"}",
                escapeJson(getSessionId()), escapeJson(id), System.currentTimeMillis(), escapeJson(deletion));
//        appendToLocalLog("Deletion", "", "", "", System.currentTimeMillis(), "", "", "", deletion, "", "", "", id, getSessionId());
        sendLogRequest(requestBody);
    }

    public void logSession(String session) {
        String requestBody = String.format("{\"EventType\": \"Session\", \"AssignmentID\": \"%s\", \"Entry\": \"Session\", \"ClientTimestamp\": \"%d\", \"X-Metadata\": \"%s\"}",
                escapeJson(getSessionId()), System.currentTimeMillis(), escapeJson(session));
        sendLogRequest(requestBody);
    }

    public void logRecall(String id, String prompt) {
        String requestBody = String.format("{\"EventType\": \"Recall\", \"AssignmentID\": \"%s\", \"SubjectID\": \"%s\", \"Entry\": \"Recall\", \"ClientTimestamp\": \"%d\", \"X-Metadata\": \"%s\"}",
                escapeJson(getSessionId()), escapeJson(id), System.currentTimeMillis(), escapeJson(prompt));
//        appendToLocalLog("Recall", "", "", "", System.currentTimeMillis(), "", "", "", prompt, "", "", "", id, getSessionId());
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

    private void appendToLocalLog(String eventType, String insertText, String deleteText, String sourceLocation,
                                  long clientTimestamp, String codeStateSection, String toolInstances,
                                  String editType, String metadata, String codeStateID, String compilable,
                                  String eventID, String subjectID, String assignmentID) {
        String projectBasePath = project.getBasePath();
        if (projectBasePath != null) {
            String logFilePath = projectBasePath + "/showyourwork/showyourwork.log";
            File logFile = new File(logFilePath);

            System.out.println("Project Base Path: " + projectBasePath);
            System.out.println("Log File Path: " + logFilePath);
            System.out.println("Log File Exists: " + logFile.exists());

            if (logFile.exists()) {
                boolean readOnly = !logFile.canWrite();
                if (readOnly) {
                    // Make the file writable
                    logFile.setWritable(true);
                }

                String logEntry = String.format("%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        eventType, insertText, deleteText, sourceLocation, clientTimestamp, codeStateSection,
                        toolInstances, editType, escapeJson(metadata), codeStateID, compilable, eventID,
                        subjectID, assignmentID);

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
                    writer.write(logEntry);
                    System.out.println("Log entry appended successfully.");
                } catch (IOException e) {
                    System.out.println("Error occurred while appending to local log: " + e.getMessage());
                } finally {
                    if (readOnly) {
                        // Revert the file permissions to read-only
                        logFile.setReadOnly();
                    }
                }
            } else {
                System.out.println("Log file does not exist. Skipping local logging.");
            }
        } else {
            System.out.println("Project base path is null. Skipping local logging.");
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