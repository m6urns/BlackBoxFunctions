package org.intellij.sdk.PyTutor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

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
                try {
                    URI uri = new URI("https://www.youtube-nocookie.com/embed/kVae2hmGdS0?si=kuSsVbIkmFmDX1ec");
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(uri);
                    } else {
                        System.err.println("Desktop not supported. Cannot open the URL.");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
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
