package Reika.FCompile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import com.google.common.base.Strings;

import Reika.FCompile.Compile.FileSwap;
import Reika.FCompile.Compile.InfoJsonParser;
import Reika.FCompile.Compile.ModCompile;
import Reika.FCompile.util.FileIO;

public class Main {

	private static final ArrayList<String> logger = new ArrayList();

	private static final Calendar calendar = Calendar.getInstance();
	private static final SimpleDateFormat date = new SimpleDateFormat("dd-MM-yyyy/HH:mm:ss.SSS");

	public static void main(String[] args) throws IOException {
		String set = loadArgOrDefault(0, args, "settings.ini");
		String logFile = loadArgOrDefault(1, args, "status.log");
		File setFile = new File(set);
		if (!setFile.exists())
			throw new IllegalArgumentException("Settings file does not exist at " + setFile.getAbsolutePath() + "!");
		Settings settings = new Settings(setFile);
		String modFolder = settings.getSetting("mod_directory");
		File modDir = new File(modFolder);
		if (!modDir.exists())
			throw new IllegalArgumentException("Specified mod directory does not exist!");
		File log = prepareLogger(logFile);

		PrintStream logging = new PrintStream(new FileOutputStream(log, true));
		System.setOut(new TStream(System.out, logging));
		System.setErr(new TStream(System.err, logging));

		ArrayList<FileSwap> swaps = loadSwaps(settings);
		File[] files = modDir.listFiles();
		ArrayList<ModCompile> mods = new ArrayList();
		String author = settings.getSetting("target_author");
		File output = new File(settings.getSetting("output_directory"));

		for (File f : files) {
			if (f.isDirectory()) {
				InfoJsonParser p = new InfoJsonParser(f);
				if (p.exists()) { //non-mod folders
					p.read();
					if (p.getAuthor().equals(author)) {
						mods.add(new ModCompile(f, p, settings));
					}
				}
			}
		}

		if (mods.isEmpty()) {
			log("Found no mods by '" + author + "'!");
			return;
		}

		for (ModCompile mod : mods) {
			mod.setOutput(output);
		}

		output.mkdirs();

		flushLogger(log);
		ArrayList<String> failed = new ArrayList();
		int compiled = 0;
		int thresh = settings.getIntSetting("min_age");
		for (ModCompile mod : mods) {
			try {
				long time = System.currentTimeMillis();
				if (mod.isTooNew(thresh, time)) {
					log("Skipping '" + mod.info.getName() + "' v" + mod.info.getVersion() + " - too new");
					continue;
				}
				log("Compiling '" + mod.info.getName() + "' v" + mod.info.getVersion() + "...");
				mod.compile(swaps);
				log("Compiled '" + mod.info.getName() + "' in " + (System.currentTimeMillis() - time) + " ms.");
				compiled++;
			}
			catch (Exception e) {
				log("Could not compile '" + mod.info.getName() + "' v" + mod.info.getVersion() + "!");
				e.printStackTrace();
				failed.add(mod.info.getName());
			}
			flushLogger(log);
		}
		log("===================================================================");
		log("Successfully compiled " + compiled + "/" + mods.size() + " mods.");
		if (!failed.isEmpty()) {
			log(failed.size() + " mods failed to compile:");
			for (String s : failed) {
				log(s);
			}
		}
		flushLogger(log);
		log("===================================================================");
		String apiKey = settings.getSetting("api_key");
		if (!Strings.isNullOrEmpty(apiKey)) {
			log("Attempting uploads.");
			log("===================================================================");
			failed.clear();
			compiled = 0;
			for (ModCompile mod : mods) {
				try {
					long time = System.currentTimeMillis();
					log("Uploading '" + mod.info.getName() + "' v" + mod.info.getVersion() + "...");
					mod.upload(apiKey);
					log("Uploaded '" + mod.info.getName() + "' in " + (System.currentTimeMillis() - time) + " ms.");
					compiled++;
				}
				catch (Exception e) {
					log("Could not upload '" + mod.info.getName() + "' v" + mod.info.getVersion() + "!");
					e.printStackTrace();
					failed.add(mod.info.getName());
				}
				flushLogger(log);
			}
			log("===================================================================");
			log("Successfully uploaded " + compiled + "/" + mods.size() + " mods.");
			if (!failed.isEmpty()) {
				log(failed.size() + " mods failed to upload:");
				for (String s : failed) {
					log(s);
				}
			}
			flushLogger(log);
		}
		System.out.println("Terminating.");
	}

	private static ArrayList<FileSwap> loadSwaps(Settings s) {
		String line = s.getSetting("swap_ins");
		String[] parts = line.split(",");
		ArrayList<FileSwap> li = new ArrayList();
		for (String sg : parts) {
			FileSwap f = FileSwap.parse(sg);
			li.add(f);
		}
		return li;
	}

	private static void flushLogger(File log) throws IOException {
		if (logger.isEmpty())
			return;
		try {
			FileIO.writeLinesToFile(log, logger, true);
			logger.clear();
		}
		catch (IOException e) {
			e.printStackTrace();
			logger.add(e.toString());
		}
	}

	private static File prepareLogger(String logFile) throws IOException {
		File log = new File(logFile);
		if (log.exists())
			log.delete();
		if (log.getParentFile() != null)
			log.getParentFile().mkdirs();
		log.createNewFile();
		return log;
	}

	public static void log(String s) {
		log(s, false);
	}

	public static void log(String s, boolean isError) {
		if (isError)
			System.err.println(s);
		else
			System.out.println(s);
		//logger.add(getTimestamp() + s);
	}

	private static String getTimestamp() {
		long t = System.currentTimeMillis();
		Date d = new Date(t);
		calendar.setTimeInMillis(t);
		return date.format(d) + ": ";
	}

	private static String loadArgOrDefault(int arg, String[] args, String def) {
		return args.length > arg ? args[arg] : def;
	}

	private static boolean loadArgOrDefault(int arg, String[] args, boolean def) {
		return args.length > arg ? Boolean.parseBoolean(args[arg]) : def;
	}

	private static int loadArgOrDefault(int arg, String[] args, int def) {
		return args.length > arg ? Integer.parseInt(args[arg]) : def;
	}

	private static class TStream extends PrintStream {

		private final PrintStream out;

		private TStream(PrintStream out1, PrintStream out2) {
			super(out1);
			out = out2;
		}

		@Override
		public void write(byte buf[], int off, int len) {
			super.write(buf, off, len);
			out.write(buf, off, len);
		}

		@Override
		public void flush() {
			super.flush();
			out.flush();
		}
	}
}
