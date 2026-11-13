package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.perf.ShaderPacks;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Name des aktiven Shaderpacks für die Angaben am Foto (MC-Reels). Ohne harte Abhängigkeit: gefragt wird per Reflection
 * die statische Iris-Methode {@code net.irisshaders.iris.Iris#getCurrentPackName()} – ein Iris-Interna, das sich ändern
 * kann. Schlägt der Aufruf fehl, bleibt der Name für den Rest der Sitzung einfach unbekannt; nichts wird gemeldet.
 */
public final class ShaderName {
	static final String IRIS = "net.irisshaders.iris.Iris";
	static final String METHOD = "getCurrentPackName";

	/** Nach dem ersten Fehlschlag wird nie wieder gefragt. */
	private static volatile boolean failed;
	private static Method method;

	private ShaderName() {
	}

	/** Name des aktiven Shaderpacks – null, wenn keines aktiv ist oder der Name unbekannt ist. */
	public static String current() {
		if (failed || !ShaderPacks.active()) return null;
		return clean(read(ShaderPacks.class.getClassLoader(), IRIS));
	}

	/** Roher Name aus der statischen Methode {@code getCurrentPackName()} dieser Klasse; null nach einem Fehlschlag. */
	static synchronized String read(ClassLoader loader, String className) {
		if (failed) return null;
		try {
			if (method == null) {
				Method m = Class.forName(className, false, loader).getMethod(METHOD);
				if (!Modifier.isStatic(m.getModifiers()) || m.getReturnType() != String.class) throw new NoSuchMethodException(METHOD);
				method = m;
			}
			return (String) method.invoke(null);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			failed = true;
			method = null;
			return null;
		}
	}

	/** Nur für Tests: wieder fragen dürfen. */
	static synchronized void resetForTest() {
		failed = false;
		method = null;
	}

	static boolean failedForTest() {
		return failed;
	}

	/**
	 * Roher Name in brauchbarer Form: null, leer, {@code (off)} und {@code (internal)} sind kein Name; die Endung
	 * {@code .zip} fällt weg; höchstens {@link ReelsMeta#MAX_SHADER} Zeichen.
	 */
	static String clean(String raw) {
		if (raw == null) return null;
		String s = raw.trim();
		if (s.isEmpty() || "(off)".equals(s) || "(internal)".equals(s)) return null;
		if (s.length() >= 4 && s.regionMatches(true, s.length() - 4, ".zip", 0, 4)) s = s.substring(0, s.length() - 4);
		s = ReelsPost.clean(s, ReelsMeta.MAX_SHADER);
		return s.isEmpty() ? null : s;
	}
}
