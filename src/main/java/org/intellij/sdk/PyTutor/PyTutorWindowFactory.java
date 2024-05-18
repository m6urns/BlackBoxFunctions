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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

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
    private final PromptLogging promptLogging = new PromptLogging();
    private final OpenAIClient openAIClient = new OpenAIClient(promptLogging);
    private final JLabel statusLabel = new JLabel();
    private final Project project;
    private final FunctionManager functionManager;
    private final Map<String, String> functionPrompts = new HashMap<>();

    public PyTutorWindowContent(ToolWindow toolWindow, Project project, FunctionManager functionManager) {
      this.project = project;
      this.functionManager = functionManager;

      contentPanel.setLayout(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.fill = GridBagConstraints.BOTH;
      constraints.weightx = 1.0;
      constraints.weighty = 0.2;
      contentPanel.add(createTextBoxPanel(), constraints);

      constraints.gridy = 1;
      constraints.weighty = 0.05;
      contentPanel.add(createControlsPanel(toolWindow), constraints);

      constraints.gridy = 2;
      constraints.weighty = 0.02;
      contentPanel.add(createDocumentationPanel(), constraints);

      constraints.gridy = 3;
      constraints.weighty = 0.7;
      submittedTextPanel.setLayout(new BoxLayout(submittedTextPanel, BoxLayout.Y_AXIS));
      JBScrollPane submittedTextScrollPane = new JBScrollPane(submittedTextPanel);
      submittedTextScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      submittedTextScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      contentPanel.add(submittedTextScrollPane, constraints);

      constraints.gridy = 4;
      constraints.weighty = 0.1;
      statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
      contentPanel.add(statusLabel, constraints);

      // Load existing function prompts from generated_functions.py
      List<String> functionPrompts = functionManager.readFunctionPrompts(project);
      List<String> functionDefinitions = functionManager.readFunctionDefinitions(project);
      List<String> functionUIDs = functionManager.readFunctionUIDs(project);

      for (int i = 0; i < functionDefinitions.size(); i++) {
        String functionDefinition = functionDefinitions.get(i);
        String functionName = functionManager.returnFunctionName(functionDefinition);
        String prompt = (i < functionPrompts.size()) ? functionPrompts.get(i) : "";
        String uid = (i < functionUIDs.size()) ? functionUIDs.get(i) : "";
        System.out.println("Function name: " + functionName + ", Prompt: " + prompt + ", UID: " + uid);
        this.functionPrompts.put(functionName, prompt);
      }

      // Load existing function definitions from generated_functions.py and add submitted text boxes
      for (String functionDefinition : functionDefinitions) {
        String functionName = functionManager.returnFunctionName(functionDefinition);
        System.out.println("Loaded function definition: " + functionDefinition);
        System.out.println("Function name: " + functionName);
        addSubmittedTextBox(functionDefinition, functionName);
      }

      // Send the session ID and UIDs to the logging server
      String uids = String.join(",", functionUIDs);
      promptLogging.logSession(uids);
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
      scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));

      textArea.setCaretPosition(0);
      textBoxPanel.add(scrollPane, constraints);

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

    @NotNull
    private JPanel createDocumentationPanel() {
      JPanel outerPanel = new JPanel(new BorderLayout());
      JPanel innerPanel = new JPanel(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.insets = new Insets(2, 0, 2, 0); // Top, left, bottom, right padding

      JLabel docLabel = new JLabel("<html><u>How do I use these functions?</u></html>");
      docLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      docLabel.setForeground(Color.GRAY);
      docLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          promptLogging.logInteraction("Clicked help link");
          JFrame docFrame = new JFrame("Using the PyTutor Plugin");
          JTextArea docArea = new JTextArea();
          docArea.setEditable(false);
          docArea.setText("Importing your generated functions\n\n" +
                  "You can add your generated functions to your Python code by importing the generated_functions module.\n" +
                    "You can import all of the functions you create by adding the following line to the top of your .py file:\n\n" +
                    "from generated_functions import *\n\n");
          docFrame.add(new JScrollPane(docArea), BorderLayout.CENTER);
          docFrame.setSize(750, 200);
          docFrame.setLocationRelativeTo(null);
          docFrame.setVisible(true);
        }
      });

      innerPanel.add(docLabel, constraints);
      outerPanel.add(innerPanel, BorderLayout.CENTER);

      return outerPanel;
    }

    private void sendPromptToOpenAI(String prompt) {
      setStatus("Sending prompt to OpenAI...");
      OpenAIClient.ProcessedChoice processedChoice = openAIClient.sendPromptToOpenAI(prompt);
      String codeDef = processedChoice.getDef();
      String codeContent = processedChoice.getCode();
      String rawResponse = processedChoice.getRaw();
      String uid = processedChoice.getUid();

      if (rawResponse.startsWith("InvalidPrompt:")) {
        String errorMessage = "Error: " + rawResponse;
        System.out.println(errorMessage);
        setStatus(errorMessage);
      } else {
        if (codeDef.isEmpty() && codeContent.isEmpty()) {
          String errorMessage = "Error: " + rawResponse;
          System.out.println(errorMessage);
          addSubmittedTextBox(errorMessage, "");
          setStatus(errorMessage);
        } else {
          String functionName = functionManager.returnFunctionName(codeDef);
          System.out.println("Function name: " + functionName);
          functionManager.writeToLibrary(project, codeDef, codeContent, prompt, uid);
          functionPrompts.put(functionName, prompt);
          addSubmittedTextBox(codeDef, functionName);
          setStatus("Function '" + functionName + "' added successfully.");
        }
      }
    }

    public void setStatus(String status) {
      statusLabel.setText(status);
    }

    private void addSubmittedTextBox(String text, String functionName) {
      System.out.println("Adding submitted text box for function '" + functionName + "'");

      JPanel containerPanel = new JPanel();
      containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.Y_AXIS));

      JPanel submittedTextPanel = new JPanel(new BorderLayout());
      submittedTextPanel.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY),
              BorderFactory.createEmptyBorder(5, 5, 5, 5)));
      submittedTextPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50)); // Set a maximum height for the panel

      String functionDefinition = text.replace("def ", "");

      JTextArea submittedTextArea = new JTextArea(functionDefinition);
      submittedTextArea.setEditable(false);
      submittedTextArea.setLineWrap(true);
      submittedTextArea.setWrapStyleWord(true);
      submittedTextArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      submittedTextArea.setBackground(null);

      String prompt = functionPrompts.get(functionName);
      if (prompt != null) {
        submittedTextPanel.setToolTipText("Prompt: " + prompt);
      } else {
        System.out.println("No prompt found for function '" + functionName + "'");
      }

      JScrollPane submittedTextScrollPane = new JBScrollPane(submittedTextArea);
      submittedTextScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
      submittedTextScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      submittedTextScrollPane.setBorder(BorderFactory.createEmptyBorder());
      submittedTextScrollPane.setOpaque(false);
      submittedTextScrollPane.getViewport().setOpaque(false);

      JButton deleteButton = new JButton("X");
      deleteButton.setPreferredSize(new Dimension(30, 40));
      deleteButton.setMargin(new Insets(0, 0, 0, 0));
      deleteButton.addActionListener(e -> {
        String uid = functionManager.getFunctionUIDs(project, functionName);
        functionManager.deleteFunction(project, functionName);
        this.submittedTextPanel.remove(containerPanel);
        promptLogging.logDeletion(uid, functionName);
        updateUI();
        setStatus("Function '" + functionName + "' removed successfully.");
      });

      JPanel buttonPanel = new JPanel(new BorderLayout());
      buttonPanel.add(deleteButton, BorderLayout.SOUTH);

      submittedTextPanel.add(submittedTextScrollPane, BorderLayout.CENTER);
      submittedTextPanel.add(buttonPanel, BorderLayout.EAST);

      containerPanel.add(submittedTextPanel);

      containerPanel.add(Box.createVerticalStrut(5)); // Add a vertical strut of 5 pixels

      this.submittedTextPanel.add(containerPanel);
      updateUI();

      // Add a ComponentListener to adjust the size when the panel is resized
      submittedTextPanel.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          Dimension preferredSize = new Dimension(submittedTextPanel.getWidth() - deleteButton.getPreferredSize().width - 20,
                  submittedTextArea.getPreferredSize().height);
          submittedTextArea.setPreferredSize(preferredSize);
          submittedTextArea.revalidate();
        }
      });
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