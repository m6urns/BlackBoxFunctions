package org.intellij.sdk.PyTutor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FunctionManager implements RunManagerListener {
    public static void writeToLibrary(Project project, String functionCode) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Path libraryPath = PathManager.getLibraryPath();
            try {
                Files.createDirectories(libraryPath.getParent());
                Files.writeString(libraryPath, functionCode, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                System.out.println("Function code written to: " + libraryPath);
            } catch (IOException e) {
                System.err.println("Error writing to library file: " + e.getMessage());
            }

            ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                ApplicationManager.getApplication().invokeLater(() -> {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        reloadProject(libraryPath);
                        PathManager.addPluginDirToPythonPath(project);
                    });
                });
            }, "Updating Project Settings", false, project);
        });
    }

    private static void reloadProject(Path filePath) {
        VirtualFile virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(filePath);
        if (virtualFile != null) {
            RefreshQueue.getInstance().refresh(true, false, null, virtualFile);
            System.out.println("Project reloaded after writing to library file.");
        } else {
            System.err.println("Failed to find virtual file for: " + filePath);
        }
    }

    public static void deleteLibraryFile(Project project) {
        Path libraryPath = PathManager.getLibraryPath();
        try {
            Files.deleteIfExists(libraryPath);
            System.out.println("Library file deleted: " + libraryPath);
        } catch (IOException e) {
            System.err.println("Error deleting library file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        updatePythonPath(settings);
    }

    @Override
    public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        updatePythonPath(settings);
    }

    private void updatePythonPath(RunnerAndConfigurationSettings settings) {
        if (settings.getConfiguration() instanceof PythonRunConfiguration) {
            PythonRunConfiguration configuration = (PythonRunConfiguration) settings.getConfiguration();
            String pytutorPath = PathManager.getPluginDirPath();
            String currentPythonPath = configuration.getEnvs().getOrDefault("PYTHONPATH", "");
            if (!currentPythonPath.contains(pytutorPath)) {
                configuration.getEnvs().put("PYTHONPATH", currentPythonPath + java.io.File.pathSeparator + pytutorPath);
            }
        }
    }

    public static void registerProjectListener() {
        ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
                deleteLibraryFile(project);
            }
        });
    }
}