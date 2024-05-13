// Based on implementation by Wesley Edwards: https://github.com/WesleyEdwards/PyTutor
package org.intellij.sdk.PyTutor;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

final class PyTutorWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    FunctionManager functionManager = new FunctionManager();
    PyTutorWindowContent toolWindowContent = new PyTutorWindowContent(toolWindow, project, functionManager);
    JPanel contentPanel = toolWindowContent.contentPanel;
    Content content = toolWindow.getContentManager().getFactory().createContent(contentPanel, "", false);

    // Replace the existing content if it exists
    if (toolWindow.getContentManager().getContents().length > 0) {
      toolWindow.getContentManager().removeAllContents(true);
    }

    toolWindow.getContentManager().addContent(content);

    // Register the project listener
    FunctionManager.registerProjectListener();
  }

  private static class PyTutorWindowContent {

    private final JPanel contentPanel = new JPanel();
    private final JTextArea textArea = new JBTextArea();
    private final JPanel submittedTextPanel = new JPanel(new GridLayout(0, 1, 0, 10));
    private final OpenAIClient openAIClient = new OpenAIClient();
    private final JLabel statusLabel = new JLabel();
    private final Project project;
    private final FunctionManager functionManager;

    public PyTutorWindowContent(ToolWindow toolWindow, Project project, FunctionManager functionManager) {
      this.project = project;
      this.functionManager = functionManager;
//      contentPanel.setLayout(new GridBagLayout());
//      GridBagConstraints constraints = new GridBagConstraints();
//      constraints.fill = GridBagConstraints.BOTH;
//      constraints.weightx = 1.0;
//      constraints.weighty = 0.2;
//      contentPanel.add(createTextBoxPanel(), constraints);
//
//      constraints.gridy = 1;
//      constraints.weighty = 0.2;
//      contentPanel.add(createControlsPanel(toolWindow), constraints);
//
//      constraints.gridy = 2;
//      constraints.weighty = 0.7;
//      JBScrollPane submittedTextScrollPane = new JBScrollPane(submittedTextPanel);
//      submittedTextScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//      contentPanel.add(submittedTextScrollPane, constraints);
//
//      constraints.gridy = 3;
//      constraints.weighty = 0.1;
//      statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
//      contentPanel.add(statusLabel, constraints);
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
      submittedTextPanel.setLayout(new BoxLayout(submittedTextPanel, BoxLayout.Y_AXIS));
      JBScrollPane submittedTextScrollPane = new JBScrollPane(submittedTextPanel);
      submittedTextScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      contentPanel.add(submittedTextScrollPane, constraints);

      constraints.gridy = 3;
      constraints.weighty = 0.1;
      statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
      contentPanel.add(statusLabel, constraints);

      // Load existing function definitions from generated_functions.py
      List<String> functionDefinitions = functionManager.readFunctionDefinitions(project);
      for (String functionDefinition : functionDefinitions) {
        String functionName = functionManager.returnFunctionName(functionDefinition);
        addSubmittedTextBox(functionDefinition, functionName);
      }
    }

    // TODO: Get the cursor position correct here. Maybe a border issue?
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
      submitButton.addActionListener(e -> {
        String text = textArea.getText();
        System.out.println("Prompt: " + text);
        sendPromptToOpenAI(text);
        textArea.setText("");
      });
      controlsPanel.add(submitButton);

      JButton clearButton = new JButton("Clear");
      clearButton.addActionListener(e -> {
        textArea.setText("");
      });
      controlsPanel.add(clearButton);

      JButton hideToolWindowButton = new JButton("Hide");
      hideToolWindowButton.addActionListener(e -> toolWindow.hide(null));
      controlsPanel.add(hideToolWindowButton);

      return controlsPanel;
    }

    private void sendPromptToOpenAI(String prompt) {
      setStatus("Sending prompt to OpenAI...");
      OpenAIClient.ProcessedChoice processedChoice = openAIClient.sendPromptToOpenAI(prompt);
      String codeDef = processedChoice.getDef();
      String codeContent = processedChoice.getCode();
      String rawResponse = processedChoice.getRaw();

      if (codeDef.isEmpty() && codeContent.isEmpty()) {
        System.out.println("Error: " + rawResponse);
        addSubmittedTextBox("Error: " + rawResponse, "");
        setStatus("Error: " + rawResponse);
      } else {
        String functionName = functionManager.returnFunctionName(codeDef);
        functionManager.writeToLibrary(project, codeDef, codeContent);
        addSubmittedTextBox(codeDef, functionName);
        setStatus("Function '" + functionName + "' added successfully.");
      }
    }

    public void setStatus(String status) {
      statusLabel.setText(status);
    }

    // TODO: Refine the submitted box so that it looks nicer on the new PyCharm theme
    // Maybe a different colored background or something like that, rounded corners?
    // Will need to add a delete button and maybe a little recall button to each
    // Prompt. Maybe combine the prompt and function boxes. Or do we just need
    // function definitions?
