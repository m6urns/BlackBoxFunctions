// Based on implementation by Wesley Edwards: https://github.com/WesleyEdwards/PyTutor
package org.intellij.sdk.PyTutor;

import io.github.sashirestela.openai.BaseSimpleOpenAI;
import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.domain.chat.ChatRequest;
import io.github.sashirestela.openai.domain.chat.message.ChatMsgSystem;
import io.github.sashirestela.openai.domain.chat.message.ChatMsgUser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class OpenAIClient {
    private final BaseSimpleOpenAI openAI;

    public OpenAIClient() {
        String apiKey = readApiKeyFromResources();
        this.openAI = SimpleOpenAI.builder().apiKey(apiKey).build();
    }

    public ProcessedChoice sendPromptToOpenAI(String prompt) {
        String instructions = generateInstructions(prompt);
        System.out.println("Generated instructions:");
        System.out.println(instructions);

        ChatRequest chatRequest = ChatRequest.builder()
                .model("gpt-3.5-turbo-1106")
                .message(new ChatMsgSystem("You are an AI programming assistant. You write clear, concise, and correct Python code based on the provided prompt."))
                .message(new ChatMsgUser(instructions))
                .temperature(0.7)
                .maxTokens(300)
                .build();

        var futureChat = openAI.chatCompletions().create(chatRequest);
        var chatResponse = futureChat.join();
        String rawResponse = chatResponse.firstContent();

        System.out.println("Raw response:");
        System.out.println(rawResponse);

        ProcessedChoice processedChoice = getFunctionFromGPT(rawResponse);
        System.out.println("Processed function definition:");
        System.out.println(processedChoice.getDef());
        System.out.println("Processed function code:");
        System.out.println(processedChoice.getCode());

        return processedChoice;
    }

    public static class ProcessedChoice {
        private final String def;
        private final String code;
        private final String raw;

        public ProcessedChoice(String def, String code, String raw) {
            this.def = def;
            this.code = code;
            this.raw = raw;
        }

        public String getDef() {
            return def;
        }

        public String getCode() {
            return code;
        }

        public String getRaw() {
            return raw;
        }
    }

    private ProcessedChoice getFunctionFromGPT(String gptRes) {
        String[] parts = gptRes.split("# Start");
        if (parts.length < 2) {
            return new ProcessedChoice("", "", "No function found with # Start and # End comments");
        }
        String afterStart = parts[1];
        String[] codeParts = afterStart.split("# End");
        if (codeParts.length == 0) {
            return new ProcessedChoice("", "", "No function found with # Start and # End comments");
        }
        String codeContent = codeParts[0].trim();
        String[] lines = codeContent.split("\\n");
        String codeDef = null;
        for (String line : lines) {
            if (line.contains("def")) {
                codeDef = line.trim();
                break;
            }
        }
        if (codeDef == null) {
            return new ProcessedChoice("", "", "Unable to find the function definition");
        }
        return new ProcessedChoice(codeDef, codeContent, gptRes);
    }

    private static String generateInstructions(String text) {
        String[] lines = {
                "Write a Python function with the following specifications:",
                "- " + text,
                "- Directly above the function definition, write the following comment: # Start",
                "- At the very end of the function, write the following comment: # End",
                "- Provide only the function definition and the code inside the function, without any additional explanations or text."
        };
        return String.join("\n", lines);
    }

    // TODO: I would like to find a way to generate a test function for the generated function
    // Allowing for some automatic testing of the generated function, we don't
    // want students having to write test code, but we also don't want to just pass
    // un tested code to the students if we can avoid it.

    private String readApiKeyFromResources() {
        try (InputStream inputStream = getClass().getResourceAsStream("/pytutor.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            String apiKey = properties.getProperty("OPENAI_API_KEY");
            if (apiKey != null && !apiKey.isEmpty()) {
//                System.out.println("API key found in pytutor.properties file: " + apiKey);
                return apiKey;
            } else {
                throw new IllegalStateException("API key not found in pytutor.properties file");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading pytutor.properties file", e);
        }
    }
}