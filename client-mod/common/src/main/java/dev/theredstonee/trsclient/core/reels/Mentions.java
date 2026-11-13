package dev.theredstonee.trsclient.core.reels;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Erwähnungen in Kommentaren (MC-Reels): {@code @} und danach 1 bis 16 Namenszeichen {@code [A-Za-z0-9_]}, am Anfang
 * oder nach einem Zeichen, das kein Namenszeichen ist. Eine Folge von 17 oder mehr Namenszeichen ist keine Erwähnung,
 * {@code a@b} auch nicht. Reine Textrechnung – wen eine Erwähnung erreicht, entscheidet {@link Pings}.
 */
public final class Mentions {
	/** Höchstlänge eines Spielernamens (Zeichen). */
	public static final int MAX_NAME = 16;

	/** Ergebnis von {@link #complete}: der neue Text und die neue Stelle der Schreibmarke. */
	public static final class Edit {
		public final String text;
		public final int caret;

		public Edit(String text, int caret) {
			this.text = text;
			this.caret = caret;
		}
	}

	private Mentions() {
	}

	static boolean nameChar(char c) {
		return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_';
	}

	/** Gültiger Spielername: {@code [A-Za-z0-9_]{1,16}}. */
	public static boolean validName(String name) {
		if (name == null || name.isEmpty() || name.length() > MAX_NAME) return false;
		for (int i = 0; i < name.length(); i++) if (!nameChar(name.charAt(i))) return false;
		return true;
	}

	/** {@code {start, end}} jeder Erwähnung im Text, das {@code @} eingeschlossen. */
	public static List<int[]> spans(String text) {
		List<int[]> out = new ArrayList<int[]>();
		if (text == null) return out;
		int n = text.length();
		int i = 0;
		while (i < n) {
			if (text.charAt(i) != '@' || i > 0 && nameChar(text.charAt(i - 1))) {
				i++;
				continue;
			}
			int end = i + 1;
			while (end < n && nameChar(text.charAt(end))) end++;
			int length = end - i - 1;
			if (length >= 1 && length <= MAX_NAME) out.add(new int[]{i, end});
			i = Math.max(end, i + 1);
		}
		return out;
	}

	/** Erwähnt der Text diesen Namen (ohne Groß-/Kleinschreibung, nur als ganzer Name)? */
	public static boolean mentions(String text, String name) {
		if (text == null || name == null || name.isEmpty()) return false;
		for (int[] s : spans(text)) {
			if (text.substring(s[0] + 1, s[1]).equalsIgnoreCase(name)) return true;
		}
		return false;
	}

	/** Erwähnt der Text jemand anderen als {@code self}? */
	public static boolean mentionsOther(String text, String self) {
		if (text == null) return false;
		for (int[] s : spans(text)) {
			if (self == null || !text.substring(s[0] + 1, s[1]).equalsIgnoreCase(self)) return true;
		}
		return false;
	}

	/**
	 * Der Teil eines Namens, der vor der Schreibmarke getippt ist (ohne das {@code @}; darf leer sein) – null, wenn dort
	 * gerade kein Name getippt wird: zwischen dem letzten {@code @} vor der Schreibmarke und ihr stehen nur
	 * Namenszeichen (höchstens 16), das {@code @} steht am Anfang oder nach einem Zeichen, das kein Namenszeichen ist,
	 * und an der Schreibmarke selbst steht kein Namenszeichen (das Textende zählt).
	 */
	public static String token(String text, int caret) {
		if (text == null || caret < 1 || caret > text.length()) return null;
		if (caret < text.length() && nameChar(text.charAt(caret))) return null;
		int at = caret - 1;
		while (at >= 0 && nameChar(text.charAt(at))) at--;
		if (at < 0 || text.charAt(at) != '@') return null;
		if (at > 0 && nameChar(text.charAt(at - 1))) return null;
		if (caret - at - 1 > MAX_NAME) return null;
		return text.substring(at + 1, caret);
	}

	/**
	 * Namen, die mit {@code prefix} anfangen (ohne Groß-/Kleinschreibung), höchstens {@code max}: nur gültige
	 * Spielernamen, ohne den eigenen und ohne Doppelte. Die Quellen bleiben in ihrer Reihenfolge, innerhalb einer
	 * Quelle wird nach dem Alphabet sortiert.
	 */
	public static List<String> suggest(String prefix, List<Collection<String>> sources, String self, int max) {
		List<String> out = new ArrayList<String>();
		if (sources == null || max <= 0) return out;
		String want = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
		Set<String> taken = new HashSet<String>();
		if (self != null) taken.add(self.toLowerCase(Locale.ROOT));
		for (Collection<String> source : sources) {
			if (source == null) continue;
			List<String> names = new ArrayList<String>();
			for (String name : source) {
				if (validName(name) && name.toLowerCase(Locale.ROOT).startsWith(want)) names.add(name);
			}
			Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
			for (String name : names) {
				if (!taken.add(name.toLowerCase(Locale.ROOT))) continue;
				out.add(name);
				if (out.size() >= max) return out;
			}
		}
		return out;
	}

	/**
	 * Den getippten Namen vor der Schreibmarke ({@link #token}) durch {@code "@name "} ersetzen; die Schreibmarke steht
	 * danach dahinter. Steht hinter der Schreibmarke schon ein Leerzeichen, kommt kein zweites dazu. Passt das
	 * Leerzeichen nicht mehr in {@code maxLength}, fällt es weg; passt auch der Name nicht (oder wird dort kein Name
	 * getippt), bleibt alles, wie es ist.
	 */
	public static Edit complete(String text, int caret, String name, int maxLength) {
		String token = token(text, caret);
		if (token == null || !validName(name)) return new Edit(text, caret);
		int at = caret - token.length() - 1;
		String head = text.substring(0, at) + "@" + name;
		String tail = text.substring(caret);
		if (!tail.isEmpty() && tail.charAt(0) == ' ') {
			return head.length() + tail.length() <= maxLength ? new Edit(head + tail, head.length() + 1) : new Edit(text, caret);
		}
		if (head.length() + 1 + tail.length() <= maxLength) return new Edit(head + " " + tail, head.length() + 1);
		if (head.length() + tail.length() <= maxLength) return new Edit(head + tail, head.length());
		return new Edit(text, caret);
	}
}
