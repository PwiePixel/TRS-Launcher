package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.TextureRef;

import java.util.List;

/**
 * Die Kamera auf dem Bildschirm des Handys (MC-Reels): der Sucher füllt den ganzen Bildschirm, darüber das
 * Drittel-Raster, oben „Zurück“ und der Hinweis, dass HUD und Chat nie auf dem Foto sind, unten die Wahl
 * „Foto“ / „Selfie“ und die Auslöser-Reihe: letztes Foto, Auslöser, Kamera wechseln. Maße aus dem freigegebenen Entwurf
 * ({@code selfieScreen()}), umgerechnet über {@link PhoneLayout#q}.
 *
 * <p><b>Quer</b> (im Entwurf nicht gezeichnet, abgeleitet): die Auslöser-Reihe steht als Spalte am rechten Rand, die
 * Wahl „Foto“ / „Selfie“ unten in der Mitte, die Hinweise oben links.
 *
 * <p>Wie überall auf dem Handy: nichts ragt über den Bildschirm, jeder Text hat eine Höchstbreite – was nicht passt,
 * fällt weg (zuerst die Hinweise, zuletzt die Beschriftung der Wahl; der Knopf „Kamera wechseln“ bleibt immer).
 */
final class CameraView {
	static final int GRID = 0x33FFFFFF;
	static final int CHIP = 0x99000000;
	static final int MODE_ON = FeedView.AMBER;
	static final int MODE_ON_TEXT = 0xFF17120A;
	static final int MODE_OFF_TEXT = 0xFFC4C4C4;
	static final int SHUTTER_RING = 0xFF17171E;
	/** Mitte des Auslösers, solange ein Foto aufgenommen oder gespeichert wird. */
	static final int SHUTTER_BUSY = FeedView.AMBER;
	static final int SQUARE = 0x99000000;
	static final int SQUARE_LINE = 0x66FFFFFF;
	static final int THUMB_EMPTY = 0xFF17171E;
	/** Bänder des seitlichen Verlaufs hinter der Auslöser-Spalte (quer). */
	static final int SIDE_BANDS = 96;
	static final float SIDE_ALPHA = 0.72f;

	private final PhoneUi ui;
	private Canvas c;
	private PhoneLayout l;
	private boolean land;
	private int sx, sy, sw, sh;

	CameraView(PhoneUi ui) {
		this.ui = ui;
	}

	private int p(float phoneLocal) {
		return Math.round(phoneLocal * l.q);
	}

	void draw(Canvas canvas, PhoneLayout layout) {
		c = canvas;
		l = layout;
		land = l.hold != PhoneHold.PORTRAIT;
		sx = l.screenX;
		sy = l.screenY;
		sw = l.screenW;
		sh = l.screenH;

		// Sucher: das Bild der Welt ohne Oberfläche, mittig auf den Bildschirm beschnitten.
		c.fill(sx, sy, sx + sw, sy + sh, FeedView.EMPTY);
		ui.spot("viewfinder", sx, sy, sw, sh);
		ui.lookArea(sx, sy, sw, sh);
		TextureRef finder = ui.viewfinder();
		if (finder != null && c.images()) FeedView.cover(c, finder, sx, sy, sw, sh, FeedView.WHITE);
		grid();
		float flash = ui.flash();
		if (flash > 0f) c.fill(sx, sy, sx + sw, sy + sh, Math.round(flash * 230f) << 24 | 0xFFFFFF);

		FeedView.gradient(c, sx, sy, sw, Math.min(sh, p(land ? 96 : 140)), 0.867f, 0.533f, 0.55f, 0f);
		if (land) {
			int gh = Math.min(sh, p(96));
			FeedView.gradient(c, sx, sy + sh - gh, sw, gh, 0f, 0.3f, 0.5f, 0.62f);
			sideGradient();
		} else {
			int gh = Math.min(sh, p(230));
			FeedView.gradient(c, sx, sy + sh - gh, sw, gh, 0f, 0.867f, 0.38f, 1f);
		}
		int statusW = FeedView.statusBar(c, ui, l, !land);
		int[] shutter = controls();
		top(statusW, shutter);
		modes(shutter);
	}

