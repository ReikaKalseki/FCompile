package Reika.FCompile.Compile;

import java.io.BufferedReader;
import java.io.File;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import Reika.FCompile.Main;
import Reika.FCompile.util.FileIO;

public class Changelog {

	private static final Date game10Release = parseDateStatic("Aug 14 2020");

	private static final TreeMap<Date, String> gameVersions = new TreeMap();

	private static final String SEPARATOR = getStringOf("-", 99);

	private final Calendar throwawayCalendar = Calendar.getInstance();

	public final ModCompile mod;
	private final File inputFile;
	public final File outputFile;

	private final ArrayList<Release> releases = new ArrayList();
	private final HashMap<String, ReleaseCandidate> portalData = new HashMap();

	private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d yyyy"); //DO NOT MAKE STATIC - not threadsafe

	static {
		gameVersions.put(parseDateStatic("Jan 13 2013"), "0.2");
		gameVersions.put(parseDateStatic("Mar 29 2013"), "0.3");
		gameVersions.put(parseDateStatic("May 3 2013"), "0.4");
		gameVersions.put(parseDateStatic("Jun 7 2013"), "0.5");
		gameVersions.put(parseDateStatic("Jul 26 2013"), "0.6");
		gameVersions.put(parseDateStatic("Sep 27 2013"), "0.7");
		gameVersions.put(parseDateStatic("Dec 6 2013"), "0.8");
		gameVersions.put(parseDateStatic("Feb 14 2014"), "0.9");
		gameVersions.put(parseDateStatic("Jun 6 2014"), "0.10");
		gameVersions.put(parseDateStatic("Oct 31 2014"), "0.11");
		gameVersions.put(parseDateStatic("Jul 17 2015"), "0.12");
		gameVersions.put(parseDateStatic("Jun 27 2016"), "0.13");
		gameVersions.put(parseDateStatic("Aug 26 2016"), "0.14");
		gameVersions.put(parseDateStatic("Apr 24 2017"), "0.15");
		gameVersions.put(parseDateStatic("Dec 13 2017"), "0.16");
		gameVersions.put(parseDateStatic("Feb 26 2019"), "0.17");
		gameVersions.put(parseDateStatic("Jan 21 2020"), "0.18");
		gameVersions.put(game10Release, "1.0");
		gameVersions.put(parseDateStatic("Nov 23 2020"), "1.1");
	}