//    private void addSubmittedTextBox(String text, String functionName) {
//      JPanel submittedTextPanel = new JPanel(new BorderLayout());
//      JTextArea submittedTextArea = new JTextArea(text);
//      submittedTextArea.setLineWrap(true);
//      submittedTextArea.setWrapStyleWord(true);
//      submittedTextArea.setEditable(false);
//      submittedTextArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
//
//      // Set the preferred size of the submitted text area
//      submittedTextArea.setPreferredSize(new Dimension(0, submittedTextArea.getPreferredSize().height));
//
//      // Create a scroll pane to hold the submitted text area
//      JScrollPane submittedTextScrollPane = new JBScrollPane(submittedTextArea);
//      submittedTextScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
//      submittedTextScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
//
//      // Create a panel to hold the delete button
//      JPanel deleteButtonPanel = new JPanel(new BorderLayout());
//      deleteButtonPanel.setOpaque(false);
//
//      JButton deleteButton = new JButton("X");
//      deleteButton.setMargin(JBUI.emptyInsets());
//      deleteButton.addActionListener(e -> {
//        functionManager.deleteFunction(project, functionName);
//        this.submittedTextPanel.remove(submittedTextPanel);
//        this.submittedTextPanel.revalidate();
//        this.submittedTextPanel.repaint();
//        setStatus("Function '" + functionName + "' removed successfully.");
//      });
//
//      deleteButtonPanel.add(deleteButton, BorderLayout.SOUTH);
//
//      // Create a panel to hold the scroll pane and the delete button panel
//      JPanel contentPanel = new JPanel(new BorderLayout());
//      contentPanel.add(submittedTextScrollPane, BorderLayout.CENTER);
//      contentPanel.add(deleteButtonPanel, BorderLayout.SOUTH);
//
//      submittedTextPanel.add(contentPanel, BorderLayout.CENTER);
//
//      this.submittedTextPanel.add(submittedTextPanel);
//      this.submittedTextPanel.revalidate();
//      this.submittedTextPanel.repaint();
//    }

    private void addSubmittedTextBox(String text, String functionName) {
      JPanel submittedTextPanel = new JPanel(new BorderLayout());

      JTextArea submittedTextArea = new JTextArea(text);
      submittedTextArea.setEditable(false);
      submittedTextArea.setLineWrap(true);
      submittedTextArea.setWrapStyleWord(true);
      submittedTextArea.setBackground(Color.WHITE);
      submittedTextArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

      JScrollPane submittedTextScrollPane = new JScrollPane(submittedTextArea);
      submittedTextScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
      submittedTextScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      submittedTextScrollPane.setBorder(BorderFactory.createEmptyBorder());

      submittedTextPanel.add(submittedTextScrollPane, BorderLayout.CENTER);

      JButton deleteButton = new JButton("X");
      deleteButton.setPreferredSize(new Dimension(50, 30));
      deleteButton.addActionListener(e -> {
        functionManager.deleteFunction(project, functionName);
        this.submittedTextPanel.remove(submittedTextPanel);
        updateUI();
        setStatus("Function '" + functionName + "' removed successfully.");
      });

      JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      buttonPanel.add(deleteButton);
      submittedTextPanel.add(buttonPanel, BorderLayout.SOUTH);

      this.submittedTextPanel.add(submittedTextPanel);
      updateUI();
    }

    private void updateUI() {
      SwingUtilities.invokeLater(() -> {
        this.submittedTextPanel.revalidate();
        this.submittedTextPanel.repaint();
        if (this.submittedTextPanel.getParent() != null) {
          this.submittedTextPanel.getParent().revalidate();
          this.submittedTextPanel.getParent().repaint();
        }
      });
    }
  }
}