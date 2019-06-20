package Reika.FCompile;

public class ModVersion implements Comparable<ModVersion> {

	public final int primary;
	public final int secondary;
	public final int tertiary;

	public ModVersion(String s) {
		this(parse(s));
	}

	private ModVersion(int[] vals) {
		this(vals[0], vals[1], vals[2]);
	}

	public ModVersion(int pri, int sec, int ter) {
		primary = pri;
		secondary = sec;
		tertiary = ter;
	}

	private static int[] parse(String s) {
		String[] parts = s.split("\\.");
		if (parts.length != 3)
			throw new IllegalArgumentException("Invalid version string '" + s + "'!");
		try {
			Integer.parseInt(parts[0]);
			Integer.parseInt(parts[1]);
			Integer.parseInt(parts[2]);
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid version string '" + s + "'!");
		}
		return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
	}

	@Override
	public String toString() {
		return primary + "." + secondary + "." + tertiary;
	}

	@Override
	public int compareTo(ModVersion o) {
		int c1 = Integer.compare(primary, o.primary);
		int c2 = Integer.compare(secondary, o.secondary);
		int c3 = Integer.compare(tertiary, o.tertiary);
		return c1 == 0 ? (c2 == 0 ? c3 : c2) : c1;
	}
}
