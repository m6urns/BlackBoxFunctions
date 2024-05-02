package org.intellij.sdk.PyTutor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathManager {
    private static final String PLUGIN_DIR_NAME = ".pytutor";
    private static final String LIBRARY_FILE_NAME = "generated_functions.py";

    public static Path getLibraryPath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, PLUGIN_DIR_NAME, LIBRARY_FILE_NAME);
    }

    public static String getPluginDirPath() {
        return getLibraryPath().getParent().toString();
    }

    public static void addPluginDirToPythonPath(Project project) {
        Sdk pythonSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (pythonSdk != null) {
            ApplicationManager.getApplication().runWriteAction(() -> {
                SdkModificator sdkModificator = pythonSdk.getSdkModificator();
                String pluginDirPath = getPluginDirPath();
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

                sdkModificator.commitChanges();
            });
        } else {
            System.err.println("Python SDK not found for the project.");
        }
    }
}
