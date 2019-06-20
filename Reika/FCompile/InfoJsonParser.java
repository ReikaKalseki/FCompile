package Reika.FCompile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import Reika.FCompile.util.FileIO;

public final class InfoJsonParser {

	private final File reference;

	private final HashMap<String, String> data = new HashMap();

	public InfoJsonParser(File modDir) {
		reference = new File(modDir, "info.json");
	}

	public void read() throws IOException {
		ArrayList<String> li = FileIO.getFileAsLines(reference);
		for (String s : li) {
			while (s.charAt(0) == ' ' || s.charAt(0) == '\t')
				s = s.substring(1);
			if (s.startsWith("\"")) {
				s = s.replaceAll("\"", "");
				s = s.substring(0, s.length() - 1);// delete final comma
				String[] parts = s.split(": ");
				if (parts.length == 2) //skip empty fields
					data.put(parts[0], parts[1]);
			}
		}
	}

	public String getName() {
		return data.get("name");
	}

	public String getAuthor() {
		return data.get("author");
	}

	public ModVersion getVersion() {
		return new ModVersion(data.get("version"));
	}

	public String getData(String key) {
		return data.get(key);
	}

	public boolean exists() {
		return reference.exists();
	}

}
