package org.intellij.sdk.PyTutor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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
    private final Project project;
    private final JPanel contentPanel = new JPanel();
    private final JTextArea textArea = new JBTextArea();
    private final JPanel submittedTextPanel = new JPanel(new GridLayout(0, 1, 0, 10));
    private final PromptLogging promptLogging;
    private final OpenAIClient openAIClient;
    private final JTextArea statusLabel = new JTextArea();
    private final FunctionManager functionManager;
    private final Map<String, String> functionPrompts = new HashMap<>();
    private String currentlyEditingFunctionName = null;
    private static final Integer FONT_SIZE = 14;
    private static final String FONT_FAMILY = "Arial";

    public PyTutorWindowContent(ToolWindow toolWindow, Project project, FunctionManager functionManager) {
      this.project = project;
      this.functionManager = functionManager;
      this.promptLogging = new PromptLogging(project);
      this.openAIClient = new OpenAIClient(promptLogging);

      contentPanel.setLayout(new BorderLayout());
      contentPanel.add(createTextBoxPanel(), BorderLayout.NORTH);

      // Creating a panel to hold both the controls and the submitted text panel
      JPanel centerPanel = new JPanel(new BorderLayout());
      centerPanel.add(createControlsPanel(toolWindow), BorderLayout.NORTH);
      centerPanel.add(createSubmittedTextPanel(), BorderLayout.CENTER);

      contentPanel.add(centerPanel, BorderLayout.CENTER);

      statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
      statusLabel.setLineWrap(true);  // Enable line wrap
      statusLabel.setWrapStyleWord(true);  // Wrap at word boundaries
      statusLabel.setEditable(false);  // Make the text area non-editable
      statusLabel.setBackground(contentPanel.getBackground());  // Make background match the panel
      statusLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, FONT_SIZE));
      contentPanel.add(statusLabel, BorderLayout.SOUTH);

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
      JPanel textBoxPanel = new JPanel(new BorderLayout());
      textBoxPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);
      textArea.setFont(new Font(FONT_FAMILY, Font.PLAIN, FONT_SIZE));

      // Set the preferred size for the textArea
      textArea.setPreferredSize(new Dimension(400, 1500));

      JBScrollPane scrollPane = new JBScrollPane(textArea);
      scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED); // Ensure vertical scroll bar appears as needed
      scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
      scrollPane.setPreferredSize(new Dimension(400, 100));

      textBoxPanel.add(scrollPane, BorderLayout.CENTER);

      return textBoxPanel;
    }



    @NotNull
    private JPanel createControlsPanel(ToolWindow toolWindow) {
      JPanel controlsPanel = new JPanel(new BorderLayout());
      JPanel buttonsPanel = new JPanel();

      JButton submitButton = new JButton("Submit");
      submitButton.addActionListener(e -> {
        String text = textArea.getText();
        System.out.println("Prompt: " + text);
        sendPromptToOpenAI(text);
        textArea.setText("");
        currentlyEditingFunctionName = null;
      });
      buttonsPanel.add(submitButton);

      JButton clearButton = new JButton("Clear");
      clearButton.addActionListener(e -> {
        textArea.setText("");
        currentlyEditingFunctionName = null;
        promptLogging.logInteraction("Cleared text area");
      });
      buttonsPanel.add(clearButton);

      JButton hideToolWindowButton = new JButton("Hide");
      hideToolWindowButton.addActionListener(e -> toolWindow.hide(null));
      buttonsPanel.add(hideToolWindowButton);

      controlsPanel.add(buttonsPanel, BorderLayout.NORTH);

      controlsPanel.add(createDocumentationPanel(), BorderLayout.SOUTH);

      return controlsPanel;
    }


    @NotNull
    private JPanel createSubmittedTextPanel() {
      submittedTextPanel.setLayout(new BoxLayout(submittedTextPanel, BoxLayout.Y_AXIS));
      JBScrollPane submittedTextScrollPane = new JBScrollPane(submittedTextPanel);
      submittedTextScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      submittedTextScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

      JPanel panel = new JPanel(new BorderLayout());
      panel.add(submittedTextScrollPane, BorderLayout.CENTER);

      return panel;
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

      JLabel copyLabel = new JLabel("<html><u>Copy import statement to clipboard</u></html>");
      copyLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      copyLabel.setForeground(Color.GRAY);
      copyLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          promptLogging.logInteraction("Clicked copy import statement");
          String exampleFunction = "from generated_functions import *";
          StringSelection selection = new StringSelection(exampleFunction);
          Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
          clipboard.setContents(selection, selection);
          setStatus("Import statement copied to clipboard. Paste into your Python file.");
        }
      });

      innerPanel.add(docLabel, constraints);
      constraints.gridy = 1; // Move to next row
      innerPanel.add(copyLabel, constraints);
      outerPanel.add(innerPanel, BorderLayout.CENTER);

      return outerPanel;
    }

    private void sendPromptToOpenAI(String prompt) {
      setStatus("Sending prompt to OpenAI...");

      // Check if currently editing a function and delete it
      if (currentlyEditingFunctionName != null) {
        deleteFunction(currentlyEditingFunctionName);
        currentlyEditingFunctionName = null;
      }

      // Send the prompt to OpenAI and process the response
      OpenAIClient.ProcessedChoice processedChoice = openAIClient.sendPromptToOpenAI(prompt);
      String codeDef = processedChoice.getDef();
      String codeContent = processedChoice.getCode();
      String rawResponse = processedChoice.getRaw();
      String uid = processedChoice.getUid();

      // Handle the response from OpenAI
      if (rawResponse.startsWith("InvalidPrompt:")) {
        String errorMessage = "Error: " + rawResponse;
        System.out.println(errorMessage);
        setStatus(errorMessage);
      } else {
        if (codeDef.isEmpty() && codeContent.isEmpty()) {
          String errorMessage = "Error: " + rawResponse;
          System.out.println(errorMessage);
          setStatus(errorMessage);
        } else {
          String functionName = functionManager.returnFunctionName(codeDef);
          System.out.println("Function name: " + functionName);

          // Check if a function with the same name already exists
          if (functionPrompts.containsKey(functionName)) {
            promptLogging.logError(uid, "Function '" + functionName + "' already exists.");
            setStatus("Function '" + functionName + "' already exists. Provide a unique function name in your prompt.");
          } else {
            functionManager.writeToLibrary(project, codeDef, codeContent, prompt, uid);
            functionPrompts.put(functionName, prompt);
            addSubmittedTextBox(codeDef, functionName);
            setStatus("Function '" + functionName + "' added successfully.");
          }
        }
      }
    }

    public void setStatus(String status) {
      statusLabel.setText(status);
    }

    private void deleteFunction(String functionName) {
      System.out.println("Deleting function '" + functionName + "'");
      String uid = functionManager.getFunctionUIDs(project, functionName);
      functionManager.deleteFunction(project, functionName);

      // Remove the function's UI component
      for (Component comp : submittedTextPanel.getComponents()) {
        if (comp instanceof JPanel) {
          JPanel containerPanel = (JPanel) comp;
          JTextArea textArea = (JTextArea) ((JScrollPane) ((BorderLayout) ((JPanel) containerPanel.getComponent(0)).getLayout()).getLayoutComponent(BorderLayout.CENTER)).getViewport().getView();
          if (textArea.getText().contains(functionName)) {
            submittedTextPanel.remove(containerPanel);
            break;
          }
        }
      }
      functionPrompts.remove(functionName);
      promptLogging.logDeletion(uid, functionName);
      updateUI();
      setStatus("Function '" + functionName + "' removed successfully.");
    }

    private void addSubmittedTextBox(String text, String functionName) {
      System.out.println("Adding submitted text box for function '" + functionName + "'");

      JPanel containerPanel = new JPanel();
      containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.Y_AXIS));

      JPanel submittedTextPanel = new JPanel(new BorderLayout());
      submittedTextPanel.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY),
              BorderFactory.createEmptyBorder(5, 5, 5, 5)));
      submittedTextPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55)); // Set a maximum height for the panel

      String functionDefinition = text.replace("def ", "");

      JTextArea submittedTextArea = new JTextArea(functionDefinition);
      submittedTextArea.setEditable(false);
      submittedTextArea.setLineWrap(true);
      submittedTextArea.setWrapStyleWord(true);
      submittedTextArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      submittedTextArea.setBackground(null);
      submittedTextArea.setFont(new Font(FONT_FAMILY, Font.PLAIN, FONT_SIZE));

      String prompt = functionPrompts.get(functionName);
      if (prompt != null) {
        submittedTextPanel.setToolTipText("Prompt: " + prompt);

        // Add mouse listener to recall the prompt on click
        submittedTextArea.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            textArea.setText("");
            textArea.setText(prompt);
            currentlyEditingFunctionName = functionName;
            setStatus("Prompt for function '" + functionName + "' loaded for editing.");
            String uid = functionManager.getFunctionUIDs(project, functionName);
            promptLogging.logRecall(uid, prompt);
          }
        });
      } else {
        System.out.println("No prompt found for function '" + functionName + "'");
      }

      JScrollPane submittedTextScrollPane = new JBScrollPane(submittedTextArea);
      submittedTextScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
      submittedTextScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      submittedTextScrollPane.setBorder(BorderFactory.createEmptyBorder());
      submittedTextScrollPane.setOpaque(false);
      submittedTextScrollPane.getViewport().setOpaque(false);

      JButton deleteButton = new JButton(AllIcons.Actions.GC);
      deleteButton.setPreferredSize(new Dimension(30, 42));
      deleteButton.setMargin(new Insets(0, 0, 0, 0));
      deleteButton.addActionListener(e -> deleteFunction(functionName));

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
