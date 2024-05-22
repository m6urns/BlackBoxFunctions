package org.intellij.sdk.PyTutor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

public class PyTutorDocumentation {
    private static final Integer FONT_SIZE = 14;
    private static final String FONT_FAMILY = "Arial";

    @NotNull
    public static JPanel createDocumentationPanel(PromptLogging promptLogging, JTextArea textArea, FunctionManager functionManager, Project project, StatusSetter statusSetter) {
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

                // Create a JFXPanel to hold the JavaFX content
                JFXPanel fxPanel = new JFXPanel();
                docFrame.add(fxPanel, BorderLayout.CENTER);
                docFrame.setSize(800, 600); // Adjust the size as needed
                docFrame.setLocationRelativeTo(null);
                docFrame.setVisible(true);

                // Initialize JavaFX
                Platform.runLater(() -> {
                    WebView webView = new WebView();
                    WebEngine webEngine = webView.getEngine();
                    webEngine.load("https://www.youtube.com/embed/kVae2hmGdS0");

                    Scene scene = new Scene(webView);
                    fxPanel.setScene(scene);
                });
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
                statusSetter.setStatus("Import statement copied to clipboard. Paste into your Python file.");
            }
        });

        innerPanel.add(docLabel, constraints);
        constraints.gridy = 1; // Move to next row
        innerPanel.add(copyLabel, constraints);
        outerPanel.add(innerPanel, BorderLayout.CENTER);

        return outerPanel;
    }

    @FunctionalInterface
    public interface StatusSetter {
        void setStatus(String status);
    }
}