	/** Drittel-Raster: zwei senkrechte und zwei waagerechte dünne Linien. */
	private void grid() {
		int t = l.px(1);
		if (sw < 6 * t || sh < 6 * t) return;
		for (int i = 1; i <= 2; i++) {
			int x = sx + Math.round(sw * i / 3f) - t;
			c.fill(x, sy, x + t, sy + sh, GRID);
		}
		for (int i = 1; i <= 2; i++) {
			int y = sy + Math.round(sh * i / 3f) - t;
			// Die Kreuzungen nicht doppelt decken.
			int x0 = sx;
			for (int j = 1; j <= 2; j++) {
				int x = sx + Math.round(sw * j / 3f) - t;
				c.fill(x0, y, x, y + t, GRID);
				x0 = x + t;
			}
			c.fill(x0, y, sx + sw, y + t, GRID);
		}
	}

	/** Quer: der Bildschirm wird nach rechts hin dunkler, damit die Auslöser-Spalte auf jedem Bild zu sehen ist. */
	private void sideGradient() {
		int w = Math.min(sw, p(230));
		if (w <= 0) return;
		// Viele schmale Bänder (je Band höchstens zwei Stufen Deckkraft) – auf hellem Himmel sind sonst Streifen zu sehen.
		int bands = Math.min(SIDE_BANDS, w);
		int x0 = sx + sw - w;
		for (int i = 0; i < bands; i++) {
			int x1 = x0 + i * w / bands;
			int x2 = x0 + (i + 1) * w / bands;
			float t = (i + 0.5f) / bands;
			// Sanfter Anfang: links kaum etwas, zum Rand hin deutlich.
			int alpha = Math.max(0, Math.min(255, Math.round(t * t * (3f - 2f * t) * SIDE_ALPHA * 255f)));
			if (alpha > 0 && x2 > x1) c.fill(x1, sy, x2, sy + sh, alpha << 24);
		}
	}

	// --- Auslöser-Reihe: letztes Foto, Auslöser, Kamera wechseln ---

	/** Zeichnet die drei Knöpfe. Rückgabe: Rechteck des Auslösers {x, y, Größe} (für die Lage der übrigen Teile). */
	private int[] controls() {
		int b = l.px(4);
		// In winzigen Fenstern schrumpft die Reihe, bis sie auf den Bildschirm passt.
		while (b > 1 && (land ? 56 * b + 2 * p(22) > sh - p(40) : 56 * b + 2 * p(10) > sw)) b--;
		int big = 24 * b;
		int small = 16 * b;
		int cx, cy, thumbX, thumbY, flipX, flipY;
		if (land) {
			cx = sx + sw - Math.max(p(74), big / 2 + 1);
			cy = sy + sh / 2 + p(8);
			int gap = Math.max(1, Math.min(p(22), (sh - p(40) - big - 2 * small) / 2));
			thumbX = cx - small / 2;
			thumbY = cy - big / 2 - gap - small;
			flipX = cx - small / 2;
			flipY = cy + big / 2 + gap;
			if (thumbY < sy || flipY + small > sy + sh) {
				// Gar kein Platz für die Spalte: nur der Auslöser, mittig.
				thumbY = Integer.MIN_VALUE;
				flipY = Integer.MIN_VALUE;
				cy = sy + sh / 2;
			}
		} else {
			cx = sx + sw / 2;
			cy = sy + sh - Math.max(p(84), big / 2 + 1);
			int side = Math.max(small / 2 + 1, Math.min(p(79), (sw / 2 - big / 2) / 2 + small / 4));
			thumbX = sx + side - small / 2;
			thumbY = cy - small / 2;
			flipX = sx + sw - side - small / 2;
			flipY = cy - small / 2;
			if (thumbX + small > cx - big / 2 || thumbX < sx) {
				thumbY = Integer.MIN_VALUE;
				flipY = Integer.MIN_VALUE;
			}
		}
		int x = cx - big / 2, y = cy - big / 2;
		if (x < sx || y < sy || x + big > sx + sw || y + big > sy + sh) return new int[]{cx, cy, 0};

		if (thumbY != Integer.MIN_VALUE) thumb(thumbX, thumbY, small, Math.max(1, b));
		shutter(x, y, b);
		if (flipY != Integer.MIN_VALUE) flip(flipX, flipY, small, Math.max(1, b));
		return new int[]{x, y, big};
	}

