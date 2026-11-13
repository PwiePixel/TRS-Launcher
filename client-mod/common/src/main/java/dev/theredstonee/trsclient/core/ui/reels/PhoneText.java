package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.Paint;

import java.util.ArrayList;
import java.util.List;

/**
 * Schrift auf dem Handy (MC-Reels): die Schrift des Spiels in ganzzahliger Vergrößerung, gezeichnet in Fensterpixeln.
 * Ein Schrift-Pixel ist immer {@code m} ganze Fensterpixel groß – so bleibt der Text bei jeder GUI-Skalierung scharf.
 *
 * <p>Die freigegebenen Bilder nutzen eine andere Pixelschrift (Jersey 15); die Schrift des Spiels ist etwa ein Fünftel
 * breiter. Deshalb bekommt <b>jeder</b> Text auf dem Handy eine Höchstbreite: Einzeiler werden erst eine Stufe kleiner
 * ({@link #fit}), dann mit „…“ gekürzt ({@link #clip}); Fließtext wird umbrochen und auf eine Zeilenzahl begrenzt
 * ({@link #wrap}).
 *
 * <p>Farben immer mit Deckkraft angeben (0xFF……): Text mit Deckkraft 0 zeichnet das Spiel gar nicht.
 */
final class PhoneText {
	/** Entwurfs-Schriftgröße (px) je Schrift-Pixel: Großbuchstaben sind im Entwurf 0,6 em hoch, in der Spielschrift 7 px. */
	static final float MOCK_PX_PER_FONT_PX = 11.67f;
	/** Höhe einer Textzeile in Schrift-Pixeln, die wirklich bemalt wird (7 Großbuchstaben + 1 Unterlänge). */
	static final int GLYPH_H = 8;
	/** Höhe der Großbuchstaben in Schrift-Pixeln (zum senkrechten Zentrieren). */
	static final int CAP_H = 7;
	private static final String DOTS = "…";

	private PhoneText() {
	}

	/**
	 * Vergrößerung für eine Schriftgröße aus dem Entwurf.
	 *
	 * @param mockPx Schriftgröße im Entwurf (CSS-px im Handy)
	 * @param q      Handy-Pixel → Fensterpixel ({@code PhoneLayout.q})
	 * @return Fensterpixel je Schrift-Pixel, mindestens 1
	 */
	static int scale(float mockPx, float q) {
		return Math.max(1, Math.round(mockPx * q / MOCK_PX_PER_FONT_PX));
	}

	/** Breite des Textes in Fensterpixeln bei Vergrößerung {@code m}. */
	static int width(Canvas c, String s, int m) {
		return s == null || s.isEmpty() ? 0 : c.textWidth(s) * Math.max(1, m);
	}

	/** Höhe einer Zeile in Fensterpixeln (ohne Zeilenabstand). */
	static int height(int m) {
		return GLYPH_H * Math.max(1, m);
	}

	/** y für Text, dessen Großbuchstaben in einem Kasten ab {@code top} mit Höhe {@code boxH} mittig stehen. */
	static int centerY(int top, int boxH, int m) {
		return top + Math.round((boxH - CAP_H * Math.max(1, m)) / 2f);
	}

	/** Text mit der linken oberen Ecke bei (x, y) – ohne Breitenprüfung; nur für Texte, die sicher passen (Zahlen). */
	static void draw(Canvas c, String s, int x, int y, int m, int argb, boolean shadow) {
		if (s == null || s.isEmpty() || (argb >>> 24) == 0) return;
		int k = Math.max(1, m);
		if (k == 1) {
			c.text(s, x, y, argb, shadow);
			return;
		}
		c.push();
		c.translate(x, y);
		c.scale(k);
		c.text(s, 0, 0, argb, shadow);
		c.pop();
	}

	/**
	 * Größte Vergrößerung ≤ {@code m}, bei der der Text in {@code maxWidth} passt – höchstens eine Stufe kleiner (dann
	 * wird gekürzt), nie unter 1.
	 */
	static int fit(Canvas c, String s, int m, int maxWidth) {
		int k = Math.max(1, m);
		if (k > 1 && width(c, s, k) > maxWidth) k--;
		return k;
	}

