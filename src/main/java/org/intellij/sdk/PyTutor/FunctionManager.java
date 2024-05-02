package org.intellij.sdk.PyTutor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import org.jetbrains.annotations.NotNull;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FunctionManager implements RunManagerListener {
    public static void writeToLibrary(Project project, String functionName, String functionCode) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Path pluginDirPath = PathManager.getPluginDirPath(project);
            Path functionFilePath = pluginDirPath.resolve(functionName + ".py");
            Path functionManagerFilePath = pluginDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);

            try {
                Files.createDirectories(pluginDirPath);
                Files.writeString(functionFilePath, functionCode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                String importStatement = String.format("from %s import %s\n", functionName, functionName);
                Files.writeString(functionManagerFilePath, importStatement, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                System.out.println("Function code written to: " + functionFilePath);
            } catch (IOException e) {
                System.err.println("Error writing to function file: " + e.getMessage());
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    reloadProject(project);
                    PathManager.addPluginDirToPythonPath(project);
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
        Path pluginDirPath = PathManager.getPluginDirPath(project);
        try {
            Files.list(pluginDirPath)
                    .filter(path -> path.toString().endsWith(".py"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                            System.out.println("Library file deleted: " + path);
                        } catch (IOException e) {
                            System.err.println("Error deleting library file: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error listing library files: " + e.getMessage());
            e.printStackTrace();
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