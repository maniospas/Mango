import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

public class ANSI {
	public static void appendAnsiText(JTextPane textPane, String text) {
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

	public static String ansiToHtml(String ansiText) {
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
			if (c > 127) { // Non-ASCII characters
				result.append("&#").append((int) c).append(";");
			} else {
				result.append(c);
			}
		}
		return result.toString();
	}

	private static String convertAnsiCodeToHtmlColor(String code) {
		switch (code) {
		case "\u001B[30m":
			return "#3B4252;"; // Black
		case "\u001B[31m":
			return "#BF616A;"; // Red
		case "\u001B[32m":
			return "#A3BE8C;"; // Green
		case "\u001B[33m":
			return "#EBCB8B;"; // Yellow
		case "\u001B[34m":
			return "#81A1C1;"; // Blue
		case "\u001B[35m":
			return "#B48EAD;"; // Magenta
		case "\u001B[36m":
			return "#88C0F0;"; // Cyan
		case "\u001B[37m":
			return "#FFFFFF;"; // White
		case "\u001B[90m":
			return "#4C566A;"; // Bright Black
		case "\u001B[91m":
			return "#BF616A;"; // Bright Red
		case "\u001B[92m":
			return "#A3BE8C;"; // Bright Green
		case "\u001B[93m":
			return "#EBCB8B;"; // Bright Yellow
		case "\u001B[94m":
			return "#81A1C1;"; // Bright Blue
		case "\u001B[95m":
			return "#B48EAD;"; // Bright Magenta
		case "\u001B[96m":
			return "#8FBCFF;"; // Bright Cyan
		case "\u001B[97m":
			return "#ECEFF4;"; // Bright White
		case "\u001B[0m":
			return null; // Reset
		default:
			return null;
		}
	}
}
