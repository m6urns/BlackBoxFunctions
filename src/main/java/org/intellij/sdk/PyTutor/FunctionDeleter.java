package org.intellij.sdk.PyTutor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class FunctionDeleter {
    public void deleteFunction(Project project, String functionName) {
        Path baseDirPath = Path.of(Objects.requireNonNull(project.getBasePath()));
        Path functionManagerFilePath = baseDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);
        Path compiledFilePath = baseDirPath.resolve(functionName + ".pyc");

        try {
            // Delete the compiled function file
            Files.deleteIfExists(compiledFilePath);
            System.out.println("Compiled function file deleted: " + compiledFilePath);

            // Remove the function definition and wrapper from the function manager file
            Path tempFile = Files.createTempFile("pytutor", "temp");
            try (var lines = Files.lines(functionManagerFilePath)) {
                Files.write(tempFile, lines
                        .filter(line -> !line.contains("def " + functionName + "("))
                        .filter(line -> !line.contains("from " + functionName + " import " + functionName))
                        .filter(line -> !line.contains("return " + functionName + "(*args, **kwargs)"))
                        .toList());
            }
            Files.move(tempFile, functionManagerFilePath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Function definition and wrapper removed from function manager file: " + functionName);

            // Reload the project
            ApplicationManager.getApplication().invokeLater(() -> {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    FunctionManager.reloadProject(project);
                });
            });
        } catch (IOException e) {
            System.err.println("Error deleting function: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void deleteLibraryFiles(Project project) {
        Path baseDirPath = Path.of(Objects.requireNonNull(project.getBasePath()));
        Path functionManagerFilePath = baseDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);

        try (var lines = Files.lines(functionManagerFilePath)) {
            lines.filter(line -> line.startsWith("# def "))
                    .map(line -> line.substring(2))
                    .map(functionDefinition -> functionDefinition.split("\\(")[0].split("\\s+")[1])
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
}
