package org.intellij.sdk.BlackBoxFunctions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import com.intellij.execution.RunnerAndConfigurationSettings;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathManager {
    public static final String FUNCTION_MANAGER_FILE_NAME = "generated_functions.py";
    public static final String PLUGIN_DIR_NAME = ".blackbox_functions";

    public static Path getPluginDirPath(Project project) {
        String basePath = project.getBasePath();
        if (basePath != null) {
            return Paths.get(basePath, PLUGIN_DIR_NAME);
        } else {
            throw new IllegalStateException("Project base directory not found for project: " + project.getName());
        }
    }

    public static void addPluginDirToPythonPath(Project project) {
        Sdk pythonSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (pythonSdk != null) {
            ApplicationManager.getApplication().runWriteAction(() -> {
                SdkModificator sdkModificator = pythonSdk.getSdkModificator();
                String pluginDirPath = getPluginDirPath(project).toString();
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

    public static void updatePythonPath(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings.getConfiguration() instanceof PythonRunConfiguration configuration) {
            String pytutorPath = getPluginDirPath(configuration.getProject()).toString();
            String currentPythonPath = configuration.getEnvs().getOrDefault("PYTHONPATH", "");
            if (!currentPythonPath.contains(pytutorPath)) {
                configuration.getEnvs().put("PYTHONPATH", currentPythonPath + java.io.File.pathSeparator + pytutorPath);
            }
        }
    }

    public static Sdk getCurrentPythonSdk(Project project) {
        return ProjectRootManager.getInstance(project).getProjectSdk();
    }

    public static void printCurrentPythonSdk(Project project) {
        Sdk pythonSdk = getCurrentPythonSdk(project);
        if (pythonSdk != null) {
            System.out.println("Current Python SDK: " + pythonSdk.getHomePath());
        } else {
            System.out.println("No Python SDK found for the project.");
        }
    }
}
