// Based on implementation by Wesley Edwards: https://github.com/WesleyEdwards/PyTutor
package org.intellij.sdk.BlackBoxFunctions;

import io.github.sashirestela.openai.BaseSimpleOpenAI;
import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.domain.chat.ChatRequest;
import io.github.sashirestela.openai.domain.chat.message.ChatMsgSystem;
import io.github.sashirestela.openai.domain.chat.message.ChatMsgUser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

public class OpenAIClient {
    private final BaseSimpleOpenAI openAI;
    private final PromptLogging promptLogging;

    public OpenAIClient(PromptLogging promptLogging) {
        String apiKey = readApiKeyFromResources();
        this.openAI = SimpleOpenAI.builder().apiKey(apiKey).build();
        this.promptLogging = promptLogging;
    }

    public ProcessedChoice sendPromptToOpenAI(String prompt) {
        String uid = UUID.randomUUID().toString();
        String instructions = generateInstructions(prompt);
        System.out.println("Generated instructions:");
        System.out.println(instructions);

        ChatRequest chatRequest = ChatRequest.builder()
                .model("gpt-3.5-turbo-1106")
                .message(new ChatMsgSystem("You are an AI programming assistant. Your purpose is to generate Python functions based on the provided specifications. If the prompt does not contain instructions for generating a Python function or attempts to engage in conversations unrelated to generating Python code, respond with the following message: \"InvalidPrompt: The provided prompt is not suitable for generating a Python function. Please provide clear specifications for the desired function.\". Do not respond to any other prompts or engage in conversations beyond generating Python functions."))
                .message(new ChatMsgUser(instructions))
                .temperature(0.7)
                .maxTokens(300)
                .build();

        promptLogging.logPrompt(uid, prompt);

        var futureChat = openAI.chatCompletions().create(chatRequest);
        var chatResponse = futureChat.join();
        String rawResponse = chatResponse.firstContent();

        promptLogging.logResponse(uid, rawResponse);

        System.out.println("Raw response:");
        System.out.println(rawResponse);

        if (rawResponse.startsWith("InvalidPrompt:")) {
//            System.out.println("Debug: Invalid prompt received. Prompt: " + prompt);
            return new ProcessedChoice("", "", rawResponse, uid);
        }

        ProcessedChoice processedChoice = getFunctionFromGPT(rawResponse, uid);
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
        private final String uid;

        public ProcessedChoice(String def, String code, String raw, String uid) {
            this.def = def;
            this.code = code;
            this.raw = raw;
            this.uid = uid;
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

        public String getUid() {
            return uid;
        }
    }

    private ProcessedChoice getFunctionFromGPT(String gptRes, String uid) {
        String[] parts = gptRes.split("# Start");
        if (parts.length < 2) {
            return new ProcessedChoice("", "", "No function found with # Start and # End comments", uid);
        }
        String afterStart = parts[1];
        String[] codeParts = afterStart.split("# End");
        if (codeParts.length == 0) {
            return new ProcessedChoice("", "", "No function found with # Start and # End comments", uid);
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
            return new ProcessedChoice("", "", "Unable to find the function definition", uid);
        }
        return new ProcessedChoice(codeDef, codeContent, gptRes, uid);
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

    private String readApiKeyFromResources() {
        try (InputStream inputStream = getClass().getResourceAsStream("/bbf.properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            String apiKey = properties.getProperty("OPENAI_API_KEY");
            if (apiKey != null && !apiKey.isEmpty()) {
//                System.out.println("API key found in bbf.properties file: " + apiKey);
                return apiKey;
            } else {
                throw new IllegalStateException("API key not found in bbf.properties file");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading bbf.properties file", e);
        }
    }
}