	/** Kürzt den Text mit „…“ auf {@code maxWidth} Fensterpixel bei Vergrößerung {@code m} (leer, wenn nichts passt). */
	static String clip(Canvas c, String s, int m, int maxWidth) {
		if (s == null || s.isEmpty() || maxWidth <= 0) return "";
		int k = Math.max(1, m);
		int room = maxWidth / k;
		if (c.textWidth(s) <= room) return s;
		int dots = c.textWidth(DOTS);
		if (room < dots) return "";
		String cut = c.clip(s, room - dots);
		// Kein Leerzeichen vor den Punkten stehen lassen.
		int end = cut.length();
		while (end > 0 && cut.charAt(end - 1) == ' ') end--;
		return cut.substring(0, end) + DOTS;
	}

	/**
	 * Einzeiler mit Höchstbreite: passt er nicht, erst eine Stufe kleiner, dann mit „…“ gekürzt. Bei kleinerer Stufe
	 * bleibt er auf derselben Mittellinie wie in der vollen Größe.
	 *
	 * @param y obere Kante bei voller Größe {@code m}
	 * @return gezeichnete Breite in Fensterpixeln
	 */
	static int line(Canvas c, String s, int x, int y, int m, int maxWidth, int argb, boolean shadow) {
		if (s == null || s.isEmpty() || maxWidth <= 0) return 0;
		int k = fit(c, s, m, maxWidth);
		String t = clip(c, s, k, maxWidth);
		if (t.isEmpty()) return 0;
		int dy = Math.round((CAP_H * Math.max(1, m) - CAP_H * k) / 2f);
		draw(c, t, x, y + dy, k, argb, shadow);
		return width(c, t, k);
	}

	/** Wie {@link #line}, aber mittig um {@code centerX}. */
	static int centered(Canvas c, String s, int centerX, int y, int m, int maxWidth, int argb, boolean shadow) {
		if (s == null || s.isEmpty() || maxWidth <= 0) return 0;
		int k = fit(c, s, m, maxWidth);
		String t = clip(c, s, k, maxWidth);
		if (t.isEmpty()) return 0;
		int w = width(c, t, k);
		int dy = Math.round((CAP_H * Math.max(1, m) - CAP_H * k) / 2f);
		draw(c, t, centerX - w / 2, y + dy, k, argb, shadow);
		return w;
	}

	/** Wie {@link #line}, aber rechtsbündig ({@code right} = rechte Kante). */
	static int right(Canvas c, String s, int right, int y, int m, int maxWidth, int argb, boolean shadow) {
		if (s == null || s.isEmpty() || maxWidth <= 0) return 0;
		int k = fit(c, s, m, maxWidth);
		String t = clip(c, s, k, maxWidth);
		if (t.isEmpty()) return 0;
		int w = width(c, t, k);
		int dy = Math.round((CAP_H * Math.max(1, m) - CAP_H * k) / 2f);
		draw(c, t, right - w, y + dy, k, argb, shadow);
		return w;
	}

	/**
	 * Umbricht Fließtext auf {@code maxWidth} Fensterpixel und höchstens {@code maxLines} Zeilen. Was nicht mehr passt,
	 * endet mit „…“; ein einzelnes Wort, das breiter ist als die Zeile, wird gekürzt.
	 */
	static List<String> wrap(Canvas c, String s, int m, int maxWidth, int maxLines) {
		List<String> out = new ArrayList<String>();
		if (s == null || s.isEmpty() || maxWidth <= 0 || maxLines <= 0) return out;
		int k = Math.max(1, m);
		List<String> lines = Paint.wrap(c, s, maxWidth / k);
		for (int i = 0; i < lines.size() && out.size() < maxLines; i++) {
			boolean last = out.size() == maxLines - 1 && i < lines.size() - 1;
			String line = last ? Paint.join(lines, i) : lines.get(i);
			String fitted = clip(c, line, k, maxWidth);
			if (!fitted.isEmpty()) out.add(fitted);
			if (last) break;
		}
		return out;
	}

	/** Das Ende des Textes, das in {@code maxWidth} passt (Eingabefelder zeigen beim Tippen den Schluss). */
	static String tail(Canvas c, String s, int m, int maxWidth) {
		if (s == null || s.isEmpty() || maxWidth <= 0) return "";
		int room = maxWidth / Math.max(1, m);
		int start = 0;
		while (start < s.length() && c.textWidth(s.substring(start)) > room) {
			start += Character.isHighSurrogate(s.charAt(start)) && start + 1 < s.length() ? 2 : 1;
		}
		return s.substring(start);
	}
}