	/** Der gestufte Auslöser: weißer Ring, dunkler Ring, weiße Mitte (24 × 24 Einheiten von {@code b} Fensterpixeln). */
	private void shutter(int x, int y, int b) {
		int big = 24 * b;
		disc(c, x, y, big, new int[]{6 * b, 4 * b, 2 * b}, 2 * b, FeedView.WHITE);
		disc(c, x + 3 * b, y + 3 * b, 18 * b, new int[]{4 * b, 2 * b}, 2 * b, SHUTTER_RING);
		disc(c, x + 5 * b, y + 5 * b, 14 * b, new int[]{4 * b, 2 * b}, 2 * b, ui.busy() ? SHUTTER_BUSY : FeedView.WHITE);
		ui.tap("camShutter", x, y, big, big, new Runnable() {
			@Override
			public void run() {
				ui.shutter();
			}
		});
	}

	/**
	 * Gestufte Scheibe: je Stufe eine Zeile der Höhe {@code step}, oben und unten um {@code insets[i]} eingerückt;
	 * dazwischen die volle Breite. Die Rechtecke überlappen sich nicht.
	 */
	static void disc(Canvas c, int x, int y, int size, int[] insets, int step, int argb) {
		int n = insets.length;
		if (size <= 0 || step <= 0 || 2 * n * step >= size) {
			if (size > 0) c.fill(x, y, x + size, y + size, argb);
			return;
		}
		for (int i = 0; i < n; i++) c.fill(x + insets[i], y + i * step, x + size - insets[i], y + (i + 1) * step, argb);
		c.fill(x, y + n * step, x + size, y + size - n * step, argb);
		for (int i = 0; i < n; i++) {
			c.fill(x + insets[n - 1 - i], y + size - (n - i) * step, x + size - insets[n - 1 - i], y + size - (n - 1 - i) * step, argb);
		}
	}

	/** Das letzte Foto im weißen Rahmen; ein Klick geht zurück zum Feed. */
	private void thumb(int x, int y, int size, int border) {
		c.fill(x, y, x + size, y + border, FeedView.WHITE);
		c.fill(x, y + size - border, x + size, y + size, FeedView.WHITE);
		c.fill(x, y + border, x + border, y + size - border, FeedView.WHITE);
		c.fill(x + size - border, y + border, x + size, y + size - border, FeedView.WHITE);
		int in = size - 2 * border;
		if (in > 0) {
			c.fill(x + border, y + border, x + size - border, y + size - border, THUMB_EMPTY);
			TextureRef last = ui.lastPhoto();
			if (last != null && c.images()) FeedView.cover(c, last, x + border, y + border, in, in, FeedView.WHITE);
		}
		ui.tap("camThumb", x, y, size, size, new Runnable() {
			@Override
			public void run() {
				ui.home();
			}
		});
	}

