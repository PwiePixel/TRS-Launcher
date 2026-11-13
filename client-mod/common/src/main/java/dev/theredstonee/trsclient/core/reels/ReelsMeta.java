package dev.theredstonee.trsclient.core.reels;

import java.util.Locale;

/**
 * Womit ein Foto aufgenommen wurde (Gson-DTO, steht als {@code meta} am Post in {@code feed.json}): Minecraft-Version,
 * Loader, Name des Shaderpacks und Name der Instanz im TRS Launcher. Jeder Wert darf fehlen (null); ein Post ganz ohne
 * Angaben hat kein {@code meta}.
 */
public final class ReelsMeta {
	/** Höchstlänge von Version und Loader (Zeichen). */
	public static final int MAX_SHORT = 16;
	/** Höchstlänge des Shader-Namens (Zeichen). */
	public static final int MAX_SHADER = 40;
	/** Höchstlänge des Instanz-Namens (Zeichen). */
	public static final int MAX_PACK = 64;

	/** Minecraft-Version, z. B. {@code 26.2}. */
	public String mc;
	/** Loader in Kleinbuchstaben, z. B. {@code fabric}. */
	public String loader;
	/** Name des Shaderpacks (Datei- oder Ordnername), nur wenn eines aktiv war. */
	public String shader;
	/** Name der Instanz im TRS Launcher. */
	public String pack;

	public ReelsMeta() {
	}

	public ReelsMeta(String mc, String loader, String shader, String pack) {
		this.mc = mc;
		this.loader = loader;
		this.shader = shader;
		this.pack = pack;
	}

	public ReelsMeta copy() {
		return new ReelsMeta(mc, loader, shader, pack);
	}

	/**
	 * Die Angaben in brauchbarer Form als neues Objekt: ungültige Werte fallen weg, Namen werden gesäubert und gekürzt.
	 * Null, wenn danach nichts übrig ist (oder {@code m} null war).
	 */
	public static ReelsMeta normalized(ReelsMeta m) {
		if (m == null) return null;
		ReelsMeta n = new ReelsMeta(version(m.mc), loaderId(m.loader), name(m.shader, MAX_SHADER), name(m.pack, MAX_PACK));
		return n.mc == null && n.loader == null && n.shader == null && n.pack == null ? null : n;
	}

	/** {@code [0-9A-Za-z .+_-]{1,16}}, sonst null. */
	private static String version(String raw) {
		if (raw == null) return null;
		String s = raw.trim();
		if (s.isEmpty() || s.length() > MAX_SHORT) return null;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean ok = c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == ' ' || c == '.' || c == '+'
					|| c == '_' || c == '-';
			if (!ok) return null;
		}
		return s;
	}

	/** Kleinbuchstaben, {@code [a-z]{1,16}}, sonst null. */
	private static String loaderId(String raw) {
		if (raw == null) return null;
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty() || s.length() > MAX_SHORT) return null;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 'a' || c > 'z') return null;
		}
		return s;
	}

	private static String name(String raw, int max) {
		String s = ReelsPost.clean(raw, max);
		return s.isEmpty() ? null : s;
	}

	/** Anzeigename eines Loaders (Eigenname): fabric → Fabric, neoforge → NeoForge; null bleibt null. */
	public static String loaderLabel(String loader) {
		if (loader == null || loader.isEmpty()) return null;
		String l = loader.toLowerCase(Locale.ROOT);
		if ("fabric".equals(l)) return "Fabric";
		if ("quilt".equals(l)) return "Quilt";
		if ("forge".equals(l)) return "Forge";
		if ("neoforge".equals(l)) return "NeoForge";
		return l.substring(0, 1).toUpperCase(Locale.ROOT) + l.substring(1);
	}

	/** {@code 26.2 · Fabric}, oder nur der Teil, den es gibt, oder null. */
	public String versionLabel() {
		String l = loaderLabel(loader);
		boolean hasMc = mc != null && !mc.isEmpty();
		if (hasMc && l != null) return mc + " · " + l;
		if (hasMc) return mc;
		return l;
	}
}
