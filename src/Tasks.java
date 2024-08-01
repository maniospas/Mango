import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import javax.swing.JTabbedPane;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class Tasks {
	private Map<String, Task> tasks;

	public Tasks overwrite(Tasks other) {
		if (other != null)
			for (String lang : other.getTasks().keySet())
				if (!tasks.containsKey(lang))
					tasks.put(lang, other.getTasks().get(lang));
		return this;
	}

	public static class Task {
		private ArrayList<String> extensions = new ArrayList<String>();
		private String highlighter = "txt";
		private String command = "";

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

	public Map<String, Task> getTasks() {
		return tasks;
	}

	public void setTasks(Map<String, Task> tasks) {
		this.tasks = tasks;
	}

	public static Tasks readYamlConfig(File yamlFile, Tasks previous) throws IOException {
		Yaml yaml = new Yaml(new Constructor(Tasks.class));
		try (InputStream inputStream = Files.newInputStream(yamlFile.toPath())) {
			return ((Tasks) yaml.load(inputStream)).overwrite(previous);
		}
	}
}
