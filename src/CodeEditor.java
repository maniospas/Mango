import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.text.BadLocationException;
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
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CodeEditor extends JFrame {
	private static final long serialVersionUID = 1431536465167923296L;
	private JTree projectTree;
	private DefaultTreeModel treeModel;
	private JTabbedPane tabbedPane;
	private JTabbedPane consoleTabbedPane;
	private Map<File, RSyntaxTextArea> openFilesMap = new HashMap<>();
	private Map<File, Boolean> dirtyMap = new HashMap<>();
	private Map<Component, Process> consoleProcessMap = new HashMap<>();
	private File projectDir;
	private File currentFile;
	private Languages languageConfig, baseLanguageConfig;
	private ArrayList<String> log = new ArrayList<String>();
	
	public void log(String message) {
		log.add(message);
		if(log.size()>10)
			log.remove(0);
	}

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
		searchButton.addActionListener(e -> Search.getInstance().showSearchDialog(this));
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

		JButton undoButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/undo.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		undoButton.setToolTipText("Undo (Ctrl+Z)");
		undoButton.addActionListener(e -> undo());
		editToolBar.add(undoButton);
		JButton redoButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/redo.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		redoButton.setToolTipText("Redo (Ctrl+Y)");
		redoButton.addActionListener(e -> redo());
		editToolBar.add(redoButton);

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
		contextMenu.add(closeOthersItem);
		contextMenu.add(closeToLeftItem);
		contextMenu.add(closeToRightItem);

		contextMenu.show(e.getComponent(), e.getX(), e.getY());
	}

	// Methods for each context menu action
	private void closeAllTabs() {
		boolean hasUnsavedChanges = dirtyMap.values().stream().anyMatch(dirty -> dirty);
		if (hasUnsavedChanges) {
			int option = JOptionPane.showConfirmDialog(this, "Save changes to the files being closed?", "Save Files",
					JOptionPane.YES_NO_CANCEL_OPTION);
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
			int option = JOptionPane.showConfirmDialog(this, "Save changes to the files being closed?", "Save Files",
					JOptionPane.YES_NO_CANCEL_OPTION);
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
			int option = JOptionPane.showConfirmDialog(this, "Save changes to the files being closed?", "Save Files",
					JOptionPane.YES_NO_CANCEL_OPTION);
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
			if (i != tabIndex) {
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
			int option = JOptionPane.showConfirmDialog(this, "Save changes to the files being closed?", "Save Files",
					JOptionPane.YES_NO_CANCEL_OPTION);
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
			for (Languages.Language language : languageConfig.getTasks().values()) {
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
			int option = JOptionPane.showConfirmDialog(this, "Save changes to the files being closed?", "Save Files",
					JOptionPane.YES_NO_CANCEL_OPTION);
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
		File yamlFile = new File(projectDir + "/.jace.yaml");
		if (yamlFile.exists()) {
			try {
				languageConfig = Languages.readYamlConfig(yamlFile, baseLanguageConfig);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this,
						"Failed to parse the project-specific configuration: " + e.toString(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
		HashMap<String, Languages.Language> alternatives = new HashMap<String, Languages.Language>();
		if (this.currentFile != null)
			for (String lang : languageConfig.getTasks().keySet()) {
				for (String extension : languageConfig.getTasks().get(lang).getExtensions()) {
					if (this.currentFile.getName().endsWith("." + extension)) {
						alternatives.put(lang, languageConfig.getTasks().get(lang));
					}
				}
			}

		if (alternatives.size()==0) {
			JOptionPane.showMessageDialog(this,
					"There is no declared task for the current file's extension.", "Nothing to run",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		Languages.Language runLanguage = null;
		if (alternatives.size() == 1) {
			runLanguage = new ArrayList<Languages.Language>(alternatives.values()).get(0);
		} 
		else {
			String[] options = new String[alternatives.size() + 1];
			HashMap<Integer, Languages.Language> optionToTask = new HashMap<Integer, Languages.Language>();
			int i = 0;
			for (String lang : alternatives.keySet()) {
				options[i] = lang;
				optionToTask.put(i, alternatives.get(lang));
				i += 1;
			}
			options[alternatives.size()] = "Cancel";

			String message = "Multiple tasks are available for the current file's extension. Please select one:";
			int choice = JOptionPane.showOptionDialog(this,
					message,
					"Select task",
					JOptionPane.DEFAULT_OPTION,
					JOptionPane.QUESTION_MESSAGE,
					null,
					options,
					options[0]);

			if (choice==JOptionPane.CLOSED_OPTION || choice == alternatives.size()) 
				return;
			else
				runLanguage = optionToTask.get(choice);
		}
		assert runLanguage!=null; // this should be impossible right now
		try {
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

		if (!consoleTabbedPane.isVisible()) {
			JSplitPane splitPane = (JSplitPane) getContentPane().getComponent(0);
			consoleTabbedPane.setVisible(true);
			splitPane.setDividerLocation(400);
		}
		consoleTabbedPane.addTab("Run Output", scrollPane);
		consoleTabbedPane.setTabComponentAt(consoleTabbedPane.getTabCount() - 1, tabComponent);
		consoleTabbedPane.setSelectedComponent(scrollPane);
		String filepath = currentFile.getAbsolutePath().replace("\\", "/");
		String command = runLanguage.getCommand()
				.replace("{path}",
						filepath.contains("/") ? filepath.substring(0, filepath.lastIndexOf("/") + 1) : ".")
				.replace("{path/}", filepath.contains("/") ? filepath.substring(0, filepath.lastIndexOf("/") + 1) : ".")
				.replace("{path.}",
						filepath.contains("/") ? filepath.substring(0, filepath.lastIndexOf("/") + 1).replace("/", ".")
								: ".")
				.replace("{path\\}",
						filepath.contains("/") ? filepath.substring(0, filepath.lastIndexOf("/") + 1).replace("/", "\\")
								: ".")
				.replace("{file}",
						filepath.contains(".")
								? filepath.substring(filepath.lastIndexOf("/") + 1, filepath.lastIndexOf("."))
								: filepath.substring(filepath.lastIndexOf("/") + 1))
				.replace("{ext}", filepath.contains(".") ? filepath.substring(filepath.lastIndexOf(".")) : "");
		ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
		processBuilder.directory(projectDir);
		ANSI.appendAnsiText(consoleOutput, command + "\n\n");
		try {
			Process process = processBuilder.start();
			stopButton.addActionListener(e -> stopProcess(process));
			closeButton.addActionListener(e -> closeConsoleTab(process, scrollPane, consoleOutput));

			consoleProcessMap.put(scrollPane, process); // Add process to the map

			new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						String l = line;
						SwingUtilities.invokeLater(() -> ANSI.appendAnsiText(consoleOutput, l + "\n"));
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
						SwingUtilities.invokeLater(() -> ANSI.appendAnsiText(consoleOutput, l + "\n"));
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}).start();

			// Add mouse listener to tab for context menu
			tabComponent.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if (SwingUtilities.isRightMouseButton(e)) {
						showConsoleContextMenu(e, consoleTabbedPane.indexOfTabComponent(tabComponent));
					}
				}
			});

		} catch (Exception e) {
			ANSI.appendAnsiText(consoleOutput, e.toString());
		}
	}

	private void stopProcess(Process process) {
		if (process != null && process.isAlive()) {
			log("Console: process cancelled by user");
			process.destroy();
		}
	}

	private void closeConsoleTab(Process process, Component tabComponent, JTextPane consoleOutput) {
		int option = JOptionPane.YES_OPTION;
		if (process != null && process.isAlive())
			option = JOptionPane.showConfirmDialog(this, "Closing a running process stops it. Proceed?", "Close",
					JOptionPane.YES_NO_OPTION);
		if (option == JOptionPane.YES_OPTION) {
			stopProcess(process);
			consoleTabbedPane.remove(tabComponent);
			consoleProcessMap.remove(tabComponent); // Remove the process from the map
			consoleOutput.setText(""); // Clearing the console output text
		}
		/*if (consoleTabbedPane.getTabCount() == 0) {
			JSplitPane splitPane = (JSplitPane) getContentPane().getComponent(0);
			splitPane.setDividerLocation(1.0); // Hide console pane
		}*/
	}

	// Method to show context menu for console tabs
	private void showConsoleContextMenu(MouseEvent e, int tabIndex) {
		JPopupMenu contextMenu = new JPopupMenu();

		JMenuItem closeAllItem = new JMenuItem("Close all");
		closeAllItem.addActionListener(ev -> closeAllConsoleTabs());
		JMenuItem closeToRightItem = new JMenuItem("Close all to the right");
		closeToRightItem.addActionListener(ev -> closeConsoleTabsToRight(tabIndex));
		JMenuItem closeToLeftItem = new JMenuItem("Close all to the left");
		closeToLeftItem.addActionListener(ev -> closeConsoleTabsToLeft(tabIndex));
		JMenuItem closeOthersItem = new JMenuItem("Close all others");
		closeOthersItem.addActionListener(ev -> closeOtherConsoleTabs(tabIndex));
		JMenuItem closeSameFirstLineItem = new JMenuItem("Close all with this command");
		closeSameFirstLineItem.addActionListener(ev -> closeConsoleTabsWithSameFirstLine(tabIndex, true));
		JMenuItem closeOthersSameFirstLineItem = new JMenuItem("Close all others with this command");
		closeOthersSameFirstLineItem.addActionListener(ev -> closeConsoleTabsWithSameFirstLine(tabIndex, false));

		contextMenu.add(closeAllItem);
		contextMenu.add(closeOthersItem);
		contextMenu.add(closeToLeftItem);
		contextMenu.add(closeToRightItem);
		contextMenu.add(closeSameFirstLineItem);
		contextMenu.add(closeOthersSameFirstLineItem);

		contextMenu.show(e.getComponent(), e.getX(), e.getY());
	}

	private void closeAllConsoleTabs() {
		boolean hasRunningProcesses = consoleProcessMap.values().stream().anyMatch(Process::isAlive);
		if (hasRunningProcesses) {
			int option = JOptionPane.showConfirmDialog(this, "There are running processes. Do you want to stop all and close?", "Confirm Close", JOptionPane.YES_NO_OPTION);
			if (option != JOptionPane.YES_OPTION) {
				return;
			}
		}
		consoleProcessMap.forEach((component, process) -> {
			stopProcess(process);
		});
		consoleTabbedPane.removeAll();
		consoleProcessMap.clear();
	}

	private void closeConsoleTabsToRight(int tabIndex) {
		boolean hasRunningProcesses = false;
		for (int i = consoleTabbedPane.getTabCount() - 1; i > tabIndex; i--) {
			Process process = consoleProcessMap.get(consoleTabbedPane.getComponentAt(i));
			if (process != null && process.isAlive()) {
				hasRunningProcesses = true;
				break;
			}
		}
		if (hasRunningProcesses) {
			int option = JOptionPane.showConfirmDialog(this, "There are running processes. Do you want to stop them and close?", "Confirm Close", JOptionPane.YES_NO_OPTION);
			if (option != JOptionPane.YES_OPTION) {
				return;
			}
		}
		for (int i = consoleTabbedPane.getTabCount() - 1; i > tabIndex; i--) {
			Component component = consoleTabbedPane.getComponentAt(i);
			Process process = consoleProcessMap.get(component);
			stopProcess(process);
			consoleTabbedPane.remove(i);
			consoleProcessMap.remove(component);
		}
	}

	private void closeConsoleTabsToLeft(int tabIndex) {
		boolean hasRunningProcesses = false;
		for (int i = tabIndex - 1; i >= 0; i--) {
			Process process = consoleProcessMap.get(consoleTabbedPane.getComponentAt(i));
			if (process != null && process.isAlive()) {
				hasRunningProcesses = true;
				break;
			}
		}
		if (hasRunningProcesses) {
			int option = JOptionPane.showConfirmDialog(this, "There are running processes. Do you want to stop them and close?", "Confirm Close", JOptionPane.YES_NO_OPTION);
			if (option != JOptionPane.YES_OPTION) {
				return;
			}
		}
		for (int i = tabIndex - 1; i >= 0; i--) {
			Component component = consoleTabbedPane.getComponentAt(i);
			Process process = consoleProcessMap.get(component);
			stopProcess(process);
			consoleTabbedPane.remove(i);
			consoleProcessMap.remove(component);
		}
	}

	private void closeOtherConsoleTabs(int tabIndex) {
		boolean hasRunningProcesses = false;
		for (int i = consoleTabbedPane.getTabCount() - 1; i >= 0; i--) {
			if (i != tabIndex) {
				Process process = consoleProcessMap.get(consoleTabbedPane.getComponentAt(i));
				if (process != null && process.isAlive()) {
					hasRunningProcesses = true;
					break;
				}
			}
		}
		if (hasRunningProcesses) {
			int option = JOptionPane.showConfirmDialog(this, "There are running processes. Do you want to stop them and close?", "Confirm Close", JOptionPane.YES_NO_OPTION);
			if (option != JOptionPane.YES_OPTION) {
				return;
			}
		}
		for (int i = consoleTabbedPane.getTabCount() - 1; i >= 0; i--) {
			if (i != tabIndex) {
				Component component = consoleTabbedPane.getComponentAt(i);
				Process process = consoleProcessMap.get(component);
				stopProcess(process);
				consoleTabbedPane.remove(i);
				consoleProcessMap.remove(component);
			}
		}
	}

	private void closeConsoleTabsWithSameFirstLine(int tabIndex, boolean includeCurrent) {
		Component selectedComponent = consoleTabbedPane.getComponentAt(tabIndex);
		JTextPane selectedTextPane = (JTextPane) ((JScrollPane) selectedComponent).getViewport().getView();
		String firstLine = getFirstLine(selectedTextPane);

		boolean hasRunningProcesses = false;
		for (int i = consoleTabbedPane.getTabCount() - 1; i >= 0; i--) {
			if (i != tabIndex || includeCurrent) {
				Component component = consoleTabbedPane.getComponentAt(i);
				JTextPane textPane = (JTextPane) ((JScrollPane) component).getViewport().getView();
				String line = getFirstLine(textPane);

				if (line.equals(firstLine)) {
					Process process = consoleProcessMap.get(component);
					if (process != null && process.isAlive()) {
						hasRunningProcesses = true;
						break;
					}
				}
			}
		}
		if (hasRunningProcesses) {
			int option = JOptionPane.showConfirmDialog(this, "There are running processes with the same first line. Do you want to stop them and close?", "Confirm Close", JOptionPane.YES_NO_OPTION);
			if (option != JOptionPane.YES_OPTION) {
				return;
			}
		}
		for (int i = consoleTabbedPane.getTabCount() - 1; i >= 0; i--) {
			if (i != tabIndex || includeCurrent) {
				Component component = consoleTabbedPane.getComponentAt(i);
				JTextPane textPane = (JTextPane) ((JScrollPane) component).getViewport().getView();
				String line = getFirstLine(textPane);

				if (line.equals(firstLine)) {
					Process process = consoleProcessMap.get(component);
					stopProcess(process);
					consoleTabbedPane.remove(i);
					consoleProcessMap.remove(component);
				}
			}
		}
	}


	private String getFirstLine(JTextPane textPane) {
		try {
			return textPane.getDocument().getText(0, textPane.getDocument().getDefaultRootElement().getElement(0).getEndOffset()).trim();
		} catch (BadLocationException e) {
			e.printStackTrace();
			return "";
		}
	}

	private void restartApplication() {
		try {
			promptSaveAllFiles();
			closeAllFiles();
			File newProjectDir = selectProjectDirectory();
			if (newProjectDir != null) {
				openProject(newProjectDir);
			}
			else {
				log("Open project: cancelled by user");
			}
		} catch (RuntimeException e) {
			log("Open project: cancelled by user");
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
		File yamlFile = new File(".jace.yaml");
		if (yamlFile.exists()) {
			try {
				languageConfig = Languages.readYamlConfig(yamlFile, null);
				baseLanguageConfig = languageConfig;
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, 
						"Failed to parse global configuration: " + e.toString(),
						"Error", JOptionPane.ERROR_MESSAGE);
				log("Open project: failed to parse global configuration");
			}
		}
		// Read the project-specific YAML configuration
		yamlFile = new File(projectDir, ".jace.yaml");
		if (yamlFile.exists()) {
			try {
				languageConfig = Languages.readYamlConfig(yamlFile, baseLanguageConfig);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, 
						"Failed to parse the project's configuration: " + e.toString(),
						"Error", JOptionPane.ERROR_MESSAGE);
				log(projectDir.getAbsolutePath()+": failed to parse project configuration");
			}
		}
		log(projectDir.getAbsolutePath()+": opened");
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
						log(file.toString()+": created");
						openFile(newFile); // Open the newly created file
					} else {
						JOptionPane.showMessageDialog(this, "File already exists.", "Error", JOptionPane.ERROR_MESSAGE);
						log(file.toString()+": already exists");
					}
				} catch (IOException ex) {
					JOptionPane.showMessageDialog(this, "File creation failed: " + e.toString(), "Error",
							JOptionPane.ERROR_MESSAGE);
					log(file.toString()+": failed to create");
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
					log(file.toString()+": created");
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
					log(file.toString()+": failed to create");
				}
			}
			else
				log(file.toString()+": cancelled by user");
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
						Files.delete(file.toPath());
						tabbedPane.remove(openFilesMap.get(file).getParent().getParent());
						openFilesMap.remove(file);
						dirtyMap.remove(file);
					}
					DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) projectTree.getSelectionPath()
							.getLastPathComponent();
					selectedNode.removeFromParent();
					treeModel.reload();
					log(file.toString()+": deleted");
				} catch (IOException ex) {
					log(file.toString()+": failed to delete");
					JOptionPane.showMessageDialog(this, "Deletion failed: "+e.toString(), "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}
			else
				log(file.toString()+": cancelled by user");
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
		CodeEditor thisObj = this;
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
		actionMap.put("uiundo", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				undo();
			}
		});
		actionMap.put("uiredo", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				redo();
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
				Search.getInstance().showSearchDialog(thisObj);
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

	private void undo() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			if (openFilesMap.get(currentFile).canUndo())
				openFilesMap.get(currentFile).undoLastAction();
		}
	}

	private void redo() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			if (openFilesMap.get(currentFile).canRedo())
				openFilesMap.get(currentFile).redoLastAction();
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

	public String getSelectedText() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			RSyntaxTextArea textArea = openFilesMap.get(currentFile);
			return textArea.getSelectedText();
		}
		return null;
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

	public RSyntaxTextArea getCurrentTextArea() {
		return openFilesMap.getOrDefault(currentFile, null);
	}
}
