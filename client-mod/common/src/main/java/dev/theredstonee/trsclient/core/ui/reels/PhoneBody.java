package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.ui.Canvas;

/**
 * Das Gehäuse des Handys (MC-Reels): ein klobiger Block mit gestuften Ecken und sichtbarer Dicke, nur aus Rechtecken –
 * wie in den freigegebenen Bildern. Alle Maße sind Handy-Pixel aus dem Entwurf, umgerechnet über
 * {@link PhoneLayout#q} in Fensterpixel.
 *
 * <p>Reihenfolge: weicher Schatten, zwei Tiefen-Schichten (nach rechts unten versetzt), Gehäuse mit hellem Rand oben
 * und links, Seitentasten, Lampe (nur hochkant), schwarzer Bildschirm. Der Inhalt des Bildschirms kommt danach.
 */
final class PhoneBody {
	static final int BODY = 0xFF2B2B38;
	static final int BODY_INNER = 0xFF17171E;
	static final int DEPTH_FAR = 0xFF07070A;
	static final int DEPTH_NEAR = 0xFF0D0D12;
	static final int HIGHLIGHT_TOP = 0xFF3D3D50;
	static final int HIGHLIGHT_LEFT = 0xFF353546;
	static final int BUTTON_RED = 0xFFE0281E;
	static final int BUTTON_RED_EDGE = 0xFF9D1C15;
	static final int BUTTON_GREY = 0xFF3D3D50;
	static final int LAMP_BAR = 0xFF0B0B0F;
	static final int LAMP_OFF = 0xFF2A2A36;
	static final int LAMP_ON = 0xFFFF5A4D;
	static final int LAMP_GLOW = 0x59FF5A4D;
	static final int SCREEN = 0xFF000000;

	/** Stufe der Ecken (Handy-Pixel); jede Ecke hat zwei Stufen. */
	static final float STEP = 8f;
	/** Der weiche Schatten: so viele Schichten, zusammen so deckend (in der Mitte). */
	static final int SHADOW_LAYERS = 18;
	static final float SHADOW_ALPHA = 0.55f;
	/** Abstand von Schicht zu Schicht (Handy-Pixel) und wie viele davon innerhalb des Schatten-Rechtecks liegen. */
	static final float SHADOW_STEP = 5f;
	static final int SHADOW_INSIDE = 8;

	private PhoneBody() {
	}

	/** Zeichnet Schatten und Gehäuse; danach ist der Bildschirm schwarz und wartet auf seinen Inhalt. */
	static void draw(Canvas c, PhoneLayout l) {
		int x = l.phoneX, y = l.phoneY, w = l.phoneW, h = l.phoneH;
		int s = l.px(STEP);
		boolean land = l.hold != PhoneHold.PORTRAIT;

		shadow(c, l, x + r(l, 24), y + r(l, 34), w, h);
		stepped(c, x + r(l, 14), y + r(l, 14), w, h, s, DEPTH_FAR);
		stepped(c, x + r(l, 7), y + r(l, 7), w, h, s, DEPTH_NEAR);

		// Seitentasten stehen über den Rand hinaus – vor dem Gehäuse zeichnen, damit die Kante sauber bleibt.
		int thick = l.px(8);
		int edge = Math.min(thick, l.px(3));
		if (land) {
			int b1 = x + r(l, 170), b2 = x + r(l, 270);
			c.fill(b1, y - thick, b1 + l.px(72), y, BUTTON_RED);
			c.fill(b1 + l.px(72) - edge, y - thick, b1 + l.px(72), y, BUTTON_RED_EDGE);
			c.fill(b2, y - thick, b2 + l.px(40), y, BUTTON_GREY);
		} else {
			int b1 = y + r(l, 170), b2 = y + r(l, 270);
			c.fill(x + w, b1, x + w + thick, b1 + l.px(72), BUTTON_RED);
			c.fill(x + w + thick - edge, b1, x + w + thick, b1 + l.px(72), BUTTON_RED_EDGE);
			c.fill(x + w, b2, x + w + thick, b2 + l.px(40), BUTTON_GREY);
		}

		stepped(c, x, y, w, h, s, BODY);
		stepped(c, x + s, y + s, w - 2 * s, h - 2 * s, s, BODY_INNER);
		c.fill(x + 2 * s, y, x + w - 2 * s, y + s, HIGHLIGHT_TOP);
		c.fill(x, y + 2 * s, x + s, y + h - 2 * s, HIGHLIGHT_LEFT);

		if (!land) {
			// Lampenleiste oben in der Mitte: links ein dunkler Punkt, rechts die rote Lampe.
			int lw = l.px(104), lh = l.px(16), dot = l.px(8);
			int lx = x + (w - lw) / 2, ly = y + r(l, 14);
			if (ly + lh <= l.screenY) {
				c.fill(lx, ly, lx + lw, ly + lh, LAMP_BAR);
				int dy = ly + (lh - dot) / 2;
				int on = lx + lw - 2 * dot;
				int off = on - 2 * dot;
				int glow = Math.max(1, l.px(2));
				c.fill(on - glow, dy - glow, on + dot + glow, dy + dot + glow, LAMP_GLOW);
				c.fill(off, dy, off + dot, dy + dot, LAMP_OFF);
				c.fill(on, dy, on + dot, dy + dot, LAMP_ON);
			}
		}

		c.fill(l.screenX, l.screenY, l.screenX + l.screenW, l.screenY + l.screenH, SCREEN);
	}

	private static int r(PhoneLayout l, float phoneLocal) {
		return Math.round(phoneLocal * l.q);
	}

	/**
	 * Rechteck mit zweifach gestuften Ecken (Stufe {@code step}). Fünf Rechtecke, die sich nicht überlappen – so stimmt
	 * die Form auch halb durchsichtig (beim Ein- und Ausblenden).
	 */
	static void stepped(Canvas c, int x, int y, int w, int h, int step, int argb) {
		if (w <= 0 || h <= 0) return;
		int s = Math.max(0, Math.min(step, Math.min(w, h) / 4));
		if (s == 0) {
			c.fill(x, y, x + w, y + h, argb);
			return;
		}
		c.fill(x + 2 * s, y, x + w - 2 * s, y + s, argb);
		c.fill(x + s, y + s, x + w - s, y + 2 * s, argb);
		c.fill(x, y + 2 * s, x + w, y + h - 2 * s, argb);
		c.fill(x + s, y + h - 2 * s, x + w - s, y + h - s, argb);
		c.fill(x + 2 * s, y + h - s, x + w - 2 * s, y + h, argb);
	}

	/**
	 * Weicher Schatten unter dem Handy: {@value #SHADOW_LAYERS} sehr durchsichtige Rechtecke, von außen nach innen
	 * kleiner – übereinander ergeben sie in der Mitte {@value #SHADOW_ALPHA} Deckkraft und nach außen einen weichen
	 * Rand ohne sichtbare Stufen.
	 */
	private static void shadow(Canvas c, PhoneLayout l, int x, int y, int w, int h) {
		double each = 1.0 - Math.pow(1.0 - SHADOW_ALPHA, 1.0 / SHADOW_LAYERS);
		int argb = Math.max(1, (int) Math.round(each * 255.0)) << 24;
		int step = l.px(SHADOW_STEP);
		for (int i = SHADOW_LAYERS - 1; i >= 0; i--) {
			int grow = (i - SHADOW_INSIDE) * step;
			if (w + 2 * grow <= 0 || h + 2 * grow <= 0) continue;
			c.fill(x - grow, y - grow, x + w + grow, y + h + grow, argb);
		}
	}
}
