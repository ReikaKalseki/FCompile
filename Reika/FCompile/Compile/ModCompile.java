package Reika.FCompile.Compile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;

import Reika.FCompile.Main;
import Reika.FCompile.Settings;
import Reika.FCompile.util.FileIO;

public class ModCompile {

	//private static final String WORKING_DIRECTORY = "compiletemp";

	private final File modFolder;
	public final InfoJsonParser info;
	private final Settings settings;

	private final boolean logPerFile;

	private final HashSet<String> ignoredExtensions = new HashSet();
	private final ArrayList<String> ignoredStrings = new ArrayList();

	private final ArrayList<String> imageFilter = new ArrayList();
	private final ArrayList<String> soundFilter = new ArrayList();

	public ModCompile(File f, InfoJsonParser p, Settings s) {
		modFolder = f;
		info = p;
		settings = s;

		logPerFile = settings.getBooleanSetting("log_per_file");

		this.loadFilters();
	}

	private void loadFilters() {
		String[] ext = settings.getArraySetting("ignored_filetypes");
		for (int i = 0; i < ext.length; i++) {
			ignoredExtensions.add(ext[i].replaceAll("\\.", ""));
		}

		String[] str = settings.getArraySetting("ignored_strings");
		for (int i = 0; i < str.length; i++) {
			ignoredStrings.add(str[i]);
		}

		String[] img = settings.getArraySetting("image_filter");
		for (int i = 0; i < img.length; i++) {
			imageFilter.add(img[i]);
		}

		String[] snd = settings.getArraySetting("sound_filter");
		for (int i = 0; i < snd.length; i++) {
			soundFilter.add(snd[i]);
		}
	}

	public void compile(File output, ArrayList<FileSwap> swaps) throws IOException {
		ModVersion override = this.getVersionOverride();
		//File workingDir = new File(new File(output, info.getName()), WORKING_DIRECTORY);
		String folderName = override == null ? modFolder.getName() : this.calcFolderName(override);
		File workingDir = new File(output, folderName);
		workingDir.mkdir();
		for (File f : modFolder.listFiles()) {
			this.handleFileInFolder(f, workingDir, override, swaps);
		}
		File zip = new File(output, folderName + ".zip");
		if (zip.exists()) {
			FileIO.deleteDirectoryWithContents(workingDir);
			throw new RuntimeException("Cannot compile " + info.getName() + "; output file already exists!");
		}
		//ZipHelper.zipFolder(workingDir.getAbsolutePath(), zip.getAbsolutePath());
		FileIO.zipFolder(workingDir.getAbsolutePath(), zip.getAbsolutePath());
		FileIO.deleteDirectoryWithContents(workingDir);
	}

	private ModVersion getVersionOverride() {
		String version = settings.getSetting("version_override");
		ModVersion override = null;
		if (!version.equals("none")) {
			override = new ModVersion(version);
		}
		return override;
	}

	private String calcFolderName(ModVersion override) {
		String pre = modFolder.getName().substring(0, modFolder.getName().length() - "_0.0.0".length());
		return pre + "_" + override.toString();
	}

	private void handleFileInFolder(File f, File workingDir, ModVersion override, ArrayList<FileSwap> swaps) throws IOException {
		String n = f.getName();
		//if (n.equals(WORKING_DIRECTORY))
		//	return;
		/*
		if (n.contains(".git"))
			return;
		 */
		for (String s : ignoredStrings) {
			if (n.contains(s))
				return;
		}
		if (f.isDirectory()) {
			for (File f2 : f.listFiles()) {
				this.handleFileInFolder(f2, workingDir, override, swaps);
			}
		}
		else {
			FileReference fr = new FileReference(f);
			boolean ignore = this.ignoreFile(fr);
			if (logPerFile)
				Main.log("Parsing file: " + fr + "; ignored: " + ignore);
			if (!ignore) {
				for (FileSwap s : swaps) {
					if (s.matchFile(this, f)) {
						File repl = s.getSwap();
						Main.log("Swapping file '"+f+"' with '"+repl+"'");
						f = repl;
					}
				}
				File f2 = new File(workingDir, fr.relativePath);
				f2.getParentFile().mkdirs();
				f2.createNewFile();
				OutputStream out = new FileOutputStream(f2);
				try {
					if (override != null && fr.isInfoJSON()) {
						this.copyInfoJSONWithOverride(f, f2, override);
					}
					else {
						Files.copy(f.toPath(), out);
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				out.close();
			}
		}
	}

	private void copyInfoJSONWithOverride(File f, File f2, ModVersion override) throws IOException {
		FileOutputStream fw = new FileOutputStream(f2);
		Reader fr = new FileReader(f);
		BufferedReader br = new BufferedReader(fr);

		while (br.ready()) {
			String s = br.readLine();
			if (s.contains("\"version\"")) {
				s = s.substring(0, s.length() - "\"0.0.0\",".length());
				s = s + "\"" + override.toString() + "\",";
			}
			if (!s.isEmpty()) {
				s = s.replaceAll(" :", ":");
				if (!s.contains("}"))
					s = s + System.lineSeparator();
				fw.write(s.getBytes());
			}
		}

		fw.close();
		br.close();
		fr.close();
	}

	private boolean ignoreFile(FileReference f) {
		String n = f.file.getName();
		//if (n.equals(WORKING_DIRECTORY))
		//	return true;
		/*
		if (n.contains(".git"))
			return true;
		if (n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".pdn") || n.endsWith(".psd"))
			return true;
		if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".bmp") || n.endsWith(".gif"))
			return !f.relativePath.startsWith("/graphics");
		 */
		if (f.isRootFolder() && n.equalsIgnoreCase("thumbnail.png"))
			return false;
		String ext = f.getExtension();
		if (ignoredExtensions.contains(ext))
			return true;
		for (String s : ignoredStrings) {
			if (n.contains(s))
				return true;
		}
		if (!imageFilter.isEmpty()) {
			if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif")) {
				for (String s : imageFilter) {
					if (f.relativePath.startsWith(s))
						return false;
				}
				return true;
			}
		}
		if (!soundFilter.isEmpty()) {
			if (n.endsWith(".ogg")) {
				for (String s : soundFilter) {
					if (f.relativePath.startsWith(s))
						return false;
				}
				return true;
			}
		}

		return false;
	}

	private class FileReference {

		private final File file;
		private final String relativePath;

		private FileReference(File f) {
			file = f;
			relativePath = f.getAbsolutePath().substring(modFolder.getAbsolutePath().length()).replaceAll("\\\\", "/").substring(1);
		}

		public boolean isRootFolder() {
			return relativePath.equals(file.getName());
		}

		public String getExtension() {
			String n = file.getName();
			return n.substring(n.length() - 3, n.length());
		}

		public boolean isInfoJSON() {
			return file.getName().equals("info.json");
		}

		@Override
		public String toString() {
			return file.toString();
		}

	}
}
