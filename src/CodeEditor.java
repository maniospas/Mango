import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class CodeEditor extends JFrame {

	private static final long serialVersionUID = 1431536465167923296L;
	private JTree projectTree;
	private DefaultTreeModel treeModel;
	private JTabbedPane tabbedPane;
	private JTabbedPane consoleTabbedPane;
	private Map<File, RSyntaxTextArea> openFilesMap = new HashMap<>();
	private Map<File, Boolean> dirtyMap = new HashMap<>();
	private File projectDir;
	private File currentFile;
	private LanguageConfig languageConfig;

	public CodeEditor() {
		setTitle("Jace - Just another code editor");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(800, 600);
		setLocationRelativeTo(null);
		setExtendedState(JFrame.MAXIMIZED_BOTH);

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		splitPane.setDividerLocation(400);

		JSplitPane upperSplitPane = new JSplitPane();
		upperSplitPane.setDividerLocation(200);

		// Project Explorer
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Project");
		treeModel = new DefaultTreeModel(root);
		projectTree = new JTree(treeModel);
		projectTree.setCellRenderer(new CustomTreeCellRenderer());
		projectTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		projectTree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) {
					int row = projectTree.getClosestRowForLocation(e.getX(), e.getY());
					projectTree.setSelectionRow(row);
					TreePath path = projectTree.getPathForRow(row);
					if (path != null) {
						DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
						File selectedFile = (File) selectedNode.getUserObject();
						showFileContextMenu(e.getX(), e.getY(), selectedFile);
					}
				}
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					int row = projectTree.getClosestRowForLocation(e.getX(), e.getY());
					TreePath path = projectTree.getPathForRow(row);
					if (path != null) {
						projectTree.setSelectionPath(path);
						DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
						if (selectedNode != null && selectedNode.isLeaf()) {
							File selectedFile = (File) selectedNode.getUserObject();
							openFile(selectedFile);
						}
					}
				}
			}
		});
		projectTree.addTreeExpansionListener(new TreeExpansionListener() {
			@Override
			public void treeExpanded(TreeExpansionEvent event) {
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
				File file = (File) node.getUserObject();
				if (file.isDirectory())
					addFilesToNode(node, file);
			}

			@Override
			public void treeCollapsed(TreeExpansionEvent event) {
				// No action needed
			}
		});
		JScrollPane treeScrollPane = new JScrollPane(projectTree);
		upperSplitPane.setLeftComponent(treeScrollPane);

		// Editor Panel with Tabs
		tabbedPane = new JTabbedPane();
		upperSplitPane.setRightComponent(tabbedPane);

		splitPane.setTopComponent(upperSplitPane);

		// Console Tabs
		consoleTabbedPane = new JTabbedPane();
		splitPane.setBottomComponent(consoleTabbedPane);

		getContentPane().add(splitPane, BorderLayout.CENTER);

		// Main Toolbar
		JToolBar mainToolBar = new JToolBar();

		JButton restartButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/open.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		restartButton.setToolTipText("Open project");
		restartButton.addActionListener(e -> restartApplication());
		mainToolBar.add(restartButton);

		JButton runButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/run.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		runButton.setToolTipText("Run (Ctrl+R)");
		runButton.addActionListener(e -> runCommand());
		mainToolBar.add(runButton);

		JButton saveButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/save.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		saveButton.setToolTipText("Save current file (Ctrl+S)");
		saveButton.addActionListener(e -> saveCurrentFile());
		mainToolBar.add(saveButton);

		// Search and Replace Toolbar
		JToolBar searchReplaceToolBar = new JToolBar();

		// Search Button
		JButton searchButton = new JButton(
				new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/search.png")).getImage()
						.getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		searchButton.setToolTipText("Search (Ctrl+F)");
		searchButton.addActionListener(e -> showSearchDialog());
		searchReplaceToolBar.add(searchButton);

		// Replace Button
		JButton replaceButton = new JButton(
				new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/replace.png")).getImage()
						.getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		replaceButton.setToolTipText("Replace (Ctrl+H)");
		replaceButton.addActionListener(e -> showReplaceDialog());
		searchReplaceToolBar.add(replaceButton);

		// Edit Toolbar
		JToolBar editToolBar = new JToolBar();

		JButton cutButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/cut.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		cutButton.setToolTipText("Cut (Ctrl+X)");
		cutButton.addActionListener(e -> cut());
		editToolBar.add(cutButton);

		JButton copyButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/copy.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		copyButton.setToolTipText("Copy (Ctrl+C)");
		copyButton.addActionListener(e -> copy());
		editToolBar.add(copyButton);

		JButton pasteButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/paste.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		pasteButton.setToolTipText("Paste (Ctrl+V)");
		pasteButton.addActionListener(e -> paste());
		editToolBar.add(pasteButton);

		JPanel toolBarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		toolBarPanel.add(mainToolBar);
		toolBarPanel.add(searchReplaceToolBar);
		toolBarPanel.add(editToolBar);

		getContentPane().add(toolBarPanel, BorderLayout.NORTH);

		// Set up key bindings
		setupKeyBindings(tabbedPane.getActionMap(), tabbedPane.getInputMap());

		// Ask for the project folder on startup
		File newProjectDir = selectProjectDirectory();
		if (newProjectDir == null)
			System.exit(0);
		openProject(newProjectDir);
		consoleTabbedPane.setVisible(false);

	}

	private File selectProjectDirectory() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Open project directory");
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int result = fileChooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			return fileChooser.getSelectedFile();
		} else {
			return null;
		}
	}

	private void openFile(File file) {
		currentFile = file;
		if (openFilesMap.containsKey(file)) {
			tabbedPane.setSelectedComponent(openFilesMap.get(file).getParent().getParent());
		} else {
			try {
				String content = new String(Files.readAllBytes(file.toPath()));
				RSyntaxTextArea textArea = new RSyntaxTextArea(content, 20, 80);
				textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
				textArea.setCodeFoldingEnabled(true);
				updateSyntaxHighlighter(file, textArea);
				setupKeyBindings(textArea.getActionMap(), textArea.getInputMap());

				RTextScrollPane sp = new RTextScrollPane(textArea);

				// Create tab component with close button
				JPanel tabComponent = new JPanel(new BorderLayout());
				tabComponent.setOpaque(false);
				JLabel tabLabel = new JLabel(file.getName());
				tabLabel.setToolTipText(file.getAbsolutePath());
				JButton closeButton = new JButton(
						new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/close.png")).getImage()
								.getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
				closeButton.setPreferredSize(new Dimension(16, 16));
				closeButton.addActionListener(e -> closeFile(file));
				closeButton.setFocusable(false);
				tabComponent.add(tabLabel, BorderLayout.WEST);
				tabComponent.add(closeButton, BorderLayout.EAST);

				tabbedPane.addTab(file.getName(), sp);
				tabbedPane.setTabComponentAt(tabbedPane.getTabCount() - 1, tabComponent);
				tabbedPane.setSelectedComponent(sp);

				// Add mouse listener to label for selecting the tab and showing context menu
				tabLabel.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						int tabIndex = tabbedPane.indexOfTabComponent(tabComponent);
						if (tabIndex != -1) {
							tabbedPane.setSelectedIndex(tabIndex);
						}

						if (SwingUtilities.isRightMouseButton(e)) {
							showTabContextMenu(e, tabIndex);
						}
					}
				});

				openFilesMap.put(file, textArea);
				dirtyMap.put(file, false); // File initially not dirty

				textArea.getDocument().addDocumentListener(new DocumentListener() {
					@Override
					public void insertUpdate(DocumentEvent e) {
						setDirty(file, true);
					}

					@Override
					public void removeUpdate(DocumentEvent e) {
						setDirty(file, true);
					}

					@Override
					public void changedUpdate(DocumentEvent e) {
						setDirty(file, true);
					}
				});

				tabbedPane.addChangeListener(new ChangeListener() {
					public void stateChanged(ChangeEvent e) {
						if (tabbedPane.getTabCount() > 0 && tabbedPane.getSelectedComponent() != null) {
							for (Map.Entry<File, RSyntaxTextArea> entry : openFilesMap.entrySet()) {
								if (entry.getValue().getParent().getParent() == tabbedPane.getSelectedComponent()) {
									currentFile = entry.getKey();
									updateTreeSelection(currentFile);
									break;
								}
							}
						}
					}
				});

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Method to show context menu
	private void showTabContextMenu(MouseEvent e, int tabIndex) {
		JPopupMenu contextMenu = new JPopupMenu();

		JMenuItem closeAllItem = new JMenuItem("Close all");
		closeAllItem.addActionListener(ev -> closeAllTabs());

		JMenuItem closeToRightItem = new JMenuItem("Close all to the right");
		closeToRightItem.addActionListener(ev -> closeTabsToRight(tabIndex));

		JMenuItem closeToLeftItem = new JMenuItem("Close all to the left");
		closeToLeftItem.addActionListener(ev -> closeTabsToLeft(tabIndex));

		JMenuItem closeOthersItem = new JMenuItem("Close all others");
		closeOthersItem.addActionListener(ev -> closeOtherTabs(tabIndex));

		contextMenu.add(closeAllItem);
		contextMenu.add(closeToRightItem);
		contextMenu.add(closeToLeftItem);
		contextMenu.add(closeOthersItem);

		contextMenu.show(e.getComponent(), e.getX(), e.getY());
	}

	// Methods for each context menu action
	private void closeAllTabs() {
		boolean hasUnsavedChanges = dirtyMap.values().stream().anyMatch(dirty -> dirty);
		if (hasUnsavedChanges) {
			int option = JOptionPane.showConfirmDialog(this, "Save changes to the files being closed?",
					"Save Files", JOptionPane.YES_NO_CANCEL_OPTION);
			if (option == JOptionPane.YES_OPTION)
				saveAllFiles();
			else
				return;
		}
		openFilesMap.clear();
		dirtyMap.clear();
		tabbedPane.removeAll();
	}

	private void closeTabsToRight(int tabIndex) {
		boolean hasUnsavedChanges = false;
		for (int i = tabbedPane.getTabCount() - 1; i > tabIndex; i--) {
			Component component = tabbedPane.getComponentAt(i);
			if (component instanceof RTextScrollPane) {
				RSyntaxTextArea textArea = (RSyntaxTextArea) ((RTextScrollPane) component).getViewport().getView();
				File file = getFileForTextArea(textArea);
				if (file != null && this.dirtyMap.get(file)) {
					hasUnsavedChanges = true;
					break;
				}
			}
		}
		if (hasUnsavedChanges) {
			int option = JOptionPane.showConfirmDialog(this, "Save changes to the files being closed?",
					"Save Files", JOptionPane.YES_NO_CANCEL_OPTION);
			if (option == JOptionPane.YES_OPTION)
				saveAllFiles();
			else
				return;
		}
		updateInverseTextAreaMap();
		for (int i = tabbedPane.getTabCount() - 1; i > tabIndex; i--) {
			Component component = tabbedPane.getComponentAt(i);
			if (component instanceof RTextScrollPane) {
				RSyntaxTextArea textArea = (RSyntaxTextArea) ((RTextScrollPane) component).getViewport().getView();
				File file = getFileForTextArea(textArea);
				if (file != null) {
					openFilesMap.remove(file);
					dirtyMap.remove(file);
				}
			}
			tabbedPane.remove(i);
		}
	}

	private void closeTabsToLeft(int tabIndex) {
		boolean hasUnsavedChanges = false;
		for (int i = tabIndex - 1; i >= 0; i--) {
			Component component = tabbedPane.getComponentAt(i);
			if (component instanceof RTextScrollPane) {
				RSyntaxTextArea textArea = (RSyntaxTextArea) ((RTextScrollPane) component).getViewport().getView();
				File file = getFileForTextArea(textArea);
				if (file != null && this.dirtyMap.get(file)) {
					hasUnsavedChanges = true;
					break;
				}
			}
		}
		if (hasUnsavedChanges) {
			int option = JOptionPane.showConfirmDialog(this, "Save changes to the files being closed?",
					"Save Files", JOptionPane.YES_NO_CANCEL_OPTION);
			if (option == JOptionPane.YES_OPTION)
				saveAllFiles();
			else
				return;
		}
		updateInverseTextAreaMap();
		for (int i = tabIndex - 1; i >= 0; i--) {
			Component component = tabbedPane.getComponentAt(i);
			if (component instanceof RTextScrollPane) {
				RSyntaxTextArea textArea = (RSyntaxTextArea) ((RTextScrollPane) component).getViewport().getView();
				File file = getFileForTextArea(textArea);
				if (file != null) {
					openFilesMap.remove(file);
					dirtyMap.remove(file);
				}
			}
			tabbedPane.remove(i);
		}
	}

	private void closeOtherTabs(int tabIndex) {
		boolean hasUnsavedChanges = false;
		for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) 
			if(i!=tabIndex){
			Component component = tabbedPane.getComponentAt(i);
			if (component instanceof RTextScrollPane) {
				RSyntaxTextArea textArea = (RSyntaxTextArea) ((RTextScrollPane) component).getViewport().getView();
				File file = getFileForTextArea(textArea);
				if (file != null && this.dirtyMap.get(file)) {
					hasUnsavedChanges = true;
					break;
				}
			}
		}
		if (hasUnsavedChanges) {
			int option = JOptionPane.showConfirmDialog(this, "Save changes to the files being closed?",
					"Save Files", JOptionPane.YES_NO_CANCEL_OPTION);
			if (option == JOptionPane.YES_OPTION)
				saveAllFiles();
			else
				return;
		}
		updateInverseTextAreaMap();
		for (int i = tabbedPane.getTabCount() - 1; i > tabIndex; i--) {
			Component component = tabbedPane.getComponentAt(i);
			if (component instanceof RTextScrollPane) {
				RSyntaxTextArea textArea = (RSyntaxTextArea) ((RTextScrollPane) component).getViewport().getView();
				File file = getFileForTextArea(textArea);
				if (file != null) {
					openFilesMap.remove(file);
					dirtyMap.remove(file);
				}
			}
			tabbedPane.remove(i);
		}
		for (int i = tabIndex - 1; i >= 0; i--) {
			Component component = tabbedPane.getComponentAt(i);
			if (component instanceof RTextScrollPane) {
				RSyntaxTextArea textArea = (RSyntaxTextArea) ((RTextScrollPane) component).getViewport().getView();
				File file = getFileForTextArea(textArea);
				if (file != null) {
					openFilesMap.remove(file);
					dirtyMap.remove(file);
				}
			}
			tabbedPane.remove(i);
		}
	}
	
	private HashMap<RSyntaxTextArea, File> areaToFile;

	private void updateInverseTextAreaMap() {
		areaToFile = new HashMap<RSyntaxTextArea, File>();
		for (Map.Entry<File, RSyntaxTextArea> entry : openFilesMap.entrySet()) 
			areaToFile.put(entry.getValue(), entry.getKey());
	}

	// Helper method to get the File associated with an RSyntaxTextArea
	private File getFileForTextArea(RSyntaxTextArea textArea) {
		return areaToFile.get(textArea);
	}

	private void updateSyntaxHighlighter(File file, RSyntaxTextArea textArea) {
		if (languageConfig != null)
			for (LanguageConfig.Language language : languageConfig.getLanguages().values()) {
				for (String extension : language.getExtensions()) {
					if (file.getName().endsWith("." + extension)) {
						textArea.setSyntaxEditingStyle("text/" + language.getHighlighter());
						return;
					}
				}
			}
		// handle some common languages based on extension
		if (file.getName().endsWith(".c"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_C);
		if (file.getName().endsWith(".cpp") || file.getName().endsWith(".h"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
		if (file.getName().endsWith(".java") || file.getName().endsWith(".jar"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
		if (file.getName().endsWith(".js"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
		if (file.getName().endsWith(".css"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CSS);
		if (file.getName().endsWith(".html"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML);
		if (file.getName().endsWith(".cs"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CSHARP);
		if (file.getName().endsWith(".csv"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CSV);
		if (file.getName().endsWith(".go"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_GO);
		if (file.getName().endsWith(".ini"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_INI);
		if (file.getName().endsWith(".yaml"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_YAML);
		if (file.getName().endsWith(".json"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON_WITH_COMMENTS);
		if (file.getName().endsWith(".lua"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_LUA);
		if (file.getName().endsWith(".lisp") || file.getName().endsWith(".lsp") || file.getName().endsWith(".l")
				|| file.getName().endsWith(".cl") || file.getName().endsWith(".v"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_LISP);
		if (file.getName().endsWith(".f90") || file.getName().endsWith(".for") || file.getName().endsWith(".f")
				|| file.getName().endsWith(".f77"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_FORTRAN);
		if (file.getName().endsWith(".md"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
		if (file.getName().endsWith(".py"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
	}

	private void setDirty(File file, boolean dirty) {
		dirtyMap.put(file, dirty);
		updateTabLabel(file, dirty);
	}

	private void saveFile(File file) {
		if (file != null && openFilesMap.containsKey(file)) {
			try {
				RSyntaxTextArea textArea = openFilesMap.get(file);
				Files.write(file.toPath(), textArea.getText().getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
				setDirty(file, false);
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, "Failed to save the file: " + e.toString(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void saveCurrentFile() {
		if (currentFile != null) {
			saveFile(currentFile);
		}
	}

	private void saveAllFiles() {
		for (Map.Entry<File, RSyntaxTextArea> entry : openFilesMap.entrySet()) {
			saveFile(entry.getKey());
		}
	}

	private void promptSaveAllFiles() {
		boolean hasUnsavedChanges = dirtyMap.values().stream().anyMatch(dirty -> dirty);
		if (hasUnsavedChanges) {
			int option = JOptionPane.showConfirmDialog(this, "Save changes to the files being closed?",
					"Save Files", JOptionPane.YES_NO_CANCEL_OPTION);
			if (option == JOptionPane.YES_OPTION) {
				saveAllFiles();
			} else if (option == JOptionPane.CANCEL_OPTION) {
				throw new RuntimeException("Operation cancelled by the user.");
			}
		}
	}

	private void closeFile(File file) {
		if (file != null) {
			if (dirtyMap.getOrDefault(file, false)) {
				int option = JOptionPane.showConfirmDialog(this,
						"Do you want to save changes to " + file.getName() + "?", "Save File",
						JOptionPane.YES_NO_CANCEL_OPTION);
				if (option == JOptionPane.YES_OPTION) {
					saveFile(file);
				} else if (option == JOptionPane.CANCEL_OPTION) {
					return;
				}
			}
			tabbedPane.remove(openFilesMap.get(file).getParent().getParent());
			openFilesMap.remove(file);
			dirtyMap.remove(file);
		}
	}

	private void closeAllFiles() {
		promptSaveAllFiles();
		tabbedPane.removeAll();
		openFilesMap.clear();
		dirtyMap.clear();
	}

	private void runCommand() {
		// Read the YAML configuration
		File yamlFile = new File(projectDir + "/.languages.yaml");
		if (yamlFile.exists()) {
			try {
				languageConfig = readYamlConfig(yamlFile);
				// for(String lang : languageConfig.getLanguages().keySet()) {
				// System.out.println(lang+"
				// "+languageConfig.getLanguages().get(lang).getExtensions());
				// }
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this,
						"Failed to parse the project-specific configuration: " + e.toString(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}

		LanguageConfig.Language runLanguage = null;
		if (this.currentFile != null)
			for (LanguageConfig.Language language : languageConfig.getLanguages().values()) {
				for (String extension : language.getExtensions()) {
					if (this.currentFile.getName().endsWith("." + extension)) {
						runLanguage = language;
						break;
					}
				}
			}

		try {
			if (runLanguage == null) {
				JOptionPane.showMessageDialog(this,
						"Please add a configuration with which to run the current file's extension.", "Nothing to run",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			promptSaveAllFiles();
		} catch (RuntimeException e) {
			return;
		}
		JTextPane consoleOutput = new JTextPane();
		consoleOutput.setContentType("text/html");
		consoleOutput.setEditable(false);
		consoleOutput.setBackground(Color.BLACK);
		JScrollPane scrollPane = new JScrollPane(consoleOutput);

		JPanel tabComponent = new JPanel(new BorderLayout());
		tabComponent.setOpaque(false);
		JLabel tabLabel = new JLabel("Console");
		JButton stopButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/stop.png"))
				.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
		stopButton.setToolTipText("Force stop");
		stopButton.setPreferredSize(new Dimension(16, 16));
		JButton closeButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/close.png"))
				.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
		closeButton.setPreferredSize(new Dimension(16, 16));
		closeButton.setToolTipText("Close");

		JPanel buttonPanel = new JPanel();
		buttonPanel.add(stopButton);
		buttonPanel.add(closeButton);

		tabComponent.add(tabLabel, BorderLayout.WEST);
		tabComponent.add(buttonPanel, BorderLayout.EAST);
		
		if(!consoleTabbedPane.isVisible()) {
			JSplitPane splitPane = (JSplitPane) getContentPane().getComponent(0);
			consoleTabbedPane.setVisible(true);
			splitPane.setDividerLocation(400); 
		}
		consoleTabbedPane.addTab("Run Output", scrollPane);
		consoleTabbedPane.setTabComponentAt(consoleTabbedPane.getTabCount() - 1, tabComponent);
		consoleTabbedPane.setSelectedComponent(scrollPane);
		String filepath = currentFile.getAbsolutePath().replace("\\", "/");
		String command = runLanguage.getCommand()
				.replace("{path}", filepath.contains("/") ? filepath.substring(0, filepath.lastIndexOf("/") + 1) : ".")
				.replace("{path/}", filepath.contains("/") ? filepath.substring(0, filepath.lastIndexOf("/") + 1) : ".")
				.replace("{path.}", filepath.contains("/") ? filepath.substring(0, filepath.lastIndexOf("/") + 1).replace("/", ".") : ".")
				.replace("{path\\}", filepath.contains("/") ? filepath.substring(0, filepath.lastIndexOf("/") + 1).replace("/", "\\") : ".")
				.replace("{file}",
						filepath.contains(".")
								? filepath.substring(filepath.lastIndexOf("/") + 1, filepath.lastIndexOf("."))
								: filepath.substring(filepath.lastIndexOf("/") + 1))
				.replace("{ext}", filepath.contains(".") ? filepath.substring(filepath.lastIndexOf(".")) : "");
		ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
		processBuilder.directory(projectDir);
		appendAnsiText(consoleOutput, command + "\n\n");
		try {
			Process process = processBuilder.start();
			stopButton.addActionListener(e -> stopProcess(process));
			closeButton.addActionListener(e -> closeConsoleTab(process, scrollPane, consoleOutput));

			new Thread(() -> {
			    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			        String line;
			        while ((line = reader.readLine()) != null) {
			        	String l = line;
			            SwingUtilities.invokeLater(() -> appendAnsiText(consoleOutput, l + "\n"));
			        }
			    } catch (IOException ex) {
			        ex.printStackTrace();
			    }
			    stopButton.setVisible(false);
			}).start();

			new Thread(() -> {
			    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			        String line;
			        while ((line = reader.readLine()) != null) {
			        	String l = line;
			            SwingUtilities.invokeLater(() -> appendAnsiText(consoleOutput, l + "\n"));
			        }
			    } catch (IOException ex) {
			        ex.printStackTrace();
			    }
			}).start();
		} catch (Exception e) {
			appendAnsiText(consoleOutput, e.toString());
		}
	}
	
	private static void appendAnsiText(JTextPane textPane, String text) {
        String html = ansiToHtml(text);
        try {
            HTMLEditorKit kit = (HTMLEditorKit) textPane.getEditorKit();
            StyleSheet styleSheet = kit.getStyleSheet();
            styleSheet.addRule("body { font-family: monospaced; color: white;}");
            kit.insertHTML((HTMLDocument) textPane.getDocument(), textPane.getDocument().getLength(), html, 0, 0, null);
        } catch (BadLocationException | IOException e) {
            e.printStackTrace();
        }
    }

    private static String ansiToHtml(String ansiText) {
        StringBuilder html = new StringBuilder();
        html.append("<pre style='display: inline; margin: 0;'>");

        String ansiRegex = "\\u001B\\[(\\d+;)?(\\d+)?m";
        Pattern pattern = Pattern.compile(ansiRegex);
        Matcher matcher = pattern.matcher(ansiText);
        int lastEnd = 0;

        while (matcher.find()) {
            String prefix = ansiText.substring(lastEnd, matcher.start());
            prefix = convertUnicodeToHtmlEntities(prefix);
            html.append(prefix.replaceAll("<", "&lt;").replaceAll(">", "&gt;"));

            String code = matcher.group();
            String color = convertAnsiCodeToHtmlColor(code);
            if (color != null) {
                html.append("<span style='color: ").append(color).append(";'>");
            } else if (code.equals("\u001B[0m")) {
                html.append("</span>");
            }

            lastEnd = matcher.end();
        }

        html.append(convertUnicodeToHtmlEntities(ansiText.substring(lastEnd).replaceAll("&", "&amp;"))
        		.replaceAll("<", "&lt;").replaceAll(">", "&gt;"));
        html.append("</pre>");
        return html.toString();
    }
    
    private static String convertUnicodeToHtmlEntities(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c > 127) {  // Non-ASCII characters
                result.append("&#").append((int) c).append(";");
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }


    private static String convertAnsiCodeToHtmlColor(String code) {
        switch (code) {
            case "\u001B[30m": return "#3B4252;"; // Black
            case "\u001B[31m": return "#BF616A;"; // Red
            case "\u001B[32m": return "#A3BE8C;"; // Green
            case "\u001B[33m": return "#EBCB8B;"; // Yellow
            case "\u001B[34m": return "#81A1C1;"; // Blue
            case "\u001B[35m": return "#B48EAD;"; // Magenta
            case "\u001B[36m": return "#88C0F0;"; // Cyan
            case "\u001B[37m": return "#FFFFFF;"; // White
            case "\u001B[90m": return "#4C566A;"; // Bright Black
            case "\u001B[91m": return "#BF616A;"; // Bright Red
            case "\u001B[92m": return "#A3BE8C;"; // Bright Green
            case "\u001B[93m": return "#EBCB8B;"; // Bright Yellow
            case "\u001B[94m": return "#81A1C1;"; // Bright Blue
            case "\u001B[95m": return "#B48EAD;"; // Bright Magenta
            case "\u001B[96m": return "#8FBCFF;"; // Bright Cyan
            case "\u001B[97m": return "#ECEFF4;"; // Bright White
            case "\u001B[0m": return null; // Reset
            default: return null;
        }
    }

	private void stopProcess(Process process) {
		if (process != null) {
			process.destroy();
		}
	}

	private void closeConsoleTab(Process process, Component tabComponent, JTextPane consoleOutput) {
		int option = JOptionPane.YES_OPTION;
		if (process != null && process.isAlive())
			option = JOptionPane.showConfirmDialog(this, "Closing a running process stops it. Proceed?", "Close",
					JOptionPane.YES_NO_OPTION);
		if (option == JOptionPane.YES_OPTION) {
			if (process.isAlive())
				stopProcess(process);
			consoleTabbedPane.remove(tabComponent);
			consoleOutput.setText(""); // Clearing the console output text
		}
		if (consoleTabbedPane.getTabCount() == 0) {
			JSplitPane splitPane = (JSplitPane) getContentPane().getComponent(0);
			splitPane.setDividerLocation(1.0); // Hide console pane
		}
	}

	private void restartApplication() {
		try {
			promptSaveAllFiles();

			// Close all files and clear data structures
			closeAllFiles();

			// Prompt user to select a new project directory
			File newProjectDir = selectProjectDirectory();

			// If a new project is selected, open it
			if (newProjectDir != null) {
				openProject(newProjectDir);
			}
		} catch (RuntimeException e) {
			// Operation was cancelled by the user, do nothing
		}
	}

	public void openProject(File dir) {
		this.projectDir = dir;
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(dir.getName());
		treeModel.setRoot(root);
		addFilesToNode(root, dir);
		treeModel.reload();
		setTitle("Jace - " + dir.getName());

		// Read the YAML configuration
		File yamlFile = new File(".languages.yaml");
		if (yamlFile.exists()) {
			try {
				languageConfig = readYamlConfig(yamlFile);
				// for(String lang : languageConfig.getLanguages().keySet()) {
				// System.out.println(lang+"
				// "+languageConfig.getLanguages().get(lang).getExtensions());
				// }
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "Failed to parse the project's configuration: " + e.toString(),
						"Error", JOptionPane.ERROR_MESSAGE);
			}
		}
		// Read the project-specific YAML configuration
		yamlFile = new File(projectDir, ".languages.yaml");
		if (yamlFile.exists()) {
			try {
				languageConfig = readYamlConfig(yamlFile);
				// for(String lang : languageConfig.getLanguages().keySet()) {
				// System.out.println(lang+"
				// "+languageConfig.getLanguages().get(lang).getExtensions());
				// }
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "Failed to parse the project's configuration: " + e.toString(),
						"Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void addFilesToNode(DefaultMutableTreeNode node, File file) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			node.removeAllChildren();
			if (files != null) {
				// Add directories first
				for (File child : files) {
					if (child.isDirectory() && !child.getName().startsWith(".") && !child.getName().startsWith("__")) {
						DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child.getName());
						childNode.setUserObject(child);
						node.add(childNode);
						addFilesToNode(childNode, child);
					}
				}
				// Add files after directories
				for (File child : files) {
					if (!child.isDirectory()) {
						DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child.getName());
						childNode.setUserObject(child);
						node.add(childNode);
					}
				}
			}
		}
	}

	private void showFileContextMenu(int x, int y, File file) {
		JPopupMenu contextMenu = new JPopupMenu();

		JMenuItem createFileItem = new JMenuItem("New file");
		createFileItem.addActionListener(e -> {
			String fileName = JOptionPane.showInputDialog(this, "File name:");
			if (fileName != null && !fileName.trim().isEmpty()) {
				File newFile = new File(file.isDirectory() ? file.getAbsolutePath() : file.getParent(), fileName);
				try {
					if (newFile.createNewFile()) {
						DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) projectTree.getSelectionPath()
								.getLastPathComponent();
						DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(newFile);
						if (!file.isDirectory())
							selectedNode = (DefaultMutableTreeNode) selectedNode.getParent();
						selectedNode.add(newNode);
						treeModel.reload(selectedNode);
						openFile(newFile); // Open the newly created file
					} else {
						JOptionPane.showMessageDialog(this, "File already exists.", "Error", JOptionPane.ERROR_MESSAGE);
					}
				} catch (IOException ex) {
					JOptionPane.showMessageDialog(this, "File creation failed: " + e.toString(), "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		contextMenu.add(createFileItem);

		JMenuItem createFolderItem = new JMenuItem("New directory");
		createFolderItem.addActionListener(e -> {
			String folderName = JOptionPane.showInputDialog(this, "Directory name:");
			if (folderName != null && !folderName.trim().isEmpty()) {
				File newFolder = new File(file.isDirectory() ? file.getAbsolutePath() : file.getParent(), folderName);
				if (newFolder.mkdir()) {
					DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) projectTree.getSelectionPath()
							.getLastPathComponent();
					if (!file.isDirectory())
						selectedNode = (DefaultMutableTreeNode) selectedNode.getParent();
					DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(newFolder);
					selectedNode.add(newNode);
					treeModel.reload(selectedNode);
				} else {
					JOptionPane.showMessageDialog(this, "Directory creation failed.", "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		contextMenu.add(createFolderItem);

		JMenuItem openExplorerItem = new JMenuItem("Show in explorer");
		openExplorerItem.addActionListener(e -> {
			try {
				Desktop.getDesktop().open(file.getParentFile());
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		});
		contextMenu.add(openExplorerItem);

		JMenuItem deleteItem = new JMenuItem("Delete");
		deleteItem.addActionListener(e -> {
			int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete " + file.getName() + "?",
					"Delete File", JOptionPane.YES_NO_OPTION);
			if (confirm == JOptionPane.YES_OPTION) {
				try {
					if (file.isDirectory()) {
						deleteDirectory(file);
					} else {
						tabbedPane.remove(openFilesMap.get(file).getParent().getParent());
						openFilesMap.remove(file);
						dirtyMap.remove(file);
						Files.delete(file.toPath());
					}
					DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) projectTree.getSelectionPath()
							.getLastPathComponent();
					selectedNode.removeFromParent();
					treeModel.reload();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});
		contextMenu.add(deleteItem);

		contextMenu.show(projectTree, x, y);
	}

	private void deleteDirectory(File directory) throws IOException {
		File[] files = directory.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					deleteDirectory(file);
				} else {
					Files.delete(file.toPath());
				}
			}
		}
		Files.delete(directory.toPath());
	}

	private void setupKeyBindings(ActionMap actionMap, InputMap inputMap) {
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK), "closeFile");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "saveFile");
		// inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK),
		// "copy");
		// inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK),
		// "paste");
		// inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK),
		// "cut");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "search");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), "run");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.CTRL_DOWN_MASK), "replace");

		actionMap.put("closeFile", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				closeFile(currentFile);
			}
		});
		actionMap.put("saveFile", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				saveCurrentFile();
			}
		});
		actionMap.put("uicopy", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				copy();
			}
		});
		actionMap.put("uipaste", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				paste();
			}
		});
		actionMap.put("uicut", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				cut();
			}
		});
		actionMap.put("run", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				runCommand();
			}
		});
		actionMap.put("search", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showSearchDialog();
			}
		});
		actionMap.put("replace", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showReplaceDialog();
			}
		});
	}

	private void copy() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			openFilesMap.get(currentFile).copy();
		}
	}

	private void paste() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			openFilesMap.get(currentFile).paste();
		}
	}

	private void cut() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			openFilesMap.get(currentFile).cut();
		}
	}

	private void updateTabLabel(File file, boolean dirty) {
		int index = tabbedPane.indexOfTab(file.getName());
		if (index >= 0) {
			JLabel tabLabel = (JLabel) ((JPanel) tabbedPane.getTabComponentAt(index)).getComponent(0);
			tabLabel.setText(dirty ? file.getName() + " * " : file.getName() + " ");
			tabLabel.setToolTipText(file.getAbsolutePath());
		}
	}

	private void updateTreeSelection(File file) {
		TreePath path = findPath(projectTree, new TreePath(treeModel.getRoot()), file);
		if (path != null) {
			projectTree.setSelectionPath(path);
		}
	}

	private TreePath findPath(JTree tree, TreePath parent, File file) {
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) parent.getLastPathComponent();
		if (file.equals(node.getUserObject())) {
			return parent;
		}
		if (node.getChildCount() >= 0) {
			for (int i = 0; i < node.getChildCount(); i++) {
				TreePath path = parent.pathByAddingChild(node.getChildAt(i));
				TreePath result = findPath(tree, path, file);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	private String previousSearchText = "";

	private void showSearchDialog() {
		JDialog searchDialog = new JDialog(this, "Search", false);
		searchDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
        Action escapeAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
            	searchDialog.dispose();
            }
        };
        searchDialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
        searchDialog.getRootPane().getActionMap().put("ESCAPE", escapeAction);
		searchDialog.setLayout(new BorderLayout());
		searchDialog.setSize(350, 180);
		searchDialog.setLocationRelativeTo(this);

		JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JTextField searchField = new JTextField(30);
		searchField.setToolTipText("Write text & press Enter to search");

		String selectedText = getSelectedText();
		if (selectedText != null && !selectedText.isEmpty()) {
			searchField.setText(selectedText);
			previousSearchText = selectedText;
		} else {
			searchField.setText(previousSearchText);
		}
		searchField.selectAll();

		JCheckBox caseSensitiveCheckBox = new JCheckBox("Case Sensitive");
		JCheckBox wrapAroundCheckBox = new JCheckBox("Wrap Around", true);
		searchField.addActionListener(e -> searchNext(searchField.getText(), caseSensitiveCheckBox.isSelected(),
				wrapAroundCheckBox.isSelected()));

		inputPanel.add(searchField);

		JPanel checkBoxPanel = new JPanel();
		checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS));
		checkBoxPanel.add(caseSensitiveCheckBox);
		checkBoxPanel.add(wrapAroundCheckBox);

		JPanel buttonPanel = new JPanel(new BorderLayout());
		JLabel occurrenceLabel = new JLabel("Type above  ", SwingConstants.RIGHT);
		occurrenceLabel.setEnabled(false);
		buttonPanel.add(occurrenceLabel, BorderLayout.NORTH);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));

		JButton firstButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/first.png"))
				.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
		firstButton.setToolTipText("First occurrence");
		firstButton.setSize(16, 16);
		firstButton.addActionListener(e -> searchFirst(searchField.getText(), caseSensitiveCheckBox.isSelected(),
				wrapAroundCheckBox.isSelected()));

		JButton nextButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/next.png"))
				.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
		nextButton.setToolTipText("Next");
		nextButton.setSize(16, 16);
		nextButton.addActionListener(e -> searchNext(searchField.getText(), caseSensitiveCheckBox.isSelected(),
				wrapAroundCheckBox.isSelected()));

		JButton previousButton = new JButton(
				new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/previous.png")).getImage()
						.getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
		previousButton.setToolTipText("Previous");
		previousButton.setSize(16, 16);
		previousButton.addActionListener(e -> searchPrevious(searchField.getText(), caseSensitiveCheckBox.isSelected(),
				wrapAroundCheckBox.isSelected()));

		buttons.add(firstButton);
		buttons.add(previousButton);
		buttons.add(nextButton);

		buttonPanel.add(buttons, BorderLayout.SOUTH);

		searchField.getDocument().addDocumentListener(new SimpleDocumentListener() {
			@Override
			public void documentChanged() {
				updateOccurrences(buttons, occurrenceLabel, searchField.getText(), caseSensitiveCheckBox.isSelected());
			}
		});
		caseSensitiveCheckBox.addActionListener(e -> updateOccurrences(buttons, occurrenceLabel, searchField.getText(),
				caseSensitiveCheckBox.isSelected()));

		buttons.setVisible(false);

		searchDialog.add(inputPanel, BorderLayout.NORTH);
		searchDialog.add(checkBoxPanel, BorderLayout.WEST);
		searchDialog.add(buttonPanel, BorderLayout.EAST);

		updateOccurrences(buttons, occurrenceLabel, searchField.getText(), caseSensitiveCheckBox.isSelected());

		searchDialog.pack();
		searchDialog.setResizable(false);
		searchDialog.setVisible(true);
	}

	private void updateOccurrences(JPanel buttons, JLabel occurrenceLabel, String searchText, boolean caseSensitive) {
		if (searchText.length() == 0) {
			occurrenceLabel.setText("Type above  ");
			buttons.setVisible(false);
			previousSearchText = searchText;
			return;
		}
		int occurrences = countOccurrences(searchText, caseSensitive);
		if (occurrences > 100) {
			occurrenceLabel.setText("100+ occurrences  ");
			buttons.setVisible(true);
			previousSearchText = searchText;
		} else if (occurrences > 1) {
			occurrenceLabel.setText(occurrences + " occurrences  ");
			buttons.setVisible(true);
			previousSearchText = searchText;
		} else if (occurrences == 1) {
			occurrenceLabel.setText(occurrences + " occurrence  ");
			buttons.setVisible(true);
			previousSearchText = searchText;
		} else {
			occurrenceLabel.setText("Nothing found  ");
			buttons.setVisible(false);
			previousSearchText = searchText;
		}
	}

	private String getSelectedText() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			RSyntaxTextArea textArea = openFilesMap.get(currentFile);
			return textArea.getSelectedText();
		}
		return null;
	}

	private int countOccurrences(String searchText, boolean caseSensitive) {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			RSyntaxTextArea textArea = openFilesMap.get(currentFile);
			String content = caseSensitive ? textArea.getText() : textArea.getText().toLowerCase();
			String search = caseSensitive ? searchText : searchText.toLowerCase();
			int occurrences = 0;
			int index = 0;

			while (index >= 0) {
				index = content.indexOf(search, index);
				if (index >= 0) {
					occurrences++;
					index += search.length();
					if (occurrences > 100) {
						return occurrences;
					}
				}
			}
			return occurrences;
		}
		return 0;
	}

	private void searchFirst(String text, boolean caseSensitive, boolean wrapAround) {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			RSyntaxTextArea textArea = openFilesMap.get(currentFile);
			String content = caseSensitive ? textArea.getText() : textArea.getText().toLowerCase();
			String searchText = caseSensitive ? text : text.toLowerCase();

			int searchStart = content.indexOf(searchText, 0);

			if (searchStart >= 0) {
				textArea.setCaretPosition(searchStart);
				textArea.moveCaretPosition(searchStart + text.length());
			} else {
				JOptionPane.showMessageDialog(this, "No first occurrence found.", "Search",
						JOptionPane.INFORMATION_MESSAGE);
			}
		}
	}

	private void searchNext(String text, boolean caseSensitive, boolean wrapAround) {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			RSyntaxTextArea textArea = openFilesMap.get(currentFile);
			String content = caseSensitive ? textArea.getText() : textArea.getText().toLowerCase();
			String searchText = caseSensitive ? text : text.toLowerCase();
			int caretPosition = textArea.getCaretPosition();

			int searchStart = content.indexOf(searchText, caretPosition);

			if (searchStart >= 0) {
				textArea.setCaretPosition(searchStart);
				textArea.moveCaretPosition(searchStart + text.length());
			} else if (wrapAround) {
				searchStart = content.indexOf(searchText);
				if (searchStart >= 0) {
					textArea.setCaretPosition(searchStart);
					textArea.moveCaretPosition(searchStart + text.length());
				} else {
					JOptionPane.showMessageDialog(this, "No occurrences found.", "Search",
							JOptionPane.INFORMATION_MESSAGE);
				}
			} else {
				JOptionPane.showMessageDialog(this, "No next occurrence found.", "Search",
						JOptionPane.INFORMATION_MESSAGE);
			}
		}
	}

	private void searchPrevious(String text, boolean caseSensitive, boolean wrapAround) {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			RSyntaxTextArea textArea = openFilesMap.get(currentFile);
			String content = caseSensitive ? textArea.getText() : textArea.getText().toLowerCase();
			String searchText = caseSensitive ? text : text.toLowerCase();
			int caretPosition = textArea.getCaretPosition();

			int searchStart = content.lastIndexOf(searchText, caretPosition - text.length() - 1);

			if (searchStart >= 0) {
				textArea.setCaretPosition(searchStart);
				textArea.moveCaretPosition(searchStart + text.length());
			} else if (wrapAround) {
				searchStart = content.lastIndexOf(searchText);
				if (searchStart >= 0) {
					textArea.setCaretPosition(searchStart);
					textArea.moveCaretPosition(searchStart + text.length());
				} else {
					JOptionPane.showMessageDialog(this, "No occurrences found.", "Search",
							JOptionPane.INFORMATION_MESSAGE);
				}
			} else {
				JOptionPane.showMessageDialog(this, "No previous occurrence found.", "Search",
						JOptionPane.INFORMATION_MESSAGE);
			}
		}
	}

	private void showReplaceDialog() {
		JOptionPane.showMessageDialog(this, "Replace functionality not implemented yet.", "Replace",
				JOptionPane.INFORMATION_MESSAGE);
	}

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		SwingUtilities.invokeLater(() -> {
			CodeEditor editor = new CodeEditor();
			editor.setVisible(true);
		});
	}

	abstract class SimpleDocumentListener implements DocumentListener {
		public void insertUpdate(DocumentEvent e) {
			documentChanged();
		}

		public void removeUpdate(DocumentEvent e) {
			documentChanged();
		}

		public void changedUpdate(DocumentEvent e) {
			documentChanged();
		}

		public abstract void documentChanged();
	}

	public class CustomTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 4888399757761252466L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {
			Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
			Object userObject = node.getUserObject();

			if (userObject instanceof File) {
				File file = (File) userObject;
				// Customize the file name display
				setText(file.getName());
				setToolTipText(file.getAbsolutePath());
			} else {
				// Handle non-file nodes, like the root node
				setText(userObject.toString());
				setToolTipText(null);
			}

			return c;
		}
	}

	public static class LanguageConfig {
		private Map<String, Language> languages;

		public static class Language {
			private String name;
			private ArrayList<String> extensions;
			private String highlighter;
			private String command;

			// Getters and setters
			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}

			public ArrayList<String> getExtensions() {
				return extensions;
			}

			public void setExtensions(ArrayList<String> extensions) {
				this.extensions = extensions;
			}

			public String getHighlighter() {
				return highlighter;
			}

			public String getCommand() {
				return command;
			}

			public void setHighlighter(String highlighter) {
				this.highlighter = highlighter;
			}

			public void setCommand(String command) {
				this.command = command;
			}
		}

		public Map<String, Language> getLanguages() {
			return languages;
		}

		public void setLanguages(Map<String, Language> languages) {
			this.languages = languages;
		}
	}

	private LanguageConfig readYamlConfig(File yamlFile) throws IOException {
		Yaml yaml = new Yaml(new Constructor(LanguageConfig.class));
		try (InputStream inputStream = Files.newInputStream(yamlFile.toPath())) {
			return yaml.load(inputStream);
		}
	}
}
