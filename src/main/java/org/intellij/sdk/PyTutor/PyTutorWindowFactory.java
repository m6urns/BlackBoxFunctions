// Based on implementation by Wesley Edwards: https://github.com/WesleyEdwards/PyTutor
package org.intellij.sdk.PyTutor;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

final class PyTutorWindowFactory implements ToolWindowFactory, DumbAware {

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    PyTutorWindowContent toolWindowContent = new PyTutorWindowContent(toolWindow, project);
    Content content = ContentFactory.getInstance().createContent(toolWindowContent.getContentPanel(), "", false);
    toolWindow.getContentManager().addContent(content);

    // Register the project listener
    FunctionManager.registerProjectListener();
  }

  private static class PyTutorWindowContent {

    private final JPanel contentPanel = new JPanel();
    private final JTextArea textArea = new JBTextArea();
    private final JPanel submittedTextPanel = new JPanel(new GridLayout(0, 1, 0, 10));
    private final OpenAIClient openAIClient = new OpenAIClient();
    private final Project project;

    public PyTutorWindowContent(ToolWindow toolWindow, Project project) {
      this.project = project;
      contentPanel.setLayout(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.fill = GridBagConstraints.BOTH;
      constraints.weightx = 1.0;
      constraints.weighty = 0.2;
      contentPanel.add(createTextBoxPanel(), constraints);

      constraints.gridy = 1;
      constraints.weighty = 0.2;
      contentPanel.add(createControlsPanel(toolWindow), constraints);

      constraints.gridy = 2;
      constraints.weighty = 0.7;
      JBScrollPane submittedTextScrollPane = new JBScrollPane(submittedTextPanel);
      submittedTextScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      contentPanel.add(submittedTextScrollPane, constraints);
    }

    @NotNull
    private JPanel createTextBoxPanel() {
      JPanel textBoxPanel = new JPanel(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.fill = GridBagConstraints.BOTH;
      constraints.weightx = 1.0;
      constraints.weighty = 0.2;
      constraints.insets = JBUI.insets(10);

      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);

      JBScrollPane scrollPane = new JBScrollPane(textArea);
      textBoxPanel.add(scrollPane, constraints);
      textArea.setCaretPosition(0);

      return textBoxPanel;
    }

    @NotNull
    private JPanel createControlsPanel(ToolWindow toolWindow) {
      JPanel controlsPanel = new JPanel();
      JButton submitButton = new JButton("Submit");
      submitButton.addActionListener(e ->  {
        String text = textArea.getText();
        System.out.println("Prompt: " + text);
        addSubmittedTextBox("User prompt:\n" + text);
        sendPromptToOpenAI(text);
        textArea.setText("");
      });
      controlsPanel.add(submitButton);

      JButton clearButton = new JButton("Clear");
      clearButton.addActionListener(e ->  {
        textArea.setText("");
      });
      controlsPanel.add(clearButton);

      JButton hideToolWindowButton = new JButton("Hide");
      hideToolWindowButton.addActionListener(e -> toolWindow.hide(null));
      controlsPanel.add(hideToolWindowButton);

      return controlsPanel;
    }

    private void sendPromptToOpenAI(String prompt) {
      OpenAIClient.ProcessedChoice processedChoice = openAIClient.sendPromptToOpenAI(prompt);
      String codeDef = processedChoice.getDef();
      String codeContent = processedChoice.getCode();
      String rawResponse = processedChoice.getRaw();

      if (codeDef.isEmpty() && codeContent.isEmpty()) {
        System.out.println("Error: " + rawResponse);
        addSubmittedTextBox("Error: " + rawResponse);
      } else {
        FunctionManager.writeToLibrary(project, codeContent);
        addSubmittedTextBox("Generated code definition:\n" + codeDef);
      }
    }

    private void addSubmittedTextBox(String text) {
      JTextArea submittedTextArea = new JTextArea(text);
      submittedTextArea.setLineWrap(true);
      submittedTextArea.setWrapStyleWord(true);
      submittedTextArea.setEditable(false);
      submittedTextArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      submittedTextPanel.add(submittedTextArea);
      submittedTextPanel.revalidate();
    }

    public JPanel getContentPanel() {
      return contentPanel;
    }
  }
}