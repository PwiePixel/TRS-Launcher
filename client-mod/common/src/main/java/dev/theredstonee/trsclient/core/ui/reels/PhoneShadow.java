package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.ui.Affine;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.TextureRef;
import dev.theredstonee.trsclient.core.ui.Textures;

/**
 * Der weiche Schatten unter dem Handy (MC-Reels), wie in den freigegebenen Bildern: ein Rechteck in Gehäusegröße,
 * {@value #ALPHA} schwarz, um ({@value #OFFSET_X}, {@value #OFFSET_Y}) Handy-Pixel nach rechts unten versetzt und mit
 * {@value #SIGMA} Handy-Pixeln weichgezeichnet. Er fällt auch auf die Arme.
 *
 * <p>Ein weichgezeichnetes Rechteck ist das Produkt zweier Kantenverläufe. Deshalb reicht eine kleine quadratische
 * Textur mit der Ecke ({@link #pixels}): sie wird viermal gedreht an die Ecken gesetzt, ihre letzte Spalte bzw. Zeile
 * (reiner Kantenverlauf) wird über die Kanten gestreckt, die Mitte ist ein Rechteck. Ein Texel ist ein Fensterpixel,
 * von Texel zu Texel ändert sich die Deckkraft um höchstens zwei Stufen – keine Bänder, runde Ecken.
 *
 * <p>Ohne Texturen (Tests, andere Versionen) gibt es stattdessen ineinander liegende, sehr durchsichtige Rechtecke
 * ({@link #bands}).
 */
final class PhoneShadow {
	static final String TEXTURE = "reels/shadow";
	static final float ALPHA = 0.55f;
	static final float OFFSET_X = 24f;
	static final float OFFSET_Y = 34f;
	/** Weichzeichner (Standardabweichung, Handy-Pixel). */
	static final float SIGMA = 30f;
	/** Der Verlauf einer Kante reicht so viele Standardabweichungen nach innen und nach außen. */
	static final float REACH = 2.5f;
	/** Größte Kantenlänge der Ecken-Textur (darüber wird sie gestreckt). */
	static final int MAX_TEXELS = 256;
	/** Ersatz ohne Textur: Schichten, Abstand von Schicht zu Schicht (Handy-Pixel) und wie viele innen liegen. */
	static final int BAND_LAYERS = 18;
	static final float BAND_STEP = 5f;
	static final int BANDS_INSIDE = 8;

	private TextureRef texture;
	private int texels;
	private boolean failed;

	/**
	 * Verlauf quer über eine weichgezeichnete Kante: {@code t} = 0 (außen, nichts) … 1 (innen, voller Schatten). Die
	 * Glockenkurve ist bei ±{@value #REACH} Standardabweichungen abgeschnitten und so gestreckt, dass sie genau von 0 bis
	 * 1 läuft.
	 */
	static float profile(float t) {
		if (t <= 0f) return 0f;
		if (t >= 1f) return 1f;
		double lo = cdf(-REACH), hi = cdf(REACH);
		return (float) ((cdf((2.0 * t - 1.0) * REACH) - lo) / (hi - lo));
	}

	/** Verteilungsfunktion der Normalverteilung (Näherung nach Abramowitz/Stegun 7.1.26, Fehler unter 1e-7). */
	private static double cdf(double z) {
		double x = Math.abs(z) / Math.sqrt(2.0);
		double t = 1.0 / (1.0 + 0.3275911 * x);
		double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
		double erf = 1.0 - poly * Math.exp(-x * x);
		return z >= 0 ? 0.5 * (1.0 + erf) : 0.5 * (1.0 - erf);
	}

