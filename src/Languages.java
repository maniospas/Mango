import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class Languages {
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

	public static Languages readYamlConfig(File yamlFile) throws IOException {
		Yaml yaml = new Yaml(new Constructor(Languages.class));
		try (InputStream inputStream = Files.newInputStream(yamlFile.toPath())) {
			return yaml.load(inputStream);
		}
	}
}
