package org.intellij.sdk.PyTutor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FunctionWriter {
    public void writeToLibrary(Project project, String functionDefinition, String functionCode, String prompt, String uid) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Path baseDirPath = Path.of(Objects.requireNonNull(project.getBasePath()));
            Path generatedFunctionsFilePath = baseDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);

            try {
                String functionName = extractFunctionName(functionDefinition);
                compilePyFile(project, functionName, functionCode);

                String strippedPrompt = prompt.replace("\n", " ").replace("\r", "");

                String commentedFunctionDefinition = "# " + functionDefinition + " # Prompt: " + strippedPrompt + " # UID: " + uid + "\n";
                String functionDefinitionInGeneratedFile = String.format("def %s(*args, **kwargs):\n    from %s import %s\n    return %s(*args, **kwargs)\n\n", functionName, functionName, functionName, functionName);
                Files.writeString(generatedFunctionsFilePath, commentedFunctionDefinition, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
//                Files.writeString(generatedFunctionsFilePath, commentedFunctionDefinition, StandardOpenOption.APPEND);
                Files.writeString(generatedFunctionsFilePath, functionDefinitionInGeneratedFile, StandardOpenOption.APPEND);
                System.out.println("Function definition written to generated_functions.py");
            } catch (IOException e) {
                System.err.println("Error writing to generated functions file: " + e.getMessage());
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    FunctionManager.reloadProject(project);
                });
            });
        });
    }

    public static String extractFunctionName(String functionDefinition) {
        String[] parts = functionDefinition.split("\\s+");
        if (parts.length >= 2 && parts[0].equals("def")) {
            return parts[1].split("\\(")[0];
        }
        return "";
    }

    public static List<String> readFunctionDefinitions(Project project) {
        Path baseDirPath = Path.of(Objects.requireNonNull(project.getBasePath()));
        Path generatedFunctionsFilePath = baseDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);

        try (var lines = Files.lines(generatedFunctionsFilePath)) {
            return lines.filter(line -> line.startsWith("# def "))
                    .map(line -> line.substring(2, line.indexOf('#', 2) >= 0 ? line.indexOf('#', 2) : line.length()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error reading function definitions: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public static List<String> readFunctionPrompts(Project project) {
        Path baseDirPath = Path.of(Objects.requireNonNull(project.getBasePath()));
        Path generatedFunctionsFilePath = baseDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);

        try (var lines = Files.lines(generatedFunctionsFilePath)) {
            List<String> prompts = lines.filter(line -> line.contains("# Prompt:"))
                    .map(line -> {
                        int promptStart = line.indexOf("# Prompt:") + "# Prompt:".length();
                        int uidStart = line.indexOf("# UID:");
                        String prompt;
                        if (uidStart >= 0) {
                            prompt = line.substring(promptStart, uidStart).trim();
                        } else {
                            prompt = line.substring(promptStart).trim();
                        }
                        System.out.println("Extracted prompt: " + prompt);
                        return prompt;
                    })
                    .collect(Collectors.toList());
            System.out.println("Loaded prompts: " + prompts);
            return prompts;
        } catch (IOException e) {
            System.err.println("Error reading function prompts: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public static List<String> readFunctionUIDs(Project project) {
        Path baseDirPath = Path.of(Objects.requireNonNull(project.getBasePath()));
        Path generatedFunctionsFilePath = baseDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);

        try (var lines = Files.lines(generatedFunctionsFilePath)) {
            List<String> uids = lines.filter(line -> line.contains("# UID:"))
                    .map(line -> {
                        int uidStart = line.indexOf("# UID:") + "# UID:".length();
                        String uid = line.substring(uidStart).trim();
                        System.out.println("Extracted UID: " + uid);
                        return uid;
                    })
                    .collect(Collectors.toList());
            System.out.println("Loaded UIDs: " + uids);
            return uids;
        } catch (IOException e) {
            System.err.println("Error reading function UIDs: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private static void compilePyFile(Project project, String functionName, String functionCode) {
        Sdk pythonSdk = PathManager.getCurrentPythonSdk(project);
        if (pythonSdk != null) {
            String pythonExecutable = pythonSdk.getHomePath();
            String compileScriptPath = extractCompileScript();
            Path baseDirPath = Path.of(Objects.requireNonNull(project.getBasePath()));
            Path functionFilePath = baseDirPath.resolve(functionName + ".py");
            Path compiledFilePath = baseDirPath.resolve(functionName + ".pyc");

            try {
                Files.writeString(functionFilePath, functionCode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                ProcessBuilder processBuilder = new ProcessBuilder(
                        pythonExecutable,
                        compileScriptPath,
                        functionFilePath.toString(),
                        compiledFilePath.toString()
                );

                Process process = processBuilder.start();
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    System.out.println("Function compiled to: " + compiledFilePath);
                } else {
                    System.err.println("Compilation failed with exit code: " + exitCode);
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Error compiling function file: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Delete the temporary .py file
                try {
                    Files.deleteIfExists(functionFilePath);
                } catch (IOException e) {
                    System.err.println("Error deleting temporary .py file: " + e.getMessage());
                }

                // Delete the temporary compile.py script file
                Path tempCompileScriptPath = Path.of(compileScriptPath);
                try {
                    Files.deleteIfExists(tempCompileScriptPath);
                } catch (IOException e) {
                    System.err.println("Error deleting temporary compile.py script: " + e.getMessage());
                }
            }
        } else {
            System.out.println("No Python SDK found for the project.");
        }
    }

    private static String extractCompileScript() {
        try {
            Path tempDir = Files.createTempDirectory("pytutor");
            Path tempCompileScriptPath = tempDir.resolve("compile.py");
            try (InputStream inputStream = FunctionManager.class.getResourceAsStream("/compile.py")) {
                assert inputStream != null;
                Files.copy(inputStream, tempCompileScriptPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempCompileScriptPath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error extracting compile.py script", e);
        }
    }
}