	/** „Kamera wechseln“: schaltet zwischen Foto und Selfie um. */
	private void flip(int x, int y, int size, int border) {
		c.fill(x + border, y + border, x + size - border, y + size - border, SQUARE);
		c.fill(x, y, x + size, y + border, SQUARE_LINE);
		c.fill(x, y + size - border, x + size, y + size, SQUARE_LINE);
		c.fill(x, y + border, x + border, y + size - border, SQUARE_LINE);
		c.fill(x + size - border, y + border, x + size, y + size - border, SQUARE_LINE);
		int s = border;
		while (s > 1 && PhoneIcons.width(PhoneIcons.FLIP, s) > size - 2 * border) s--;
		int iw = PhoneIcons.width(PhoneIcons.FLIP, s), ih = PhoneIcons.height(PhoneIcons.FLIP, s);
		if (iw <= size && ih <= size) {
			PhoneIcons.draw(c, PhoneIcons.FLIP, x + (size - iw) / 2, y + (size - ih) / 2, s, FeedView.WHITE, false);
		}
		ui.tap("camFlip", x, y, size, size, new Runnable() {
			@Override
			public void run() {
				ui.toggleSelfie();
			}
		});
	}

	// --- Oben: „Zurück“ und die Hinweise ---

	private void top(int statusW, int[] shutter) {
		int m = PhoneText.scale(24, l.q);
		int padX = p(9), padY = Math.max(1, p(3));
		int left = sx + p(12);
		int top = sy + p(land ? 8 : 40);
		// Bis hierhin darf der obere Teil reichen (darunter stehen die Wahl bzw. der Auslöser).
		int limit = shutter[2] > 0 && !land ? shutter[1] : sy + sh;

		// „X Zurück“
		String back = I18n.tr("reels.camera.back");
		int xs = l.px(2);
		int xw = PhoneIcons.width(PhoneIcons.X, xs), xh = PhoneIcons.height(PhoneIcons.X, xs);
		int gap = Math.max(1, p(7));
		int rightLimit = land ? sx + sw - statusW : sx + sw - p(12);
		int room = rightLimit - left - 2 * padX - xw - gap;
		int k = PhoneText.fit(c, back, m, room);
		boolean label = room > 0 && PhoneText.width(c, back, k) <= room;
		int h = Math.max(xh, PhoneText.height(label ? k : m)) + 2 * padY;
		int w = 2 * padX + xw + (label ? gap + PhoneText.width(c, back, k) : 0);
		int backBottom = top;
		if (left + w <= rightLimit && top + h <= limit) {
			c.fill(left, top, left + w, top + h, CHIP);
			PhoneIcons.draw(c, PhoneIcons.X, left + padX, top + (h - xh) / 2, xs, FeedView.WHITE, true);
			if (label) PhoneText.draw(c, back, left + padX + xw + gap, PhoneText.centerY(top, h, k), k, FeedView.WHITE, true);
			int hit = Math.max(1, p(6));
			ui.tap("camBack", Math.max(sx, left - hit), Math.max(sy, top - hit), w + 2 * hit, h + 2 * hit, new Runnable() {
				@Override
				public void run() {
					ui.closeCamera();
				}
			});
			backBottom = top + h;
		}

		// Hinweise, mittig: „Auf dem Foto: kein HUD, kein Chat“ und darunter „Ziehen: umsehen“.
		int columnW = land ? Math.min(sw, p(150)) : 0;
		int cx = land ? sx + (sw - columnW) / 2 : sx + sw / 2;
		int y = land ? top : sy + p(84);
		if (!land && y < backBottom + padY) y = backBottom + padY;
		int max = land ? 2 * Math.min(cx - (left + w + gap), sx + sw - Math.max(columnW, statusW) - gap - cx) : sw - 2 * p(12);
		int noteH = chip(I18n.tr("reels.camera.note"), cx, y, PhoneText.scale(20, l.q), max, FeedView.WHITE, limit, "camNote");
		if (noteH > 0) y += noteH + Math.max(1, p(6));
		chip(I18n.tr("reels.camera.look"), cx, y, PhoneText.scale(19, l.q), max, FeedView.TEXT_HINT, limit, null);
	}

