import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class Languages {
	private Map<String, Language> tasks;

	public Languages overwrite(Languages other) {
		if (other != null)
			for (String lang : other.getTasks().keySet())
				if (!tasks.containsKey(lang))
					tasks.put(lang, other.getTasks().get(lang));
		return this;
	}

	public static class Language {
		private String name;
		private ArrayList<String> extensions;
		private String highlighter;
		private String command;

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

	public Map<String, Language> getTasks() {
		return tasks;
	}

	public void setTasks(Map<String, Language> languages) {
		this.tasks = languages;
	}

	public static Languages readYamlConfig(File yamlFile, Languages previous) throws IOException {
		Yaml yaml = new Yaml(new Constructor(Languages.class));
		try (InputStream inputStream = Files.newInputStream(yamlFile.toPath())) {
			return ((Languages) yaml.load(inputStream)).overwrite(previous);
		}
	}
}
