package Reika.FCompile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import Reika.FCompile.util.FileIO;

public class Settings {

	private final HashMap<String, String> data = new HashMap();

	public Settings(File f) {
		try {
			ArrayList<String> li = FileIO.getFileAsLines(f);
			for (String s : li) {
				if (s.isEmpty() || s.startsWith("#"))
					continue;
				String[] parts = s.split("=");
				if (parts.length != 2)
					throw new RuntimeException("Illegal line in setting file: '" + s + "'");
				data.put(parts[0], parts[1]);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String getSetting(String key) {
		return data.containsKey(key) ? data.get(key) : "";
	}

	public int getIntSetting(String key) {
		return Integer.parseInt(this.getSetting(key));
	}

	public boolean getBooleanSetting(String key) {
		return Boolean.parseBoolean(this.getSetting(key));
	}

	public String[] getArraySetting(String key) {
		return this.getSetting(key).split(",");
	}

}
