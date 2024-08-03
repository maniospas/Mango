import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
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
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeEditor extends JFrame {
	private static final long serialVersionUID = 1431536465167923296L;
	private JTree projectTree;
	private DefaultTreeModel treeModel;
	private JTabbedPane tabbedPane;
	private JTabbedPane consoleTabbedPane;
	private Map<File, Component> openFilesMap = new HashMap<>();
	private Map<File, Boolean> dirtyMap = new HashMap<>();
	private Map<Component, Process> consoleProcessMap = new HashMap<>();
	public File projectDir;
	private File currentFile;
	private Tasks languageConfig, baseLanguageConfig;
	private ArrayList<String> log = new ArrayList<String>();

	public void log(String message) {
		log.add(message);
		if (log.size() > 10)
			log.remove(0);
	}
	

	public CodeEditor() {
		ThemeDialog.loadThemeFromFile();
		
		setTitle("Mango - Just another code editor");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(800, 600);
		setLocationRelativeTo(null);
		setExtendedState(JFrame.MAXIMIZED_BOTH);
		
		setIconImage(
				new ImageIcon(CodeEditor.class.getResource("/icons/mango.png"))
				.getImage());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				try {
					promptSaveAllFiles();
					System.exit(0);
				} catch (RuntimeException ex) {
				}
			}
		});

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
						if (selectedNode != null) {
							File selectedFile = (File) selectedNode.getUserObject();
							if (selectedFile.isDirectory())
								addFilesToNode(selectedNode, selectedFile);
							else
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
				// System.out.println(node.getUserObject());
				File file = (File) new File(node.getUserObject().toString());
				if (file.isDirectory()
						&& (node.getChildCount() == 1 && node.getChildAt(0).toString().equals("Loading..."))) {
					addFilesToNode(node, file);
				}
			}

			@Override
			public void treeCollapsed(TreeExpansionEvent event) {
				// No action needed
			}
		});
		addWindowFocusListener(new WindowFocusListener() {
			@Override
			public void windowGainedFocus(WindowEvent e) {
				refreshContent(projectTree);
			}

			@Override
			public void windowLostFocus(WindowEvent e) {
				// Do nothing when window loses focus
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

		CodeEditor thisObj = this;
		JButton taskButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/settings.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		taskButton.setToolTipText("Edit tasks");
		taskButton.addActionListener(e -> TasksEditor.createTasks(thisObj));
		mainToolBar.add(taskButton);

		JButton themeButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/mango.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		themeButton.setToolTipText("Set theme");
		themeButton.addActionListener(e -> updateTheme());
		mainToolBar.add(themeButton);

		// Search and Replace Toolbar
		JToolBar searchReplaceToolBar = new JToolBar();

		JButton saveButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/save.png"))
				.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
		saveButton.setToolTipText("Save current file (Ctrl+S)");
		saveButton.addActionListener(e -> saveCurrentFile());
		searchReplaceToolBar.add(saveButton);

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

	private void updateTheme() {
        ThemeDialog dialog = new ThemeDialog(this);
        dialog.setVisible(true);
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
	    if(file.getName().equals(".mango.yaml")) {
	        TasksEditor.createTasks(this);
	        return;
	    }
	    currentFile = file;
	    if (openFilesMap.containsKey(file)) {
	    	System.out.println(getParentInPane(openFilesMap.get(file)));
	        tabbedPane.setSelectedComponent(getParentInPane(openFilesMap.get(file)));
	    } else {
	        if (isImageFile(file)) {
	            openImageFile(file);
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
	                            for (Map.Entry<File, Component> entry : openFilesMap.entrySet()) {
	                                if (getParentInPane(entry.getValue()) == tabbedPane.getSelectedComponent()) {
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
	}

	private boolean isImageFile(File file) {
	    String[] imageExtensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp"};
	    for (String ext : imageExtensions) {
	        if (file.getName().toLowerCase().endsWith(ext)) {
	            return true;
	        }
	    }
	    return false;
	}

	private void openImageFile(File file) {
	    try {
	        ImageIcon imageIcon = new ImageIcon(file.getAbsolutePath());
	        double initialScale = 0.5;
	        int originalWidth = imageIcon.getIconWidth();
	        int originalHeight = imageIcon.getIconHeight();
	        int sizex = (int) Math.min(this.getWidth(), this.getHeight() * originalWidth / originalHeight);
	        sizex = (int) (sizex * initialScale);
	        int sizey = sizex * originalHeight / originalWidth;

	        JLabel imageLabel = new JLabel(new ImageIcon(imageIcon.getImage().getScaledInstance(sizex, sizey, Image.SCALE_SMOOTH)));
	        JLabel dimensionsLabel = new JLabel("Actual size: " + originalWidth + "x" + originalHeight);
	        dimensionsLabel.setHorizontalAlignment(SwingConstants.CENTER);

	        JScrollPane imageScrollPane = new JScrollPane(imageLabel);

	        JSlider scaleSlider = new JSlider(25, 400, 100); // Scale slider from 0.25 to 4.0
	        scaleSlider.setMajorTickSpacing(50);
	        scaleSlider.setPaintTicks(true);
	        scaleSlider.setPaintLabels(false);

	        scaleSlider.addChangeListener(e -> {
	            double scale = scaleSlider.getValue() / 100.0 * initialScale;
	            int newSizex = (int) Math.min(this.getWidth(), this.getHeight() * originalWidth / originalHeight);
	            newSizex = (int) (newSizex * scale);
	            int newSizey = newSizex * originalHeight / originalWidth;
	            imageLabel.setIcon(new ImageIcon(imageIcon.getImage().getScaledInstance(newSizex, newSizey, Image.SCALE_SMOOTH)));
	            imageLabel.revalidate();
	        });

	        JPanel topPanel = new JPanel(new BorderLayout());
	        topPanel.add(dimensionsLabel, BorderLayout.NORTH);
	        topPanel.add(scaleSlider, BorderLayout.SOUTH);

	        JPanel imagePanel = new JPanel(new BorderLayout());
	        imagePanel.add(topPanel, BorderLayout.NORTH);
	        imagePanel.add(imageScrollPane, BorderLayout.CENTER);

	        JPanel tabComponent = new JPanel(new BorderLayout());
	        tabComponent.setOpaque(false);
	        JLabel tabLabel = new JLabel(file.getName());
	        tabLabel.setToolTipText(file.getAbsolutePath());
	        JButton closeButton = new JButton(
	                new ImageIcon(new ImageIcon(getClass().getResource("/icons/close.png")).getImage()
	                        .getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
	        closeButton.setPreferredSize(new Dimension(16, 16));
	        closeButton.addActionListener(e -> closeFile(file));
	        closeButton.setFocusable(false);
	        tabComponent.add(tabLabel, BorderLayout.WEST);
	        tabComponent.add(closeButton, BorderLayout.EAST);

	        tabbedPane.addTab(file.getName(), imagePanel);
	        tabbedPane.setTabComponentAt(tabbedPane.getTabCount() - 1, tabComponent);
	        tabbedPane.setSelectedComponent(imagePanel);
	        openFilesMap.put(file, imageScrollPane);
	        dirtyMap.put(file, false);

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

	    } catch (Exception e) {
	        e.printStackTrace();
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

	private HashMap<Component, File> areaToFile;

	private void updateInverseTextAreaMap() {
		areaToFile = new HashMap<Component, File>();
		for (Map.Entry<File, Component> entry : openFilesMap.entrySet())
			areaToFile.put(entry.getValue(), entry.getKey());
	}

	// Helper method to get the File associated with an RSyntaxTextArea
	private File getFileForTextArea(RSyntaxTextArea textArea) {
		return areaToFile.get(textArea);
	}

	private void updateSyntaxHighlighter(File file, RSyntaxTextArea textArea) {
		if (languageConfig != null)
			for (Tasks.Task task : languageConfig.getTasks().values()) {
				for (String extension : task.getExtensions()) {
					if (file.getName().endsWith("." + extension)) {
						textArea.setSyntaxEditingStyle("text/" + task.getHighlighter().replace("text/", ""));
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
		if (file.getName().endsWith(".rb"))
			textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_RUBY);
	}

	private void setDirty(File file, boolean dirty) {
		dirtyMap.put(file, dirty);
		updateTabLabel(file, dirty);
	}

	private void saveFile(File file) {
		if (file != null && openFilesMap.containsKey(file)) {
			try {
				Component component = openFilesMap.get(file);
				if(!(component instanceof RSyntaxTextArea))
					return;
				RSyntaxTextArea textArea = (RSyntaxTextArea)component;
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
		for (Map.Entry<File, Component> entry : openFilesMap.entrySet()) {
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
		if (file != null && openFilesMap.get(file) != null) {
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
			tabbedPane.remove(getParentInPane(openFilesMap.get(file)));
			openFilesMap.remove(file);
			dirtyMap.remove(file);
		}
		else {
			throw new RuntimeException("No file to close");
		}
	}
	
	private Component getParentInPane(Component component) {
		while(true) {
			Component parent = component.getParent();
			if(parent==null || parent==tabbedPane)
				return component;
			component = parent;
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
		File yamlFile = new File(projectDir + "/.mango.yaml");
		if (yamlFile.exists()) {
			try {
				languageConfig = Tasks.readYamlConfig(yamlFile, baseLanguageConfig);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this,
						"Failed to parse the project-specific configuration: " + e.toString(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
		HashMap<String, Tasks.Task> alternatives = new HashMap<String, Tasks.Task>();
		if (this.currentFile != null)
			for (String lang : languageConfig.getTasks().keySet()) {
				for (String extension : languageConfig.getTasks().get(lang).getExtensions()) {
					if (this.currentFile.getName().endsWith("." + extension)) {
						alternatives.put(lang, languageConfig.getTasks().get(lang));
					}
				}
			}

		if (alternatives.size() == 0) {
		    Object[] options = {"Edit tasks", "OK"};
		    int choice = JOptionPane.showOptionDialog(
		            this,
		            "There is no declared task for the current file's extension.",
		            "Nothing to run",
		            JOptionPane.DEFAULT_OPTION,
		            JOptionPane.ERROR_MESSAGE,
		            null,
		            options,
		            options[0]
		    );

		    if (choice == 0) {
		    	TasksEditor.createTasks(this); 
		    }
		    return;
		}
		
		Tasks.Task runLanguage = null;
		if (alternatives.size() == 1) {
			runLanguage = new ArrayList<Tasks.Task>(alternatives.values()).get(0);
		} else {
			String[] options = new String[alternatives.size() + 1];
			HashMap<Integer, Tasks.Task> optionToTask = new HashMap<Integer, Tasks.Task>();
			int i = 0;
			for (String lang : alternatives.keySet()) {
				options[i] = lang;
				optionToTask.put(i, alternatives.get(lang));
				i += 1;
			}
			options[alternatives.size()] = "Cancel";

			String message = "Multiple tasks are available for the current file's extension. Please select one:";
			int choice = JOptionPane.showOptionDialog(this, message, "Select task", JOptionPane.DEFAULT_OPTION,
					JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

			if (choice == JOptionPane.CLOSED_OPTION || choice == alternatives.size())
				return;
			else
				runLanguage = optionToTask.get(choice);
		}
		assert runLanguage != null; // this should be impossible right now
		try {
			promptSaveAllFiles();
		} catch (RuntimeException e) {
			return;
		}
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

		Pattern pattern = Pattern.compile("\\{(.*?)\\}");
		Matcher matcher = pattern.matcher(command);

		StringBuffer resultString = new StringBuffer();
		while (matcher.find()) {
			String placeholder = matcher.group(1);
			String userInput = JOptionPane.showInputDialog(this, "Enter " + placeholder + ":");
			if (userInput == null)
				return;
			matcher.appendReplacement(resultString, userInput);
		}
		matcher.appendTail(resultString);
		command = resultString.toString();

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
		/*
		 * if (consoleTabbedPane.getTabCount() == 0) { JSplitPane splitPane =
		 * (JSplitPane) getContentPane().getComponent(0);
		 * splitPane.setDividerLocation(1.0); // Hide console pane }
		 */
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
			int option = JOptionPane.showConfirmDialog(this,
					"There are running processes. Do you want to stop all and close?", "Confirm Close",
					JOptionPane.YES_NO_OPTION);
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
			int option = JOptionPane.showConfirmDialog(this,
					"There are running processes. Do you want to stop them and close?", "Confirm Close",
					JOptionPane.YES_NO_OPTION);
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
			int option = JOptionPane.showConfirmDialog(this,
					"There are running processes. Do you want to stop them and close?", "Confirm Close",
					JOptionPane.YES_NO_OPTION);
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
			int option = JOptionPane.showConfirmDialog(this,
					"There are running processes. Do you want to stop them and close?", "Confirm Close",
					JOptionPane.YES_NO_OPTION);
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
			int option = JOptionPane.showConfirmDialog(this,
					"There are running processes with the same first line. Do you want to stop them and close?",
					"Confirm Close", JOptionPane.YES_NO_OPTION);
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
			return textPane.getDocument()
					.getText(0, textPane.getDocument().getDefaultRootElement().getElement(0).getEndOffset()).trim();
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
			} else {
				log("Open project: cancelled by user");
			}
		} catch (RuntimeException e) {
			log("Open project: cancelled by user");
			// Operation was cancelled by the user, do nothing
		}
	}

	private void refreshContent(JTree tree) {
		// Save expanded paths as file paths
		ArrayList<String> expandedPaths = new ArrayList<String>();
		Enumeration<TreePath> enumeration = tree.getExpandedDescendants(new TreePath(treeModel.getRoot()));
		if (enumeration != null) {
			while (enumeration.hasMoreElements()) {
				TreePath treePath = enumeration.nextElement();
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
				if (!(node.getUserObject() instanceof File))
					continue;
				File file = (File) node.getUserObject();
				expandedPaths.add(file.getAbsolutePath());
			}
		}

		// Refresh the tree content
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(projectDir);
		treeModel.setRoot(root);
		root.add(new DefaultMutableTreeNode("Loading..."));
		addFilesToNode(root, projectDir);
		treeModel.reload();
		Collections.sort(expandedPaths); // needed to have the correct hierarchical order when loading

		// Restore expanded paths
		for (String path : expandedPaths) {
			TreePath treePath = findTreePath(root, path);
			if (treePath != null) {
				tree.expandPath(treePath);
			}
		}
	}

	private TreePath findTreePath(DefaultMutableTreeNode root, String path) {
		Enumeration<TreeNode> enumeration = root.breadthFirstEnumeration();
		while (enumeration.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
			if (!(node.getUserObject() instanceof File))
				continue;
			File file = (File) node.getUserObject();
			if (file.getAbsolutePath().equals(path)) {
				return new TreePath(node.getPath());
			}
		}
		return null;
	}

	public void openProject(File dir) {
		this.projectDir = dir;
		DefaultMutableTreeNode root = new DefaultMutableTreeNode(dir.getName());
		treeModel.setRoot(root);
		root.add(new DefaultMutableTreeNode("Loading..."));
		addFilesToNode(root, dir);
		treeModel.reload();
		setTitle("Mango - " + dir.getName());

		// Read the YAML configuration
		File yamlFile = new File(".mango.yaml");
		if (yamlFile.exists()) {
			try {
				languageConfig = Tasks.readYamlConfig(yamlFile, null);
				baseLanguageConfig = languageConfig;
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "Failed to parse global configuration: " + e.toString(), "Error",
						JOptionPane.ERROR_MESSAGE);
				log("Open project: failed to parse global configuration");
			}
		}
		// Read the project-specific YAML configuration
		yamlFile = new File(projectDir, ".mango.yaml");
		if (yamlFile.exists()) {
			try {
				languageConfig = Tasks.readYamlConfig(yamlFile, baseLanguageConfig);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "Failed to parse the project's configuration: " + e.toString(),
						"Error", JOptionPane.ERROR_MESSAGE);
				log(projectDir.getAbsolutePath() + ": failed to parse project configuration");
			}
		}
		log(projectDir.getAbsolutePath() + ": opened");
	}
	
	public Tasks reloadTasks() {
		File yamlFile = new File(".mango.yaml");
		// Read the project-specific YAML configuration
		yamlFile = new File(projectDir, ".mango.yaml");
		if (yamlFile.exists()) {
			try {
				languageConfig = Tasks.readYamlConfig(yamlFile, baseLanguageConfig);
			} catch (Exception e) {
				languageConfig = new Tasks();
				JOptionPane.showMessageDialog(this, "Failed to parse the project's configuration: " + e.toString(),
						"Error", JOptionPane.ERROR_MESSAGE);
				log(projectDir.getAbsolutePath() + ": failed to parse project configuration");
			}
		}
		return languageConfig;
	}

	private void addFilesToNode(DefaultMutableTreeNode node, File file) {
		if (file.isDirectory() && node.getChildCount() == 1 && node.getChildAt(0).toString().equals("Loading...")) {
			File[] files = file.listFiles();
			node.removeAllChildren();
			if (files != null) {
				// Add directories first
				for (File child : files) {
					if (child.isDirectory() && !child.getName().startsWith(".") && !child.getName().startsWith("__")) {
						DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child.getName());
						childNode.setUserObject(child);
						node.add(childNode);
						childNode.add(new DefaultMutableTreeNode("Loading..."));
						// addFilesToNode(childNode, child);
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
			treeModel.reload(node);
			// SwingUtilities.invokeLater(() -> {treeModel.reload();});

		}
	}

	private void showFileContextMenu(int x, int y, File file) {
		JPopupMenu contextMenu = new JPopupMenu();

		if (file.isDirectory()) {
			JMenuItem createFileItem = new JMenuItem("New file");
			createFileItem.addActionListener(e -> {
				String fileName = JOptionPane.showInputDialog(this, "File name:");
				if (fileName != null && !fileName.trim().isEmpty()) {
					File newFile = new File(file.isDirectory() ? file.getAbsolutePath() : file.getParent(), fileName);
					try {
						if (newFile.createNewFile()) {
							DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) projectTree
									.getSelectionPath().getLastPathComponent();
							DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(newFile);
							if (!file.isDirectory())
								selectedNode = (DefaultMutableTreeNode) selectedNode.getParent();
							selectedNode.add(newNode);
							treeModel.reload(selectedNode);
							log(file.toString() + ": created");
							openFile(newFile); // Open the newly created file
						} else {
							JOptionPane.showMessageDialog(this, "File already exists.", "Error",
									JOptionPane.ERROR_MESSAGE);
							log(file.toString() + ": already exists");
						}
					} catch (IOException ex) {
						JOptionPane.showMessageDialog(this, "File creation failed: " + e.toString(), "Error",
								JOptionPane.ERROR_MESSAGE);
						log(file.toString() + ": failed to create");
					}
				}
			});
			contextMenu.add(createFileItem);

			JMenuItem createFolderItem = new JMenuItem("New directory");
			createFolderItem.addActionListener(e -> {
				String folderName = JOptionPane.showInputDialog(this, "Directory name:");
				if (folderName != null && !folderName.trim().isEmpty()) {
					File newFolder = new File(file.isDirectory() ? file.getAbsolutePath() : file.getParent(),
							folderName);
					if (newFolder.mkdir()) {
						log(file.toString() + ": created");
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
						log(file.toString() + ": failed to create");
					}
				} else
					log(file.toString() + ": cancelled by user");
			});
			contextMenu.add(createFolderItem);

			contextMenu.addSeparator();
		}

		JMenuItem openExplorerItem = new JMenuItem("Show in explorer");
		openExplorerItem.addActionListener(e -> {
			try {
				Desktop.getDesktop().open(file.getParentFile());
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		});
		contextMenu.add(openExplorerItem);

		if (file.isDirectory()) {
			JMenuItem rebaseItem = new JMenuItem("Open as project");
			rebaseItem.addActionListener(e -> {
				try {
					promptSaveAllFiles();
					closeAllFiles();
					File newProjectDir = file;
					openProject(newProjectDir);
				} catch (RuntimeException ex) {
					log("Open project: cancelled by user");
				}
			});
			contextMenu.add(rebaseItem);
		}

		contextMenu.addSeparator();

		JMenuItem renameItem = new JMenuItem("Rename");
		renameItem.addActionListener(e -> rename(file));
		contextMenu.add(renameItem);

		JMenuItem deleteItem = new JMenuItem("Delete");
		deleteItem.addActionListener(e -> deleteFile(file));
		contextMenu.add(deleteItem);

		contextMenu.show(projectTree, x, y);
	}
	
	private void deleteFile(File file) {
		{
			int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete " + file.getName() + "?",
					"Delete File", JOptionPane.YES_NO_OPTION);
			if (confirm == JOptionPane.YES_OPTION) {
				try {
					if (file.isDirectory()) {
						deleteDirectory(file);
					} else {
						Files.delete(file.toPath());
						if (openFilesMap.get(file) != null)
							tabbedPane.remove(getParentInPane(openFilesMap.get(file)));
						openFilesMap.remove(file);
						dirtyMap.remove(file);
					}
					DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) projectTree.getSelectionPath()
							.getLastPathComponent();
					selectedNode.removeFromParent();
					treeModel.reload();
					log(file.toString() + ": deleted");
				} catch (IOException ex) {
					log(file.toString() + ": failed to delete");
					JOptionPane.showMessageDialog(this, "Deletion failed: " + ex.toString(), "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			} else
				log(file.toString() + ": cancelled by user");
		}
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
			private static final long serialVersionUID = 9209165124982821882L;

			@Override
			public void actionPerformed(ActionEvent e) {
				closeFile(currentFile);
			}
		});
		actionMap.put("saveFile", new AbstractAction() {
			private static final long serialVersionUID = -7470772844920841362L;

			@Override
			public void actionPerformed(ActionEvent e) {
				saveCurrentFile();
			}
		});
		actionMap.put("uicopy", new AbstractAction() {
			private static final long serialVersionUID = -2848581416420978728L;

			@Override
			public void actionPerformed(ActionEvent e) {
				copy();
			}
		});
		actionMap.put("uipaste", new AbstractAction() {
			private static final long serialVersionUID = 1787082954032698363L;

			@Override
			public void actionPerformed(ActionEvent e) {
				paste();
			}
		});
		actionMap.put("uicut", new AbstractAction() {
			private static final long serialVersionUID = -1105434388308503474L;

			@Override
			public void actionPerformed(ActionEvent e) {
				cut();
			}
		});
		actionMap.put("uiundo", new AbstractAction() {
			private static final long serialVersionUID = 8406056850614767499L;

			@Override
			public void actionPerformed(ActionEvent e) {
				undo();
			}
		});
		actionMap.put("uiredo", new AbstractAction() {
			private static final long serialVersionUID = -3657508890779731058L;

			@Override
			public void actionPerformed(ActionEvent e) {
				redo();
			}
		});
		actionMap.put("run", new AbstractAction() {
			private static final long serialVersionUID = 229256907978587772L;

			@Override
			public void actionPerformed(ActionEvent e) {
				runCommand();
			}
		});
		actionMap.put("search", new AbstractAction() {
			private static final long serialVersionUID = -4547284488040065226L;

			@Override
			public void actionPerformed(ActionEvent e) {
				Search.getInstance().showSearchDialog(thisObj);
			}
		});
		actionMap.put("replace", new AbstractAction() {
			private static final long serialVersionUID = 5959636973284522325L;

			@Override
			public void actionPerformed(ActionEvent e) {
				showReplaceDialog();
			}
		});
		actionMap.put("fileuirename", new AbstractAction() {
			private static final long serialVersionUID = 5809902219160600284L;

			@Override
			public void actionPerformed(ActionEvent e) {
				rename((File)projectTree.getLastSelectedPathComponent());
			}
		});
		actionMap.put("fileuidelete", new AbstractAction() {
			private static final long serialVersionUID = 3233089824931581771L;

			@Override
			public void actionPerformed(ActionEvent e) {
				deleteFile((File)projectTree.getLastSelectedPathComponent());
			}
		});
	}

	private void copy() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			Component component = openFilesMap.get(currentFile);
			if(component instanceof RSyntaxTextArea)
				((RSyntaxTextArea)component).copy();
		}
	}

	private void paste() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			Component component = openFilesMap.get(currentFile);
			if(component instanceof RSyntaxTextArea)
				((RSyntaxTextArea)component).paste();
		}
	}

	private void cut() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			Component component = openFilesMap.get(currentFile);
			if(component instanceof RSyntaxTextArea)
				((RSyntaxTextArea)component).cut();
		}
	}

	private void undo() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			Component component = openFilesMap.get(currentFile);
			if(component instanceof RSyntaxTextArea)
				((RSyntaxTextArea)component).undoLastAction();
		}
	}

	private void redo() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			Component component = openFilesMap.get(currentFile);
			if(component instanceof RSyntaxTextArea)
				((RSyntaxTextArea)component).redoLastAction();
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
	
	private void rename(File file) {
		{
			String newName = (String) JOptionPane.showInputDialog(null, "Enter new name for " + file.getName() + ":",
					"Rename", JOptionPane.PLAIN_MESSAGE, null, null, file.getName());
			if (newName != null && !newName.trim().isEmpty()) {
				File newFile = new File(file.getParentFile(), newName);
				if (file.renameTo(newFile)) {
					// Update references to the renamed file
					int tabbedIndex = tabbedPane.indexOfTab(file.getName());
					if (tabbedIndex >= 0) {
						JLabel tabLabel = (JLabel) ((JPanel) tabbedPane.getTabComponentAt(tabbedIndex)).getComponent(0);
						tabLabel.setText(dirtyMap.get(file) ? newFile.getName() + " * " : newFile.getName() + " ");
					}

					// Update the tree node
					DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) projectTree.getSelectionPath()
							.getLastPathComponent();
					treeModel.reload(selectedNode);

					log(file.toString() + ": renamed to " + newFile.toString());
				} else {
					log(file.toString() + ": failed to rename");
					JOptionPane.showMessageDialog(this, "Renaming failed.", "Error", JOptionPane.ERROR_MESSAGE);
				}
			} else {
				log(file.toString() + ": rename cancelled by user");
			}
		}
	}

	public String getSelectedText() {
		if (currentFile != null && openFilesMap.containsKey(currentFile)) {
			Component component = openFilesMap.get(currentFile);
			if(component instanceof RSyntaxTextArea)
				return ((RSyntaxTextArea)component).getSelectedText();
		}
		return null;
	}

	private void showReplaceDialog() {
		JOptionPane.showMessageDialog(this, "Replace functionality not implemented yet.", "Replace",
				JOptionPane.INFORMATION_MESSAGE);
	}

	public static void main(String[] args) {
		try {
            // Install FlatLaf themes
            UIManager.installLookAndFeel("Light", FlatLightLaf.class.getName());
            UIManager.installLookAndFeel("Dark", FlatDarkLaf.class.getName());
            UIManager.installLookAndFeel("Darcula", FlatDarculaLaf.class.getName());
            UIManager.installLookAndFeel("IntelliJ", FlatIntelliJLaf.class.getName());
            
            LookAndFeelInfo[] infos = FlatAllIJThemes.INFOS;
            for (LookAndFeelInfo info : infos) {
                UIManager.installLookAndFeel(info.getName(), info.getClassName());
            }
            
            // Set the default look and feel
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            e.printStackTrace();
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
		Component component = openFilesMap.get(currentFile);
		if(component!=null && component instanceof RSyntaxTextArea)
			return (RSyntaxTextArea)component;
		return null;
	}
}
