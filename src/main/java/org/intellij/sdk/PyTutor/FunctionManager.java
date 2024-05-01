package org.intellij.sdk.PyTutor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FunctionManager {
    private static final String LIBRARY_FILE_NAME = "generated_functions.py";
    private static final String PLUGIN_DIR_NAME = ".pytutor";

    public static void writeToLibrary(Project project, String functionCode) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Path libraryPath = getLibraryPath();
            try {
                Files.createDirectories(libraryPath.getParent());
                Files.writeString(libraryPath, functionCode + "\\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                System.out.println("Function code written to: " + libraryPath);
            } catch (IOException e) {
                System.err.println("Error writing to library file: " + e.getMessage());
            }

            ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                ApplicationManager.getApplication().invokeLater(() -> {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        reloadProject(libraryPath);
                        addPluginDirToPythonPath(project);
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
        Path libraryPath = getLibraryPath();
        try {
            Files.deleteIfExists(libraryPath);
            System.out.println("Library file deleted: " + libraryPath);
        } catch (IOException e) {
            System.err.println("Error deleting library file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Path getLibraryPath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, PLUGIN_DIR_NAME, LIBRARY_FILE_NAME);
    }

    private static void addPluginDirToPythonPath(Project project) {
        Sdk pythonSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (pythonSdk != null) {
            ApplicationManager.getApplication().runWriteAction(() -> {
                SdkModificator sdkModificator = pythonSdk.getSdkModificator();
                String pluginDirPath = getLibraryPath().getParent().toString();
                VirtualFile pluginVirtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(pluginDirPath));

                if (pluginVirtualFile != null) {
                    boolean found = false;
                    for (VirtualFile root : sdkModificator.getRoots(OrderRootType.CLASSES)) {
                        if (root.getPath().equals(pluginVirtualFile.getPath())) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        sdkModificator.addRoot(pluginVirtualFile, OrderRootType.CLASSES);
                    }
                } else {
                    System.err.println("Failed to find or create a virtual file for: " + pluginDirPath);
                }

                sdkModificator.commitChanges();  // Commit changes here, within the same scope
            });
        } else {
            System.err.println("Python SDK not found for the project.");
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
