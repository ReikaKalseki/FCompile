package Reika.FCompile.Compile;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map.Entry;

import com.google.common.base.Strings;

import Reika.FCompile.util.FileIO;

public class Changelog {

	public final ModCompile mod;
	private final File inputFile;
	public final File outputFile;

	private final ArrayList<Release> releases = new ArrayList<Release>();

	private static final String SEPARATOR = "---------------------------------------";

	public Changelog(ModCompile mod, File in, File out) {
		this.mod = mod;
		inputFile = in;
		outputFile = out;
	}

	public void generate() throws IOException {
		outputFile.delete();
		outputFile.createNewFile();
		ArrayList<String> li = FileIO.getFileAsLines(inputFile);
		ReleaseCandidate releaseCandidate = null;
		for (String s : li) {
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
				releaseCandidate.date = s.substring("Date:".length());
			}
			else if (s.startsWith("Game:")) {
				if (releaseCandidate == null)
					releaseCandidate = new ReleaseCandidate();
				releaseCandidate.gameVersion = s.substring("Game:".length());
			}
			else {
				releaseCandidate.list.add(s);
			}
		}
		Collections.sort(releases);
		ArrayList<String> li2 = new ArrayList();
		li2.add(SEPARATOR);
		for (Release r : releases) {
			r.generateOutput(li2);
			li2.add(SEPARATOR);
		}
		FileIO.writeLinesToFile(outputFile, li2, false);
	}

	private static class ReleaseCandidate {

		private String name;
		private String gameVersion;
		private String date;

		private final ArrayList<String> list = new ArrayList();

		private Release build() {
			Release r = new Release(name.trim(), gameVersion != null ? gameVersion.trim() : null, date.trim());
			for (String s : list) {
				r.addRawChange(s);
			}
			return r;
		}

	}

	private static class Release implements Comparable<Release> {

		private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d yyyy");
		private final Date game10Release = this.parseDate("Aug 14 2020");
		private final Date game11Release = this.parseDate("Nov 23 2020");

		private final String name;
		private final String gameVersion;
		private final String date;

		private final Date dateValue;
		private final Calendar calendar = Calendar.getInstance();

		private final EnumMap<Category, ArrayList<String>> changes = new EnumMap(Category.class);

		private Release(String n, String gv, String d) {
			name = n;
			date = d.trim();

			try {
				dateValue = this.parseDate(date);
				calendar.setTime(dateValue);
			}
			catch (Exception e) {
				throw new RuntimeException("Error parsing date: "+date, e);
			}

			gameVersion = Strings.isNullOrEmpty(gv) ? this.tryGetGameVersion() : gv;
		}

		public void generateOutput(ArrayList<String> li) {
			li.add("Version: "+name);
			li.add("Date: "+date);
			for (Entry<Category, ArrayList<String>> e : changes.entrySet()) {
				li.add("\t- "+e.getKey().displayName);
				for (String s : e.getValue())
					li.add("\t\t- "+s);
			}
		}

		private String tryGetGameVersion() {
			String[] parts = name.split("\\.");
			if (parts.length == 3) {
				String inner = parts[1];
				if (inner.length() == 2)
					return "0."+inner;
			}
			try {
				if (dateValue.compareTo(game11Release) > 0)
					return "1.1";
				if (dateValue.compareTo(game10Release) > 0)
					return "1.0";
			}
			catch (Exception e) {
				throw new RuntimeException("Could not parse date for heuristic GV: "+name+"/"+date, e);
			}
			int yr = calendar.get(Calendar.YEAR);
			if (yr == 2018)
				return "0.16";
			if (yr == 2017)
				return "0.15";
			if (yr == 2015)
				return calendar.get(Calendar.MONTH) < 10 ? "0.11" : "0.12";
			throw new RuntimeException("Indeterminate game version: "+name+"/"+date);
		}

		private Date parseDate(String s) {
			try {
				return dateFormat.parse(s);
			}
			catch (ParseException e) {
				throw new RuntimeException(e);
			}
		}

		private void addRawChange(String s) {
			s = s.trim();
			if (s.charAt(0) == '-')
				s = s.substring(1).trim();
			Category cat = Category.GENERIC;
			String sl = s.toLowerCase(Locale.ENGLISH);
			if (sl.contains("fix") || sl.contains("issue") || sl.contains("crash")) {
				cat = Category.FIX;
			}
			if (sl.contains("added") || sl.contains("new")) {
				cat = Category.FEATURE;
			}
			if (sl.contains("remove")) {
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
		GENERIC("Misc Changes"),
		REMOVE("Removed Features");

		public final String displayName;

		private Category(String s) {
			displayName = s;
		}
	}

}