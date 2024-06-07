package org.intellij.sdk.BlackBoxFunctions;

import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FunctionManager implements RunManagerListener {
    private final FunctionWriter functionWriter;
    private final FunctionDeleter functionDeleter;

    public FunctionManager() {
        this.functionWriter = new FunctionWriter();
        this.functionDeleter = new FunctionDeleter();
    }

    public void writeToLibrary(Project project, String functionDefinition, String functionCode, String prompt, String uid) {
        functionWriter.writeToLibrary(project, functionDefinition, functionCode, prompt, uid);
    }

    public String returnFunctionName(String functionDefinition) {
        return FunctionWriter.extractFunctionName(functionDefinition);
    }

    public List<String> readFunctionDefinitions(Project project) {
        return FunctionWriter.readFunctionDefinitions(project);
    }

    public List<String> readFunctionPrompts(Project project) {
        return FunctionWriter.readFunctionPrompts(project);
    }

    public List<String> readFunctionUIDs(Project project) {
        return FunctionWriter.readFunctionUIDs(project);
    }

    public void deleteFunction(Project project, String functionName) {
        functionDeleter.deleteFunction(project, functionName);
    }

    public String getFunctionUIDs(Project project, String functionName) {
        List<String> functionDefinitions = FunctionWriter.readFunctionDefinitions(project);
        List<String> functionUIDs = FunctionWriter.readFunctionUIDs(project);

        for (int i = 0; i < functionDefinitions.size(); i++) {
            String functionDefinition = functionDefinitions.get(i);
            String currentFunctionName = returnFunctionName(functionDefinition);
            if (currentFunctionName.equals(functionName)) {
                return functionUIDs.get(i);
            }
        }

        return "";
    }

    public static void reloadProject(Project project) {
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir != null) {
            RefreshQueue.getInstance().refresh(true, true, null, projectDir);
            System.out.println("Project reloaded after writing to function file.");
        } else {
            System.err.println("Failed to find project directory for project: " + project.getName());
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
//                FunctionDeleter.deleteLibraryFiles(project);
            }
        });
    }
}