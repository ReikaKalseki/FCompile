package Reika.FCompile.Compile;

import java.io.File;

public class FileSwap {

	public final String modName;
	public final String sourceFile;
	public final String targetFile;

	private FileSwap(String n, String s, String t) {
		modName = n;
		sourceFile = s;
		targetFile = t;
	}

	public static FileSwap parse(String s) {
		String content = s.substring(1, s.length()-1);
		String[] parts = content.split("\\|");
		if (parts.length != 3)
			throw new IllegalArgumentException("Invalid file swap, wrong number of params! "+s);
		return new FileSwap(parts[0], parts[1], parts[2]);
	}

	public boolean matchFile(ModCompile mc, File f) {
		return mc.info.getName().equals(modName) && f.getAbsolutePath().endsWith(sourceFile);
	}

	public File getSwap() {
		return new File(targetFile);
	}

}
