package dev.theredstonee.trsclient.core.reels;

/**
 * Vom Bild des Spiels zum Foto: mittiger Ausschnitt im Seitenverhältnis des Handys, Verkleinern per Flächenmittel
 * (nie vergrößern) und die Wahl des Verkleinerungsfaktors für den Sucher. Reine Rechnung auf ARGB-Feldern – läuft im
 * Hintergrund-Thread ({@link Reels#worker()}).
 */
public final class PhotoFrame {
	/** Längste Seite eines gespeicherten Fotos (Pixel). */
	public static final int MAX_LONG_SIDE = 1280;
	/** Längste Seite der Vorschau vor dem Posten (Pixel). */
	public static final int MAX_PREVIEW_SIDE = 512;

	private PhotoFrame() {
	}

	/** Seitenverhältnis der Fotos: quer 16:9, hochkant 9:16. */
	public static int[] aspect(PhoneHold hold) {
		return hold == PhoneHold.PORTRAIT ? new int[]{9, 16} : new int[]{16, 9};
	}

	/**
	 * Größtes mittiges Rechteck mit dem Seitenverhältnis {@code aspectW}:{@code aspectH} in einem Bild von
	 * {@code w}×{@code h}.
	 *
	 * @return {x, y, Breite, Höhe}
	 */
	public static int[] cropRect(int w, int h, int aspectW, int aspectH) {
		w = Math.max(1, w);
		h = Math.max(1, h);
		aspectW = Math.max(1, aspectW);
		aspectH = Math.max(1, aspectH);
		int cw, ch;
		if ((long) w * aspectH > (long) h * aspectW) {
			// Bild ist breiter als gewünscht: volle Höhe, Seiten weg.
			ch = h;
			cw = clamp((int) Math.round((double) h * aspectW / aspectH), 1, w);
		} else {
			cw = w;
			ch = clamp((int) Math.round((double) w * aspectH / aspectW), 1, h);
		}
		return new int[]{(w - cw) / 2, (h - ch) / 2, cw, ch};
	}

	/**
	 * Zielgröße: auf höchstens {@code maxLong} an der langen Seite verkleinert, nie vergrößert.
	 *
	 * @return {Breite, Höhe}
	 */
	public static int[] outSize(int cw, int ch, int maxLong) {
		cw = Math.max(1, cw);
		ch = Math.max(1, ch);
		int longSide = Math.max(cw, ch);
		if (maxLong <= 0 || longSide <= maxLong) return new int[]{cw, ch};
		if (cw >= ch) return new int[]{maxLong, Math.max(1, (int) Math.round((double) ch * maxLong / cw))};
		return new int[]{Math.max(1, (int) Math.round((double) cw * maxLong / ch)), maxLong};
	}

	/**
	 * Ausschnitt {@code crop} ({x, y, Breite, Höhe}) aus {@code argb} ({@code w}×{@code h}, Zeilen von oben) auf
	 * {@code outW}×{@code outH} bringen: Flächenmittel über alle Quellpixel eines Zielpixels, Alpha immer deckend.
	 */
	public static int[] render(int[] argb, int w, int h, int[] crop, int outW, int outH) {
		if (argb == null || w <= 0 || h <= 0 || argb.length < (long) w * h) throw new IllegalArgumentException("Bild " + w + "×" + h);
		if (outW <= 0 || outH <= 0) throw new IllegalArgumentException("Ziel " + outW + "×" + outH);
		int cx = clamp(crop[0], 0, w - 1);
		int cy = clamp(crop[1], 0, h - 1);
		int cw = clamp(crop[2], 1, w - cx);
		int ch = clamp(crop[3], 1, h - cy);
		int[] out = new int[outW * outH];
		if (cw == outW && ch == outH) {
			for (int y = 0; y < outH; y++) {
				int row = (cy + y) * w + cx;
				for (int x = 0; x < outW; x++) out[y * outW + x] = argb[row + x] | 0xFF000000;
			}
			return out;
		}
		// Spaltengrenzen einmal ausrechnen (für jede Zeile gleich).
		int[] x0 = new int[outW];
		int[] x1 = new int[outW];
		for (int x = 0; x < outW; x++) {
			x0[x] = (int) ((long) x * cw / outW);
			x1[x] = Math.min(cw, Math.max(x0[x] + 1, (int) ((long) (x + 1) * cw / outW)));
		}
		for (int y = 0; y < outH; y++) {
			int y0 = (int) ((long) y * ch / outH);
			int y1 = Math.min(ch, Math.max(y0 + 1, (int) ((long) (y + 1) * ch / outH)));
			for (int x = 0; x < outW; x++) {
				long r = 0, g = 0, b = 0;
				for (int yy = y0; yy < y1; yy++) {
					int row = (cy + yy) * w + cx;
					for (int xx = x0[x]; xx < x1[x]; xx++) {
						int p = argb[row + xx];
						r += (p >> 16) & 0xFF;
						g += (p >> 8) & 0xFF;
						b += p & 0xFF;
					}
				}
				int n = (y1 - y0) * (x1[x] - x0[x]);
				out[y * outW + x] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
			}
		}
		return out;
	}

	/**
	 * Verkleinerungsfaktor beim Auslesen des Spielbilds für den Sucher: der größte aus 4, 3, 2, 1, der beide Seiten des
	 * Bilds teilt UND nach dem Ausschnitt auf {@code aspectW}:{@code aspectH} noch mindestens {@code needW}×{@code needH}
	 * Pixel übrig lässt (so groß ist der Sucher auf dem Bildschirm) – der Sucher wird also nie hochskaliert. Geht das mit
	 * keinem Faktor, dann 1 (volles Bild).
	 */
	public static int readbackFactor(int frameW, int frameH, int aspectW, int aspectH, int needW, int needH) {
		for (int f = 4; f >= 2; f--) {
			if (frameW <= 0 || frameH <= 0 || frameW % f != 0 || frameH % f != 0) continue;
			int[] crop = cropRect(frameW / f, frameH / f, aspectW, aspectH);
			if (crop[2] >= needW && crop[3] >= needH) return f;
		}
		return 1;
	}

	/** Längste Seite der Sucher-Textur (Pixel) – nur in sehr großen Fenstern kleiner als der Sucher selbst. */
	public static final int MAX_VIEW_SIDE = 2048;

	/**
	 * Größe der Sucher-Textur für einen Ausschnitt von {@code cropW}×{@code cropH}, der auf dem Bildschirm
	 * {@code needW}×{@code needH} Fensterpixel einnimmt: so groß wie der Sucher, aber nie größer als der Ausschnitt
	 * (es wird nie vergrößert) und höchstens {@link #MAX_VIEW_SIDE} lang.
	 *
	 * @return {Breite, Höhe}
	 */
	public static int[] viewSize(int cropW, int cropH, int needW, int needH) {
		cropW = Math.max(1, cropW);
		cropH = Math.max(1, cropH);
		int w = cropW, h = cropH;
		if (needW > 0 && needH > 0 && cropW >= needW && cropH >= needH) {
			w = needW;
			h = needH;
		}
		return outSize(w, h, MAX_VIEW_SIDE);
	}

	private static int clamp(int v, int min, int max) {
		return v < min ? min : (v > max ? max : v);
	}
}
