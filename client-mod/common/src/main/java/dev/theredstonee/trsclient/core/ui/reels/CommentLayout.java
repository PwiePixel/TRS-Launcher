package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.reels.Mentions;
import dev.theredstonee.trsclient.core.reels.ReelsComment;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Kommentar als farbige Stücke (MC-Reels): Name, Text, Erwähnungen und „(bearbeitet)“ – und ihr Umbruch auf Zeilen.
 * Der Umbruch ist ein eigener, weil jedes Stück seine Farbe behalten muss: er bricht an Leerzeichen, teilt nur ein
 * einzelnes Wort, das breiter ist als die Zeile (nie mitten in einem Zeichenpaar), und kürzt erst hinter der letzten
 * erlaubten Zeile mit „…“. Reine Textrechnung, die Breite kommt über {@link Measure}.
 */
final class CommentLayout {
	/** „Name: “ vor dem Text. */
	static final int NAME = 0;
	static final int TEXT = 1;
	/** Erwähnung ({@code @Name}) von jemand anderem. */
	static final int MENTION = 2;
	/** Erwähnung des eigenen Namens. */
	static final int MENTION_SELF = 3;
	/** „(bearbeitet)“ hinter dem Text. */
	static final int EDITED = 4;
	private static final String DOTS = "…";

	/** Ein Stück Text in einer Farbe. */
	static final class Piece {
		final String text;
		final int kind;

		Piece(String text, int kind) {
			this.text = text;
			this.kind = kind;
		}

		@Override
		public String toString() {
			return kind + ":\"" + text + "\"";
		}
	}

	/** Breite eines Textes in Schrift-Pixeln. */
	interface Measure {
		int width(String text);
	}

	private CommentLayout() {
	}

	/**
	 * Der Kommentar in Stücken: „Name: “, dann der Text – Erwähnungen ({@link Mentions}) als eigene Stücke, die des
	 * eigenen Namens ({@code self}, ohne Groß-/Kleinschreibung) mit eigener Art –, und bei einem bearbeiteten Kommentar
	 * dahinter {@code editedLabel} („(bearbeitet)“, mit einem Leerzeichen davor).
	 */
	static List<Piece> pieces(ReelsComment c, String self, String editedLabel) {
		List<Piece> out = new ArrayList<Piece>();
		if (c == null) return out;
		if (c.author != null && !c.author.isEmpty()) out.add(new Piece(c.author + ": ", NAME));
		String text = c.text == null ? "" : c.text;
		int at = 0;
		for (int[] span : Mentions.spans(text)) {
			if (span[0] > at) out.add(new Piece(text.substring(at, span[0]), TEXT));
			boolean own = self != null && !self.isEmpty() && text.substring(span[0] + 1, span[1]).equalsIgnoreCase(self);
			out.add(new Piece(text.substring(span[0], span[1]), own ? MENTION_SELF : MENTION));
			at = span[1];
		}
		if (at < text.length()) out.add(new Piece(text.substring(at), TEXT));
		if (c.editedAt != null && editedLabel != null && !editedLabel.isEmpty()) out.add(new Piece(" " + editedLabel, EDITED));
		return out;
	}

	/**
	 * Umbricht die Stücke auf Zeilen von höchstens {@code maxFontPx} Schrift-Pixeln. Jede Zeile besteht wieder aus
	 * Stücken; benachbarte Stücke derselben Art sind eines. Passt nicht alles in {@code maxLines} Zeilen, endet die
	 * letzte mit „…“.
	 */
	static List<List<Piece>> wrap(List<Piece> pieces, Measure measure, int maxFontPx, int maxLines) {
		List<List<Piece>> out = new ArrayList<List<Piece>>();
		if (pieces == null || measure == null || maxFontPx <= 0 || maxLines <= 0) return out;
		StringBuilder all = new StringBuilder();
		for (Piece p : pieces) if (p != null && p.text != null) all.append(p.text);
		String s = all.toString();
		int n = s.length();
		int[] kinds = new int[n];
		int k = 0;
		for (Piece p : pieces) {
			if (p == null || p.text == null) continue;
			for (int i = 0; i < p.text.length(); i++) kinds[k++] = p.kind;
		}
		int i = 0;
		while (i < n) {
			// Eine Zeile beginnt nie mit einem Leerzeichen.
			while (i < n && s.charAt(i) == ' ') i++;
			if (i >= n) break;
			int start = i;
			int end = i;
			int j = i;
			while (true) {
				int w0 = j;
				while (w0 < n && s.charAt(w0) == ' ') w0++;
				if (w0 >= n) break;
				int w1 = w0;
				while (w1 < n && s.charAt(w1) != ' ') w1++;
				if (measure.width(s.substring(start, w1)) <= maxFontPx) {
					end = w1;
					j = w1;
					continue;
				}
				// Das Wort passt nicht mehr: nächste Zeile – außer es ist allein schon breiter als die Zeile, dann wird
				// es geteilt (ein Zeichenpaar bleibt ganz, und mindestens ein Zeichen kommt immer mit).
				if (end == start) {
					int cut = PhoneText.after(s, start);
					while (cut < w1) {
						int next = PhoneText.after(s, cut);
						if (measure.width(s.substring(start, next)) > maxFontPx) break;
						cut = next;
					}
					end = cut;
				}
				break;
			}
			if (out.size() == maxLines - 1 && rest(s, end)) {
				out.add(line(s, kinds, start, dotted(s, start, end, measure, maxFontPx), true));
				return out;
			}
			out.add(line(s, kinds, start, end, false));
			i = end;
		}
		return out;
	}

	/** Steht ab {@code from} noch etwas anderes als Leerzeichen? */
	private static boolean rest(String s, int from) {
		for (int i = from; i < s.length(); i++) if (s.charAt(i) != ' ') return true;
		return false;
	}

	/** Ende der Zeile, hinter das noch „…“ passt (ohne Leerzeichen davor, nie mitten in einem Zeichenpaar). */
	private static int dotted(String s, int start, int end, Measure measure, int maxFontPx) {
		int cut = end;
		while (cut > start) {
			while (cut > start && s.charAt(cut - 1) == ' ') cut--;
			if (cut == start || measure.width(s.substring(start, cut) + DOTS) <= maxFontPx) break;
			cut = PhoneText.before(s, cut);
		}
		return cut;
	}

	/** Die Zeichen {@code start} bis {@code end} als Stücke; {@code dots}: „…“ hängt am letzten Stück. */
	private static List<Piece> line(String s, int[] kinds, int start, int end, boolean dots) {
		List<Piece> out = new ArrayList<Piece>();
		int from = start;
		for (int i = start + 1; i <= end; i++) {
			if (i < end && kinds[i] == kinds[from]) continue;
			if (i > from) out.add(new Piece(s.substring(from, i), kinds[from]));
			from = i;
		}
		if (dots) {
			if (out.isEmpty()) {
				out.add(new Piece(DOTS, TEXT));
			} else {
				Piece last = out.remove(out.size() - 1);
				out.add(new Piece(last.text + DOTS, last.kind));
			}
		}
		return out;
	}
}
