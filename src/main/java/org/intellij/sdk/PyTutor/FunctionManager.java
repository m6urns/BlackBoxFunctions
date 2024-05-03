package org.intellij.sdk.PyTutor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import org.jetbrains.annotations.NotNull;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.projectRoots.Sdk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

// TODO: Resolve the import errors in pycharm, maybe there is a way to suppress that particular error or override its behavior?

public class FunctionManager implements RunManagerListener {
    public static void writeToLibrary(Project project, String functionName, String functionCode) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Path baseDirPath = Path.of(project.getBasePath());
            Path generatedFunctionsFilePath = baseDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);

            try {
                compilePyFile(project, functionName, functionCode);
                String functionDefinition = String.format("def %s(*args, **kwargs):\n    from %s import %s\n    return %s(*args, **kwargs)\n\n", functionName, functionName, functionName, functionName);
                Files.writeString(generatedFunctionsFilePath, functionDefinition, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                System.out.println("Function definition written to generated_functions.py");
            } catch (IOException e) {
                System.err.println("Error writing to generated functions file: " + e.getMessage());
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    reloadProject(project);
                });
            });
        });
    }


    private static void reloadProject(Project project) {
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir != null) {
            RefreshQueue.getInstance().refresh(true, true, null, projectDir);
            System.out.println("Project reloaded after writing to function file.");
        } else {
            System.err.println("Failed to find project directory for project: " + project.getName());
        }
    }

    public static void deleteLibraryFiles(Project project) {
        Path baseDirPath = Path.of(project.getBasePath());
        Path functionManagerFilePath = baseDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);

        try (var lines = Files.lines(functionManagerFilePath)) {
            lines.map(line -> line.split("\\s+")[1])
                    .map(functionName -> functionName.split("\\.")[0])
                    .distinct()
                    .forEach(functionName -> {
                        Path compiledFilePath = baseDirPath.resolve(functionName + ".pyc");
                        try {
                            Files.deleteIfExists(compiledFilePath);
                            System.out.println("Compiled function file deleted: " + compiledFilePath);
                        } catch (IOException e) {
                            System.err.println("Error deleting compiled function file: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error reading function manager file: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            Files.deleteIfExists(functionManagerFilePath);
            System.out.println("Function manager file deleted: " + functionManagerFilePath);
        } catch (IOException e) {
            System.err.println("Error deleting function manager file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void compilePyFile(Project project, String functionName, String functionCode) {
        Sdk pythonSdk = PathManager.getCurrentPythonSdk(project);
        if (pythonSdk != null) {
            String pythonExecutable = pythonSdk.getHomePath();
            String compileScriptPath = extractCompileScript();
            Path baseDirPath = Path.of(project.getBasePath());
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

    @Override
    public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        PathManager.updatePythonPath(settings);
    }

    @Override
    public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        PathManager.updatePythonPath(settings);
    }

    public static void registerProjectListener() {
        ApplicationManager.getApplication().getMessageBus().connect().subscribe(com.intellij.openapi.project.ProjectManager.TOPIC, new com.intellij.openapi.project.ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
                deleteLibraryFiles(project);
            }
        });
    }
}