	/**
	 * Die Ecken-Textur ({@code n}×{@code n}, ARGB, schwarz): Deckkraft = {@value #ALPHA} · Verlauf(x) · Verlauf(y); links
	 * oben ist außen. Letzte Spalte und letzte Zeile sind der reine Kantenverlauf.
	 */
	static int[] pixels(int n) {
		int[] out = new int[n * n];
		float[] edge = new float[n];
		for (int i = 0; i < n; i++) edge[i] = profile(n == 1 ? 1f : i / (float) (n - 1));
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				out[y * n + x] = Math.round(ALPHA * 255f * edge[x] * edge[y]) << 24;
			}
		}
		return out;
	}

	/** Breite des Kantenverlaufs in Fensterpixeln (halb außerhalb, halb innerhalb des Schatten-Rechtecks). */
	static int edge(PhoneLayout l) {
		return Math.max(2, 2 * Math.round(REACH * SIGMA * l.q));
	}

	/** Zeichnet den Schatten des Handys – nach den Armen, vor dem Gehäuse. */
	void draw(Canvas c, PhoneLayout l) {
		int s = edge(l);
		int half = s / 2;
		int x0 = l.phoneX + Math.round(OFFSET_X * l.q) - half;
		int y0 = l.phoneY + Math.round(OFFSET_Y * l.q) - half;
		int x1 = x0 + l.phoneW + s;
		int y1 = y0 + l.phoneH + s;
		int w = x1 - x0 - 2 * s, h = y1 - y0 - 2 * s;
		int n = Math.min(s, MAX_TEXELS);
		TextureRef t = w > 0 && h > 0 ? texture(c, n) : null;
		if (t == null) {
			bands(c, l);
			return;
		}
		float k = s / (float) n;
		// Ecken: dieselbe Textur, viermal um 90° weitergedreht (sie ist an der Diagonale spiegelgleich).
		blit(c, t, k, 0, 0, k, x0, y0, 0, 0, n, n);
		blit(c, t, 0, k, -k, 0, x1, y0, 0, 0, n, n);
		blit(c, t, -k, 0, 0, -k, x1, y1, 0, 0, n, n);
		blit(c, t, 0, -k, k, 0, x0, y1, 0, 0, n, n);
		// Kanten: oben/unten die letzte Spalte in die Breite gestreckt, links/rechts die letzte Zeile in die Höhe.
		blit(c, t, w, 0, 0, k, x0 + s, y0, n - 1, 0, 1, n);
		blit(c, t, -w, 0, 0, -k, x1 - s, y1, n - 1, 0, 1, n);
		blit(c, t, k, 0, 0, h, x0, y0 + s, 0, n - 1, n, 1);
		blit(c, t, -k, 0, 0, -h, x1, y1 - s, 0, n - 1, n, 1);
		c.fill(x0 + s, y0 + s, x1 - s, y1 - s, Math.round(ALPHA * 255f) << 24);
	}

	private static void blit(Canvas c, TextureRef t, float a, float b, float cc, float d, float e, float f, int u, int v, int tw,
			int th) {
		c.push();
		if (Affine.apply(c, a, b, cc, d, e, f)) c.image(t, u, v, tw, th, 0xFFFFFFFF);
		c.pop();
	}

	private TextureRef texture(Canvas c, int n) {
		if (texture != null && texels == n) return texture;
		if (failed || !c.images()) return null;
		Textures.Store store = Textures.store();
		if (store == null) return null;
		try {
			// Andere Größe (Handy gedreht, Fenster geändert): derselbe Name, der Speicher ersetzt die alte Textur.
			texture = store.upload(TEXTURE, n, n, pixels(n));
			texels = n;
		} catch (RuntimeException e) {
			texture = null;
		}
		if (texture == null) failed = true;
		return texture;
	}

	/**
	 * Ersatz ohne Textur: {@value #BAND_LAYERS} sehr durchsichtige Rechtecke, von außen nach innen kleiner –
	 * übereinander ergeben sie in der Mitte {@value #ALPHA} Deckkraft und nach außen einen gestuften Rand.
	 */
	static void bands(Canvas c, PhoneLayout l) {
		int x = l.phoneX + Math.round(OFFSET_X * l.q), y = l.phoneY + Math.round(OFFSET_Y * l.q);
		int w = l.phoneW, h = l.phoneH;
		double each = 1.0 - Math.pow(1.0 - ALPHA, 1.0 / BAND_LAYERS);
		int argb = Math.max(1, (int) Math.round(each * 255.0)) << 24;
		int step = l.px(BAND_STEP);
		for (int i = BAND_LAYERS - 1; i >= 0; i--) {
			int grow = (i - BANDS_INSIDE) * step;
			if (w + 2 * grow <= 0 || h + 2 * grow <= 0) continue;
			c.fill(x - grow, y - grow, x + w + grow, y + h + grow, argb);
		}
	}

	/** Textur freigeben (Handy weggesteckt). Render-Thread. */
	void release() {
		TextureRef t = texture;
		texture = null;
		texels = 0;
		failed = false;
		Textures.Store store = Textures.store();
		if (t != null && store != null) store.release(t);
	}
}
