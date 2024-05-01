package org.intellij.sdk.PyTutor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FunctionManager {
    private static final String LIBRARY_FILE_NAME = "generated_functions.py";

    public static void writeToLibrary(Project project, String functionCode) {
        Path libraryPath = getLibraryPath(project);
        try {
            // TODO: Expand this to also write the prompt above the function in generated_functions
            // will allow for improved logging in showyourwork
            // Better integration of ShowYourWork with PyTutor would allow for more detailed logging
            // potentially allowing for analyzing the prompt writing process in greater detail
            Files.writeString(libraryPath, functionCode + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            // Reload the project after writing to the library file
            reloadProject(libraryPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void reloadProject(Path filePath) {
        ApplicationManager.getApplication().invokeLater(() -> {
            VirtualFile virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(filePath);
            if (virtualFile != null) {
                RefreshQueue.getInstance().refresh(true, false, null, virtualFile);
            }
        });
    }

    public static void deleteLibraryFile(Project project) {
        Path libraryPath = getLibraryPath(project);
        try {
            Files.deleteIfExists(libraryPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // TODO: Create a function to delete a specific function from the library file
    // An option to call the prompt back into the prompt window might be useful, would
    // need to delete the existing function at the same time

    private static Path getLibraryPath(Project project) {
        String projectBasePath = project.getBasePath();
        return Paths.get(projectBasePath, LIBRARY_FILE_NAME);
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