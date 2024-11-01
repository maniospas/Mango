import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class ThemeDialog extends JDialog {
    private static final long serialVersionUID = 1261713001735453126L;
    private JComboBox<String> themeComboBox;
    private JComboBox<Integer> fontSizeComboBox;
    private JButton applyButton;
    private static final String CONFIG_FILE = "mango.yaml";

    public ThemeDialog(JFrame parentFrame) {
        super(parentFrame, "Themes & info", true);

        // Initialize components
        themeComboBox = new JComboBox<>();
        fontSizeComboBox = new JComboBox<>();
        applyButton = new JButton("Done");

        // Populate theme combo box with available look and feels
        String currentLookAndFeel = UIManager.getLookAndFeel().getName();
        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            themeComboBox.addItem(info.getName());
            if (info.getName().equals(currentLookAndFeel)) {
                themeComboBox.setSelectedItem(info.getName());
            }
        }

        // Populate font size combo box with options
        for (int i = 8; i <= 24; i += 2) {
            fontSizeComboBox.addItem(i);
        }

        // Set the layout
        setLayout(new GridLayout(3, 1));
        JTextField credits = new JTextField("Copyright (C) 2024 Emmanouil Krasanakis");
        credits.setEditable(false);
        add(credits);
        add(themeComboBox);
        //add(fontSizeComboBox);
        add(applyButton);

        applyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveThemeToFile();
                dispose();
            }
        });

        JDialog thisObj = this;
        themeComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedTheme = (String) themeComboBox.getSelectedItem();
                //Integer selectedFontSize = (Integer) fontSizeComboBox.getSelectedItem();
                setTheme(selectedTheme);
                //setFontSize(selectedFontSize);
                SwingUtilities.updateComponentTreeUI(parentFrame);
                parentFrame.revalidate();
                parentFrame.repaint();
                thisObj.revalidate();
                thisObj.repaint();
            }
        });

        // Load theme from file if it exists
        loadThemeFromFile();

        // Set dialog properties
        setSize(300, 150);
        setLocationRelativeTo(parentFrame);
    }

    private static void setTheme(String theme) {
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if (theme.equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveThemeToFile() {
        String selectedTheme = (String) themeComboBox.getSelectedItem();
        Yaml yaml = new Yaml();
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        yaml = new Yaml(options);

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            yaml.dump(Map.of("theme", Map.of("name", selectedTheme)), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadThemeFromFile() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try {
                Yaml yaml = new Yaml(new Constructor(Map.class));
                Map<String, Map<String, String>> config = yaml.load(Files.newInputStream(Paths.get(CONFIG_FILE)));
                String themeName = config.get("theme").get("name");
                setTheme(themeName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
    		try {
    			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    		} catch (Exception ex) {
    			ex.printStackTrace();
    			try {
    			    for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
    			        if ("Nimbus".equals(info.getName())) {
    			            UIManager.setLookAndFeel(info.getClassName());
    			            break;
    			        }
    			    }
    			} catch (Exception e) {
    				e.printStackTrace();
    			} 
    		}
        }
    }
}