	private static Date parseDateStatic(String s) {
		try {
			return new SimpleDateFormat("MMM d yyyy").parse(s.trim());
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	private Date parseDate(String s) {
		try {
			return dateFormat.parse(s.trim());
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	public Changelog(ModCompile mod, File in, File out) {
		this.mod = mod;
		inputFile = in;
		outputFile = out;
	}

	private static String getStringOf(String s, int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++)
			sb.append(s);
		return sb.toString();
	}

	private void loadVersionData() throws Exception {
		File f9 = new File("cached_version_data");
		if (!f9.exists())
			f9.mkdirs();

		String id = mod.info.getName();
		File json = new File(f9, id+".json");
		if (!json.exists() || json.length() == 0 || json.lastModified()+1000L*mod.settings.getIntSetting("ver_cache_lifetime") < System.currentTimeMillis()) {
			Main.log("Downloading mod portal data for "+mod);
			json.createNewFile();
			String url = "https://mods.factorio.com/api/mods/"+id+"/full";
			try (BufferedReader r = FileIO.getReader(new URL(url), 5000)) {
				if (r == null) {
					Main.log("Could not read URL '"+url+"'! Is the mod not released?", true);
				}
				else {
					FileIO.writeLinesToFile(json, FileIO.getFileAsLines(r), false);
				}
			}
		}
		try (BufferedReader r = FileIO.getReader(json)) {
			JsonReader jr = new JsonReader(r);
			jr.setLenient(true);
			JsonElement e = new JsonParser().parse(jr); //root JSONObject
			if (e instanceof JsonObject) {
				JsonObject j = (JsonObject)e;
				String creation = j.get("created_at").getAsString(); //format: 2019-06-06T17:29:53.131000Z
				JsonArray downloads = (JsonArray)j.get("releases");
				for (JsonElement d : downloads) {
					JsonObject table = (JsonObject)d;
					String date = table.get("released_at").getAsString();
					String ver = table.get("version").getAsString();
					String gv = table.get("info_json").getAsJsonObject().get("factorio_version").getAsString();
					Date time = Date.from(Instant.parse(date));
					if (gv.equals("0.18") && time.compareTo(game10Release) >= 0) {
						gv = "1.0";
					}
					portalData.put(ver, new ReleaseCandidate(ver, gv, time));
				}
			}
		}
	}

	public void generate() throws Exception {
		this.loadVersionData();
		outputFile.delete();
		outputFile.createNewFile();
		ArrayList<String> li = FileIO.getFileAsLines(inputFile);
		ReleaseCandidate releaseCandidate = null;
		for (String s : li) {
			s = s.trim();
			if (s.isEmpty())
				continue;
			else if (s.startsWith("------")) {
				if (releaseCandidate != null)
					releases.add(releaseCandidate.build());
				releaseCandidate = new ReleaseCandidate();
			}
			else if (s.startsWith("Version:")) {
				if (releaseCandidate == null)
					releaseCandidate = new ReleaseCandidate();
				releaseCandidate.name = s.substring("Version:".length());
			}
			else if (s.startsWith("Date:")) {
				if (releaseCandidate == null)
					releaseCandidate = new ReleaseCandidate();
				releaseCandidate.date = this.parseDate(s.substring("Date:".length()));
			}
			else if (s.startsWith("Game:")) {
				if (releaseCandidate == null)
					releaseCandidate = new ReleaseCandidate();
				releaseCandidate.gameVersion = s.substring("Game:".length());
			}
			else if (s.startsWith("-")) {
				releaseCandidate.list.add(s);
			}
		}
		Collections.sort(releases);
		this.cleanupReleaseList();
		ArrayList<String> li2 = new ArrayList();
		for (Release r : releases) {
			li2.add(SEPARATOR);
			r.generateOutput(li2);
		}
		FileIO.writeLinesToFile(outputFile, li2, false);
	}

	private void cleanupReleaseList() {
		for (int i = releases.size()-1; i > 0; i--) {
			Release r0 = releases.get(i);
			Release r1 = releases.get(i-1);
			if (r0.name.equals("0.0.0")) {
				releases.remove(i);
				continue;
			}
			if (r0.name.equals(r1.name)) {
				r1.merge(r0);
				releases.remove(i);
				continue;
			}
		}
		for (Release r : releases) {
			int idx = r.outputName.indexOf('.');
			String[] parts = r.name.split("\\.");
			String[] parts1 = r.name.split("\\.");
			String gv = r.gameVersion;
			if (gv.startsWith("1.")) {
				int subVer = Integer.parseInt(gv.substring(2));
				gv = String.format("%03d", 100+subVer);
			}
			if (parts[1].equals("0") || parts[1].length() == 2)
				parts[1] = parts[2];
			r.outputName = parts[0]+"."+gv.replace(".", "")+"."+parts[1];
			//System.out.println(r.name+" > "+r.outputName);
		}
	}

	private class ReleaseCandidate {

		private String name;
		private String gameVersion;
		private Date date;

		private final ArrayList<String> list = new ArrayList();

		private ReleaseCandidate() {

		}

		private ReleaseCandidate(String ver, String gv, Date d) {
			name = ver;
			gameVersion = gv;
			date = d;
		}

		private Release build() {
			Release r = new Release(name.trim(), gameVersion != null ? gameVersion.trim() : null, date);
			for (String s : list) {
				r.addRawChange(s);
			}
			return r;
		}

	}

	private class Release implements Comparable<Release> {

		private final String name;
		private final String gameVersion;
		private final String date;

		private String outputName;

		private final Date dateValue;
		private final Calendar calendar = Calendar.getInstance();

		private final EnumMap<Category, ArrayList<String>> changes = new EnumMap(Category.class);

		private Release(String n, String gv, Date d) {
			name = n;
			outputName = name;

			calendar.setTime(d);
			ReleaseCandidate rc = portalData.get(n);
			if (rc != null && !this.isSameDay(rc)) {
				Main.log(mod+" - Date mismatch in files vs mod portal: "+n+" ["+gv+"] @ "+dateFormat.format(d)+" but portal says it is "+dateFormat.format(rc.date)+"; using portal", true);
				d = rc.date;
			}

			dateValue = d;
			date = dateFormat.format(d);
			gameVersion = Strings.isNullOrEmpty(gv) ? this.tryGetGameVersion() : gv;

		}

		private boolean isSameDay(ReleaseCandidate rc) {
			throwawayCalendar.setTime(rc.date);
			return throwawayCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) && throwawayCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) && throwawayCalendar.get(Calendar.DAY_OF_MONTH) == calendar.get(Calendar.DAY_OF_MONTH);
		}

		@Override
		public String toString() {
			return name+" ["+gameVersion+"] @ "+date+" > "+changes.keySet().size();
		}

		public void merge(Release r0) {
			for (Entry<Category, ArrayList<String>> e : r0.changes.entrySet()) {
				for (String s : e.getValue())
					this.addChange(e.getKey(), s);
			}
		}

		public void generateOutput(ArrayList<String> li) {
			li.add("Version: "+outputName);
			li.add("Date: "+date);
			for (Entry<Category, ArrayList<String>> e : changes.entrySet()) {
				li.add("  "+e.getKey().displayName+":");
				for (String s : e.getValue())
					li.add("    - "+s);
			}
		}

		private String tryGetGameVersion() {
			String[] parts = name.split("\\.");
			if (parts.length == 3) {
				String inner = parts[1];
				if (inner.length() == 2)
					return "0."+inner;
			}
			ReleaseCandidate release = portalData.get(name);
			if (release != null) {
				return release.gameVersion;
			}
			return gameVersions.floorEntry(dateValue).getValue();
		}

		private void addRawChange(String s) {
			s = s.trim();
			if (s.charAt(0) == '-')
				s = s.substring(1).trim();
			Category cat = Category.GENERIC;
			String sl = s.toLowerCase(Locale.ENGLISH);
			if (sl.startsWith("fix") || sl.startsWith(" missing ") || sl.contains(" issue") || sl.contains("script error") || sl.contains(" crash")) {
				cat = Category.FIX;
			}
			else if (sl.startsWith("added") || sl.startsWith("new") || sl.contains(" now have ")) {
				cat = Category.FEATURE;
			}
			else if (sl.contains("balance") || sl.startsWith("decreased") || sl.startsWith("reduced") || sl.startsWith("increased")) {
				cat = Category.BALANCE;
			}
			else if (sl.startsWith("remove")) {
				cat = Category.REMOVE;
			}
			this.addChange(cat, s);
		}

		private void addChange(Category c, String s) {
			ArrayList<String> li = changes.get(c);
			if (li == null) {
				li = new ArrayList();
				changes.put(c, li);
			}
			li.add(s);
			Collections.sort(li);
		}

		@Override
		public int compareTo(Release o) {
			return -dateValue.compareTo(o.dateValue);
		}

	}

	private enum Category {
		FIX("Bugfixes"),
		FEATURE("New Features"),
		BALANCE("Rebalancing"),
		GENERIC("Misc Changes"),
		REMOVE("Removed Features");

		public final String displayName;

		private Category(String s) {
			displayName = s;
		}
	}

}