	/**
	 * Hinweis-Marke mittig um {@code cx}: nur ganz oder gar nicht. Passt der Text nicht in eine Zeile, steht er in zwei
	 * Zeilen; passt er auch so nicht, eine Schriftstufe kleiner. Rückgabe: ihre Höhe oder 0, wenn sie nicht passt.
	 */
	private int chip(String text, int cx, int y, int m, int maxWidth, int argb, int bottomLimit, String spot) {
		int padX = p(9), padY = Math.max(1, p(2));
		int room = maxWidth - 2 * padX;
		if (text == null || text.isEmpty() || room <= 0 || y < sy) return 0;
		for (int k = Math.max(1, m); k >= Math.max(1, m - 1); k--) {
			List<String> lines = PhoneText.wrap(c, text, k, room, 2);
			if (lines.isEmpty() || lines.get(lines.size() - 1).endsWith("…")) continue;
			int lineH = (PhoneText.GLYPH_H + 1) * k;
			int tw = 0;
			for (String line : lines) tw = Math.max(tw, PhoneText.width(c, line, k));
			int h = (lines.size() - 1) * lineH + PhoneText.height(k) + 2 * padY;
			int w = tw + 2 * padX;
			int x = cx - w / 2;
			if (tw > room || y + h > bottomLimit || x < sx || x + w > sx + sw) continue;
			c.fill(x, y, x + w, y + h, CHIP);
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				int ly = y + padY + i * lineH + Math.round((PhoneText.GLYPH_H - PhoneText.CAP_H) * k / 2f);
				PhoneText.draw(c, line, cx - PhoneText.width(c, line, k) / 2, ly, k, argb, true);
			}
			if (spot != null) ui.spot(spot, x, y, w, h);
			return h;
		}
		return 0;
	}

	// --- Wahl „Foto“ / „Selfie“ ---

	private void modes(int[] shutter) {
		String photo = I18n.tr("reels.camera.photo");
		String self = I18n.tr("reels.camera.selfie");
		int padX = p(12), padY = Math.max(1, p(3));
		int gap = Math.max(1, p(18));
		int columnW = land ? Math.min(sw, p(150)) : 0;
		int max = sw - columnW - 2 * Math.max(1, p(8));
		int m = PhoneText.scale(28, l.q);
		int total = width(photo, self, m, padX, gap);
		if (total > max && m > 1) {
			m--;
			total = width(photo, self, m, padX, gap);
		}
		if (total > max) return;
		int h = PhoneText.height(m) + 2 * padY;
		int bottom = land ? sy + sh - p(14) : (shutter[2] > 0 ? Math.min(sy + sh - p(158), shutter[1] - Math.max(1, p(10))) : sy + sh - p(158));
		int y = bottom - h;
		if (y < sy + p(40) || bottom > sy + sh) return;
		int x = (land ? sx + (sw - columnW) / 2 : sx + sw / 2) - total / 2;
		int w1 = PhoneText.width(c, photo, m) + 2 * padX;
		int w2 = PhoneText.width(c, self, m) + 2 * padX;
		mode(photo, x, y, w1, h, m, padX, !ui.selfie(), "camPhoto", false);
		mode(self, x + w1 + gap, y, w2, h, m, padX, ui.selfie(), "camSelfie", true);
	}

	private int width(String a, String b, int m, int padX, int gap) {
		return PhoneText.width(c, a, m) + PhoneText.width(c, b, m) + 4 * padX + gap;
	}

	private void mode(String text, int x, int y, int w, int h, int m, int padX, boolean on, String spot, final boolean selfie) {
		if (on) c.fill(x, y, x + w, y + h, MODE_ON);
		PhoneText.draw(c, text, x + padX, PhoneText.centerY(y, h, m), m, on ? MODE_ON_TEXT : MODE_OFF_TEXT, !on);
		ui.tap(spot, x, y, w, h, new Runnable() {
			@Override
			public void run() {
				ui.setSelfie(selfie);
			}
		});
	}
}
