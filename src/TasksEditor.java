import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class TasksEditor {
    private static final String[] HIGHLIGHTERS = {
        SyntaxConstants.SYNTAX_STYLE_NONE.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_JAVA.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_PYTHON.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_HTML.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_C.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_CSHARP.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_XML.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_SQL.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_JSON.replace("text/", ""),
        //SyntaxConstants.SYNTAX_STYLE_BASH.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_RUBY.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_GO.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_KOTLIN.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_SCALA.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_PERL.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_PHP.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_LUA.replace("text/", ""),
        SyntaxConstants.SYNTAX_STYLE_MARKDOWN.replace("text/", "")
    };

    public static void createTasks(CodeEditor codeEditor, Tasks tasks) {
        JDialog dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(codeEditor), "Tasks (.mango.yaml)", true);
        dialog.setSize(new Dimension(800, 400));
        dialog.setMinimumSize(new Dimension(800, 400));
        dialog.setMaximumSize(new Dimension(800, 400));
        dialog.setLayout(new BorderLayout());
        
        // Center the dialog
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(codeEditor));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        dialog.add(scrollPane, BorderLayout.CENTER);

        JPanel tasksPanel = new JPanel();
        tasksPanel.setLayout(new BoxLayout(tasksPanel, BoxLayout.Y_AXIS));
        mainPanel.add(tasksPanel, BorderLayout.NORTH);

        for (Map.Entry<String, Tasks.Task> entry : tasks.getTasks().entrySet()) {
            JPanel taskPanel = createTaskPanel(entry.getKey(), entry.getValue(), tasksPanel);
            tasksPanel.add(taskPanel);
        }

        // Create a panel for the add task button
        JPanel addTaskPanel = new JPanel();
        addTaskPanel.setLayout(new BorderLayout());
        JButton addTaskButton = new JButton("+");
        addTaskButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        addTaskButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JPanel taskPanel = createTaskPanel("", new Tasks.Task(), tasksPanel);
                tasksPanel.add(taskPanel);
                tasksPanel.revalidate();
                tasksPanel.repaint();
            }
        });
        addTaskPanel.add(addTaskButton, BorderLayout.WEST);
        mainPanel.add(addTaskPanel, BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("Done");
        JButton applyButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyTasks(tasksPanel, tasks);
                codeEditor.reloadTasks();
                exportToYaml(new File(codeEditor.projectDir, ".mango.yaml").getAbsolutePath(), tasks);
                dialog.dispose();
            }
        });

        applyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyTasks(tasksPanel, tasks);
                codeEditor.reloadTasks();
                exportToYaml(new File(codeEditor.projectDir, ".mango.yaml").getAbsolutePath(), tasks);
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        buttonPanel.add(okButton);
        //buttonPanel.add(applyButton);
        //buttonPanel.add(cancelButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private static JPanel createTaskPanel(String taskName, Tasks.Task task, JPanel tasksPanel) {
        JPanel taskPanel = new JPanel(new BorderLayout());

        JPanel fieldsPanel = new JPanel();
        fieldsPanel.setLayout(new BoxLayout(fieldsPanel, BoxLayout.X_AXIS));

        JTextField nameField = new JTextField(taskName);
        setPlaceholder(nameField, "Task name");

        JTextField extensionsField = new JTextField(String.join(", ", task.getExtensions()));
        setPlaceholder(extensionsField, "Extensions separated by comma");

        JComboBox<String> highlighterBox = new JComboBox<>(HIGHLIGHTERS);
        highlighterBox.setSelectedItem(task.getHighlighter().replace("text/", ""));
        highlighterBox.setMaximumSize(new Dimension(100, highlighterBox.getPreferredSize().height));

        JTextField commandField = new JTextField(task.getCommand());
        setPlaceholder(commandField, "Command");
        
        // Add action listener to commandField
        commandField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openCommandEditorDialog(commandField);
            }
        });

        Dimension fieldSize = new Dimension(100, nameField.getPreferredSize().height);
        nameField.setMaximumSize(fieldSize);
        extensionsField.setMaximumSize(fieldSize);
        highlighterBox.setMaximumSize(new Dimension(100, highlighterBox.getPreferredSize().height));
        commandField.setMaximumSize(new Dimension(400, commandField.getPreferredSize().height));
        commandField.setPreferredSize(new Dimension(400, commandField.getPreferredSize().height));

        JButton removeButton = new JButton(
                new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/close.png")).getImage()
                        .getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
        removeButton.setToolTipText("Remove task");
        removeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tasksPanel.remove(taskPanel);
                tasksPanel.revalidate();
                tasksPanel.repaint();
            }
        });

        taskPanel.add(removeButton, BorderLayout.WEST);
        fieldsPanel.add(nameField);
        fieldsPanel.add(extensionsField);
        fieldsPanel.add(highlighterBox);
        fieldsPanel.add(commandField);

        taskPanel.add(fieldsPanel, BorderLayout.CENTER);

        return taskPanel;
    }

    private static void setPlaceholder(JTextField textField, String placeholder) {
        textField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (textField.getText().equals(placeholder)) {
                    textField.setText("");
                    textField.setForeground(Color.BLACK);
                }
            }

            public void focusLost(java.awt.event.FocusEvent evt) {
                if (textField.getText().isEmpty()) {
                    textField.setForeground(Color.GRAY);
                    textField.setText(placeholder);
                }
            }
        });
    }

    private static void applyTasks(JPanel tasksPanel, Tasks tasks) {
        Map<String, Tasks.Task> newTasks = new HashMap<>();

        for (int i = 0; i < tasksPanel.getComponentCount(); i++) {
            JPanel taskPanel = (JPanel) tasksPanel.getComponent(i);
            JPanel fieldsPanel = (JPanel) taskPanel.getComponent(1);

            JTextField nameField = (JTextField) fieldsPanel.getComponent(0);
            JTextField extensionsField = (JTextField) fieldsPanel.getComponent(1);
            JComboBox<String> highlighterBox = (JComboBox<String>) fieldsPanel.getComponent(2);
            JTextField commandField = (JTextField) fieldsPanel.getComponent(3);

            String name = nameField.getText();
            String extensions = extensionsField.getText();
            String highlighter = (String) highlighterBox.getSelectedItem();
            String command = commandField.getText();

            if (!name.isEmpty() && !name.equals("Task name")) {
                Tasks.Task newTask = new Tasks.Task();
                newTask.setExtensions(new ArrayList<>(List.of(extensions.split(",\\s*"))));
                newTask.setHighlighter("text/" + highlighter);
                newTask.setCommand(command);
                newTasks.put(name, newTask);
            }
        }

        tasks.setTasks(newTasks);
    }

    private static void exportToYaml(String path, Tasks tasks) {
        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(path)) {
            yaml.dump(tasks, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void openCommandEditorDialog(JTextField commandField) {
        JDialog commandDialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(commandField), "Edit Command", true);
        commandDialog.setSize(new Dimension(600, 400));
        commandDialog.setLayout(new BorderLayout());
        commandDialog.setLocationRelativeTo(commandField);

        JTextArea commandTextArea = new JTextArea(commandField.getText());
        JScrollPane scrollPane = new JScrollPane(commandTextArea);
        commandDialog.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                commandField.setText(commandTextArea.getText());
                commandDialog.dispose();
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                commandDialog.dispose();
            }
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        commandDialog.add(buttonPanel, BorderLayout.SOUTH);

        commandDialog.setVisible(true);
    }
}
