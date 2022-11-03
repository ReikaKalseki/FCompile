package Reika.FCompile.Compile;

import java.io.File;
import java.io.IOException;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class InfoJsonParser {

	private final File reference;

	private JsonObject data;

	public InfoJsonParser(File modDir) {
		reference = new File(modDir, "info.json");
	}

	public void read() throws IOException {/*
		ArrayList<String> li = FileIO.getFileAsLines(reference);
		for (String s : li) {
			if (s == null || s.isEmpty())
				continue;
			while (s.charAt(0) == ' ' || s.charAt(0) == '\t')
				s = s.substring(1);
			if (s.startsWith("\"")) {
				s = s.replaceAll("\"", "");
				s = s.substring(0, s.length() - 1);// delete final comma
				String[] parts = s.split(": ");
				if (parts.length == 2) //skip empty fields
					data.put(parts[0], parts[1]);
			}
		}*/
		data = new JsonParser().parse(Files.toString(reference, Charsets.UTF_8)).getAsJsonObject();
	}

	public String getName() {
		return this.getData("name");
	}

	public String getAuthor() {
		return this.getData("author");
	}

	public ModVersion getVersion() {
		return new ModVersion(this.getData("version"));
	}

	public String getData(String key) {
		return data.get(key).getAsString();
	}

	public boolean exists() {
		return reference.exists();
	}

}
