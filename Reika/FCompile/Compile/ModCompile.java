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

import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

	private File outputFile;
	private long lastModified;
	private long lastSize;

	public ModCompile(File f, InfoJsonParser p, Settings s) {
		modFolder = f;
		info = p;
		settings = s;

		logPerFile = settings.getBooleanSetting("log_per_file");

		this.loadFilters();
	}

	public void setOutput(File out) {
		ModVersion override = this.getVersionOverride();
		String folderName = override == null ? modFolder.getName() : this.calcFolderName(override);
		outputFile = new File(out, folderName + ".zip");
		lastModified = outputFile.exists() ? outputFile.lastModified() : -1;
		lastSize = outputFile.exists() ? outputFile.length() : 0;
	}

	public boolean isTooNew(int thresh, long time) {
		return (time-lastModified) <= thresh*1000;
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

	public void compile(ArrayList<FileSwap> swaps) throws IOException {
		//File workingDir = new File(new File(output, info.getName()), WORKING_DIRECTORY);
		File workingDir = new File(outputFile.getParentFile(), outputFile.getName().substring(0, outputFile.getName().length()-4));
		workingDir.mkdir();
		ModVersion override = this.getVersionOverride();
		for (File f : modFolder.listFiles()) {
			this.handleFileInFolder(f, workingDir, override, swaps);
		}
		outputFile.delete();
		if (outputFile.exists()) {
			FileUtils.deleteDirectory(workingDir);
			throw new RuntimeException("Cannot compile " + info.getName() + "; output file already exists!");
		}
		//ZipHelper.zipFolder(workingDir.getAbsolutePath(), zip.getAbsolutePath());
		FileIO.zipFolder(workingDir.getAbsolutePath(), outputFile.getAbsolutePath());
		FileUtils.deleteDirectory(workingDir);
	}

	public void upload(String apiKey) throws Exception {
		HttpClient client = HttpClients.createDefault();
		String url = "https://mods.factorio.com/api/v2/mods/releases/init_upload";
		//HttpsURLConnection c = (HttpsURLConnection)new URL(url).openConnection();
		//c.setRequestMethod("POST");
		HttpPost post = new HttpPost(url);
		post.addHeader("Authorization", "Bearer "+apiKey);
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.EXTENDED);
		StringBody modname = new StringBody(info.getName(), ContentType.MULTIPART_FORM_DATA);
		builder.addPart("mod", modname);
		HttpEntity entity = builder.build();
		post.setEntity(entity);
		CloseableHttpResponse response = (CloseableHttpResponse)client.execute(post);
		int code = response.getCode();
		if (code == 404) {
			Main.log("Could not find mod portal entry for '"+info.getName()+"'; is the mod not released?", true);
			return;
		}
		if (code != 200)
			throw new RuntimeException("Received error code when executing upload init: "+code);
		entity = response.getEntity();

		JsonObject ret = new JsonParser().parse(EntityUtils.toString(entity)).getAsJsonObject();
		if (!ret.has("upload_url")) {
			throw new RuntimeException("Failed to upload mod due to '"+ret.get("error").getAsString()+"': "+ret.get("message").getAsString());
		}
		url = ret.get("upload_url").getAsString();

		post = new HttpPost(url);
		post.addHeader("Authorization", "Bearer "+apiKey);
		FileBody fileBody = new FileBody(outputFile, ContentType.DEFAULT_BINARY);
		builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.EXTENDED);
		builder.addPart("file", fileBody);
		entity = builder.build();
		post.setEntity(entity);
		response = (CloseableHttpResponse)client.execute(post);
		entity = response.getEntity();
		ret = new JsonParser().parse(EntityUtils.toString(entity)).getAsJsonObject();
		if (!ret.get("success").getAsBoolean()) {
			throw new RuntimeException("Failed to upload mod due to '"+ret.get("error").getAsString()+"': "+ret.get("message").getAsString());
		}
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
