
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

public class Search {
	private String previousSearchText = "";

	// RSyntaxTextArea textArea = openFilesMap.get(currentFile);
	private void searchFirst(CodeEditor parent, RSyntaxTextArea textArea, String text, boolean caseSensitive,
			boolean wrapAround) {
		if (textArea != null) {
			String content = caseSensitive ? textArea.getText() : textArea.getText().toLowerCase();
			String searchText = caseSensitive ? text : text.toLowerCase();

			int searchStart = content.indexOf(searchText, 0);

			if (searchStart >= 0) {
				textArea.setCaretPosition(searchStart);
				textArea.moveCaretPosition(searchStart + text.length());
			} else {
				JOptionPane.showMessageDialog(parent, "No first occurrence found.", "Search",
						JOptionPane.INFORMATION_MESSAGE);
			}
		}
	}

	private void searchNext(CodeEditor parent, RSyntaxTextArea textArea, String text, boolean caseSensitive,
			boolean wrapAround) {
		if (textArea != null) {
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
					JOptionPane.showMessageDialog(parent, "No occurrences found.", "Search",
							JOptionPane.INFORMATION_MESSAGE);
				}
			} else {
				JOptionPane.showMessageDialog(parent, "No next occurrence found.", "Search",
						JOptionPane.INFORMATION_MESSAGE);
			}
		}
	}

	private void searchPrevious(CodeEditor parent, RSyntaxTextArea textArea, String text, boolean caseSensitive,
			boolean wrapAround) {
		if (textArea != null) {
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
					JOptionPane.showMessageDialog(parent, "No occurrences found.", "Search",
							JOptionPane.INFORMATION_MESSAGE);
				}
			} else {
				JOptionPane.showMessageDialog(parent, "No previous occurrence found.", "Search",
						JOptionPane.INFORMATION_MESSAGE);
			}
		}
	}

	private int countOccurrences(CodeEditor parent, RSyntaxTextArea textArea, String searchText,
			boolean caseSensitive) {
		if (textArea != null) {
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

	private void updateOccurrences(CodeEditor parent, RSyntaxTextArea textArea, JPanel buttons, JLabel occurrenceLabel,
			String searchText, boolean caseSensitive) {
		if (searchText.length() == 0) {
			occurrenceLabel.setText("Type above  ");
			buttons.setVisible(false);
			previousSearchText = searchText;
			return;
		}
		int occurrences = countOccurrences(parent, textArea, searchText, caseSensitive);
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

	public void showSearchDialog(CodeEditor parent) {
		JDialog searchDialog = new JDialog(parent, "Search", false);
		searchDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
		Action escapeAction = new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				searchDialog.dispose();
			}
		};
		searchDialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
		searchDialog.getRootPane().getActionMap().put("ESCAPE", escapeAction);
		searchDialog.setLayout(new BorderLayout());
		searchDialog.setSize(350, 180);
		searchDialog.setLocationRelativeTo(parent);

		JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JTextField searchField = new JTextField(30);
		searchField.setToolTipText("Write text & press Enter to search");

		String selectedText = parent.getSelectedText();
		if (selectedText != null && !selectedText.isEmpty()) {
			searchField.setText(selectedText);
			previousSearchText = selectedText;
		} else {
			searchField.setText(previousSearchText);
		}
		searchField.selectAll();

		JCheckBox caseSensitiveCheckBox = new JCheckBox("Case Sensitive");
		JCheckBox wrapAroundCheckBox = new JCheckBox("Wrap Around", true);
		searchField.addActionListener(e -> searchNext(parent, parent.getCurrentTextArea(), searchField.getText(),
				caseSensitiveCheckBox.isSelected(), wrapAroundCheckBox.isSelected()));

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
		firstButton.addActionListener(e -> searchFirst(parent, parent.getCurrentTextArea(), searchField.getText(),
				caseSensitiveCheckBox.isSelected(), wrapAroundCheckBox.isSelected()));

		JButton nextButton = new JButton(new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/next.png"))
				.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
		nextButton.setToolTipText("Next");
		nextButton.setSize(16, 16);
		nextButton.addActionListener(e -> searchNext(parent, parent.getCurrentTextArea(), searchField.getText(),
				caseSensitiveCheckBox.isSelected(), wrapAroundCheckBox.isSelected()));

		JButton previousButton = new JButton(
				new ImageIcon(new ImageIcon(CodeEditor.class.getResource("/icons/previous.png")).getImage()
						.getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
		previousButton.setToolTipText("Previous");
		previousButton.setSize(16, 16);
		previousButton.addActionListener(e -> searchPrevious(parent, parent.getCurrentTextArea(), searchField.getText(),
				caseSensitiveCheckBox.isSelected(), wrapAroundCheckBox.isSelected()));

		buttons.add(firstButton);
		buttons.add(previousButton);
		buttons.add(nextButton);

		buttonPanel.add(buttons, BorderLayout.SOUTH);

		searchField.getDocument().addDocumentListener(new SimpleDocumentListener() {
			@Override
			public void documentChanged() {
				updateOccurrences(parent, parent.getCurrentTextArea(), buttons, occurrenceLabel, searchField.getText(),
						caseSensitiveCheckBox.isSelected());
			}
		});
		caseSensitiveCheckBox.addActionListener(e -> updateOccurrences(parent, parent.getCurrentTextArea(), buttons,
				occurrenceLabel, searchField.getText(), caseSensitiveCheckBox.isSelected()));

		buttons.setVisible(false);

		searchDialog.add(inputPanel, BorderLayout.NORTH);
		searchDialog.add(checkBoxPanel, BorderLayout.WEST);
		searchDialog.add(buttonPanel, BorderLayout.EAST);

		updateOccurrences(parent, parent.getCurrentTextArea(), buttons, occurrenceLabel, searchField.getText(),
				caseSensitiveCheckBox.isSelected());

		searchDialog.pack();
		searchDialog.setResizable(false);
		searchDialog.setVisible(true);
	}

	private Search() {
	}

	private static Search instance = new Search();

	public static Search getInstance() {
		return instance;
	}

	abstract static class SimpleDocumentListener implements DocumentListener {
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
}
