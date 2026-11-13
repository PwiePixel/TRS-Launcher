package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.ui.Affine;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.TextureRef;
import dev.theredstonee.trsclient.core.ui.Textures;

/**
 * Der dunkle Schleier über dem Spiel, solange das Handy draußen ist (MC-Reels): in der Mitte leicht (34 %), zu den
 * Ecken hin dunkler (66 %) – ein weicher, runder Verlauf wie in den freigegebenen Bildern.
 *
 * <p>Der Verlauf ist eine kleine Textur ({@value #TEX_W}×{@value #TEX_H}, einmal je Handy hochgeladen), die über das
 * ganze Fenster gestreckt wird; von Texel zu Texel ändert sich die Deckkraft um höchstens eine Stufe, es entstehen
 * also keine sichtbaren Bänder. Ohne Texturen (Tests, andere Versionen) gibt es stattdessen {@value #BANDS}
 * rechteckige Rahmen.
 */
final class PhoneVeil {
	static final int TEX_W = 240;
	static final int TEX_H = 135;
	static final String TEXTURE = "reels/veil";
	/** Farbe des Schleiers (ohne Deckkraft). */
	static final int RGB = 0x08080E;
	static final float ALPHA_CENTRE = 0.34f;
	static final float ALPHA_EDGE = 0.66f;
	/** Mitte des Verlaufs (Anteil der Fensterbreite/-höhe). */
	static final float CENTRE_X = 0.50f;
	static final float CENTRE_Y = 0.52f;
	/** Rahmen des Ersatz-Verlaufs. */
	static final int BANDS = 24;

	private TextureRef texture;
	private boolean tried;

	/** Deckkraft (0…1) an einer Stelle des Fensters ({@code fx}, {@code fy} = Anteil 0…1). */
	static float alphaAt(float fx, float fy) {
		// Ellipse im Seitenverhältnis der Fläche, die durch die fernste Ecke geht (wie der Verlauf im Entwurf).
		double sx = Math.max(CENTRE_X, 1 - CENTRE_X), sy = Math.max(CENTRE_Y, 1 - CENTRE_Y);
		double nx = Math.min(CENTRE_X, 1 - CENTRE_X), ny = Math.min(CENTRE_Y, 1 - CENTRE_Y);
		double grow = Math.sqrt((sx / nx) * (sx / nx) + (sy / ny) * (sy / ny));
		double dx = (fx - CENTRE_X) / (nx * grow), dy = (fy - CENTRE_Y) / (ny * grow);
		double t = Math.min(1.0, Math.sqrt(dx * dx + dy * dy));
		return (float) (ALPHA_CENTRE + (ALPHA_EDGE - ALPHA_CENTRE) * t);
	}

	/** Die Pixel der Verlaufs-Textur (ARGB). */
	static int[] pixels(int w, int h) {
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int a = Math.round(alphaAt((x + 0.5f) / w, (y + 0.5f) / h) * 255f);
				out[y * w + x] = a << 24 | RGB;
			}
		}
		return out;
	}

	/** Zeichnet den Schleier über das ganze Fenster ({@code w}×{@code h} Fensterpixel). */
	void draw(Canvas c, int w, int h) {
		if (w <= 0 || h <= 0) return;
		TextureRef t = texture(c);
		if (t != null) {
			Affine.image(c, t, 0, 0, w, h, 0, 0, TEX_W, TEX_H, 0xFFFFFFFF);
			return;
		}
		bands(c, w, h);
	}

	private TextureRef texture(Canvas c) {
		if (texture != null || tried || !c.images()) return texture;
		Textures.Store store = Textures.store();
		if (store == null) return null;
		tried = true;
		try {
			texture = store.upload(TEXTURE, TEX_W, TEX_H, pixels(TEX_W, TEX_H));
		} catch (RuntimeException e) {
			texture = null;
		}
		return texture;
	}

	/** Ersatz ohne Textur: Grundton plus Rahmen, die nach außen hin zusammen auf die Rand-Deckkraft kommen. */
	static void bands(Canvas c, int w, int h) {
		c.fill(0, 0, w, h, Math.round(ALPHA_CENTRE * 255f) << 24 | RGB);
		// Jeder Rahmen legt dieselbe dünne Schicht über alles außerhalb seines Rechtecks.
		double each = 1.0 - Math.pow((1.0 - ALPHA_EDGE) / (1.0 - ALPHA_CENTRE), 1.0 / BANDS);
		int argb = Math.max(1, (int) Math.round(each * 255.0)) << 24 | RGB;
		float cx = w * CENTRE_X, cy = h * CENTRE_Y;
		for (int i = 1; i <= BANDS; i++) {
			// Ausgespartes Rechteck: von fast nichts (i = 1) bis fast das ganze Fenster (i = BANDS).
			float g = i / (float) (BANDS + 1);
			frame(c, w, h, Math.round(cx * (1 - g)), Math.round(cy * (1 - g)), Math.round(cx + (w - cx) * g),
					Math.round(cy + (h - cy) * g), argb);
		}
	}

	/** Alles außerhalb von (x1, y1)–(x2, y2) füllen, ohne Überlappung. */
	private static void frame(Canvas c, int w, int h, int x1, int y1, int x2, int y2, int argb) {
		if (y1 > 0) c.fill(0, 0, w, y1, argb);
		if (y2 < h) c.fill(0, y2, w, h, argb);
		if (x1 > 0) c.fill(0, y1, x1, y2, argb);
		if (x2 < w) c.fill(x2, y1, w, y2, argb);
	}

	/** Textur freigeben (Handy weggesteckt). Render-Thread. */
	void release() {
		TextureRef t = texture;
		texture = null;
		tried = false;
		Textures.Store store = Textures.store();
		if (t != null && store != null) store.release(t);
	}
}
