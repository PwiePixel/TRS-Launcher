package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.ui.Canvas;

/**
 * Die Pixel-Symbole auf dem Handy (MC-Reels), genau wie in den freigegebenen Bildern. Sie stehen nicht in
 * {@code core.ui.Icons}, weil sie nicht 8×8 groß sind (Herz 9×8, Flamme 7×9, Kamera 11×7 …).
 */
final class PhoneIcons {
	static final String[] HEART = {".##...##.", "####.####", "#########", "#########", ".#######.", "..#####..", "...###...",
			"....#...."};
	static final String[] TALK = {".#######.", "#########", "#########", "#########", "#########", ".#######.", "..##.....",
			".##......"};
	static final String[] FLAME = {"...#...", "..##...", "..###..", ".####.#", ".######", "#######", "###.###", "##...##",
			".##.##."};
	static final String[] CAM = {"...####....", "###########", "####...####", "###.....###", "###.....###", "####...####",
			"###########"};
	static final String[] HOME = {"....#....", "...###...", "..#####..", ".#######.", "#########", ".##...##.", ".##...##.",
			".#######."};
	static final String[] FLIP = {"..#####..", ".#.....#.", "#......##", "#.....###", "#........", "###.....#", "##......#",
			".#.....#.", "..#####.."};
	static final String[] X = {"#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"};

	/** Schatten der Symbole: dieselbe Form, um eine halbe Pixelgröße nach rechts unten versetzt. */
	static final int SHADOW = 0x80000000;

	private PhoneIcons() {
	}

	/** Breite des Symbols in Fensterpixeln bei {@code size} Fensterpixeln je Symbol-Pixel. */
	static int width(String[] rows, int size) {
		return rows[0].length() * Math.max(1, size);
	}

	static int height(String[] rows, int size) {
		return rows.length * Math.max(1, size);
	}

	/**
	 * Zeichnet ein Symbol mit der linken oberen Ecke bei (x, y).
	 *
	 * @param size   Fensterpixel je Symbol-Pixel (mindestens 1)
	 * @param shadow mit Schlagschatten (halbe Pixelgröße nach rechts unten, wie im Entwurf)
	 */
	static void draw(Canvas c, String[] rows, int x, int y, int size, int argb, boolean shadow) {
		int s = Math.max(1, size);
		if (shadow) {
			int d = Math.max(1, s / 2);
			runs(c, rows, x + d, y + d, s, SHADOW);
		}
		runs(c, rows, x, y, s, argb);
	}

	/** Je Zeile ein Rechteck je zusammenhängendem Lauf. */
	private static void runs(Canvas c, String[] rows, int x, int y, int s, int argb) {
		for (int r = 0; r < rows.length; r++) {
			String row = rows[r];
			int start = -1;
			for (int i = 0; i <= row.length(); i++) {
				boolean on = i < row.length() && row.charAt(i) == '#';
				if (on && start < 0) start = i;
				if (!on && start >= 0) {
					c.fill(x + start * s, y + r * s, x + i * s, y + (r + 1) * s, argb);
					start = -1;
				}
			}
		}
	}
}
