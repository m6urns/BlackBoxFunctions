package org.intellij.sdk.PyTutor;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

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
                JTextPane textPane = new JTextPane();
                textPane.setEditable(false);
                textPane.setContentType("text/html");

                String htmlContent = "<html><body style='font-family: " + FONT_FAMILY + "; font-size: " + FONT_SIZE + "pt;'>" +
                        "Importing your generated functions<br><br>" +
                        "You can add your generated functions to your Python code by importing the generated_functions module.<br>" +
                        "You can import all of the functions you create by adding the following line to the top of your .py file:<br><br>" +
                        "from generated_functions import *<br><br>" +
                        "For a demo of PyTutor, please click <a href=\"https://usu-my.sharepoint.com/:v:/g/personal/a02398138_aggies_usu_edu/EY-4KYK2hnZGusECcESykcsBl1bPYocCxFs5062e4Oiobg?e=YgcyVp&nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJTdHJlYW1XZWJBcHAiLCJyZWZlcnJhbFZpZXciOiJTaGFyZURpYWxvZy1MaW5rIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXcifX0%3D\">here</a>." +
                        "</body></html>";

                textPane.setText(htmlContent);

                textPane.addHyperlinkListener(new HyperlinkListener() {
                    @Override
                    public void hyperlinkUpdate(HyperlinkEvent e) {
                        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            try {
                                Desktop.getDesktop().browse(e.getURL().toURI());
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                });

                docFrame.add(new JScrollPane(textPane), BorderLayout.CENTER);
                docFrame.setSize(800, 200);
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
