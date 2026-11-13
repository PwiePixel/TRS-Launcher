package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.reels.ReelsComment;
import dev.theredstonee.trsclient.core.reels.ReelsPost;
import dev.theredstonee.trsclient.core.reels.ReelsTime;
import dev.theredstonee.trsclient.core.ui.Affine;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.TextureRef;

import java.util.List;
import java.util.Locale;

/**
 * Der Feed auf dem Bildschirm des Handys (MC-Reels): ein Foto je Seite, darüber Uhr, Streak, Titel und Seitenzahl,
 * rechts Herz und Kommentare, unten Name, Text und (hochkant) die Leiste Feed · Kamera · Drehen. Quer stehen Kamera
 * und Drehen mit in der rechten Spalte. Dazu das Kommentar-Blatt über dem unteren Teil.
 *
 * <p>Alle Maße sind Handy-Pixel aus dem freigegebenen Entwurf ({@code reels()} / {@code reelsLand()}), umgerechnet über
 * {@link PhoneLayout#q}. Gezeichnet wird nur innerhalb des Bildschirm-Rechtecks – ohne Scissor: Fotos werden über
 * ihren Texturausschnitt beschnitten, jeder Text hat eine Höchstbreite ({@link PhoneText}).
 *
 * <p><b>Foto und Haltung:</b> passt die Ausrichtung des Fotos zur Haltung, füllt es den Bildschirm (mittig
 * beschnitten). Passt sie nicht (Hochkant-Foto auf quer gehaltenem Handy oder umgekehrt), ist das ganze Foto zu sehen:
 * mittig eingepasst, dahinter dasselbe Foto abgedunkelt als Füllung.
 */
final class FeedView {
	static final int WHITE = 0xFFFFFFFF;
	static final int RED = 0xFFFF5A4D;
	static final int RED_BUTTON = 0xFFE0281E;
	static final int AMBER = 0xFFFFB84D;
	static final int AMBER_LIGHT = 0xFFFFD48A;
	static final int AMBER_LINE = 0x80FFB84D;
	static final int TEXT_SOFT = 0xFFD8D8E2;
	static final int TEXT_DIM = 0xFF8B8BA2;
	static final int TEXT_HINT = 0xFFC8C8D8;
	static final int ICON_DIM = 0xFF4A4A5E;
	static final int BAR = 0xFF0C0C11;
	static final int BAR_LINE = 0xFF23232E;
	static final int BAR_OFF = 0xFF9A9AB0;
	static final int FIELD = 0xFF17171E;
	static final int FIELD_LINE = 0xFF3A3A4A;
	static final int EMPTY = 0xFF14141B;
	static final int CHIP = 0x8C000000;
	static final int CHIP_STREAK = 0x99000000;
	/** Abdunkelung des Fotos, das hinter einem eingepassten Foto die Fläche füllt. */
	static final int DIMMED = 0xFF3A3A3A;
	static final int SHEET_DIM = 0x73000000;
	/** Bänder je Verlauf (oben/unten über dem Foto). */
	static final int GRADIENT_BANDS = 32;
	/** So viele Kommentare zeigt das Blatt höchstens (die neuesten). */
	static final int MAX_COMMENTS_SHOWN = 4;

	private final PhoneUi ui;
	private Canvas c;
	private PhoneLayout l;
	private boolean land;
	private int sx, sy, sw, sh;

	FeedView(PhoneUi ui) {
		this.ui = ui;
	}

	/** Handy-Pixel → Fensterpixel (darf 0 werden; für Abstände). */
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

		ReelsPost post = ui.current();
		int navH = land ? 0 : Math.min(sh / 3, p(66));
		if (post == null) {
			c.fill(sx, sy, sx + sw, sy + sh, EMPTY);
		} else {
			photo(post);
			gradient(sx, sy, sw, Math.min(sh, p(land ? 110 : 170)), 0.867f, 0.533f, 0.55f, 0f);
			int gh = Math.min(sh, p(land ? 190 : 360));
			gradient(sx, sy + sh - gh, sw, gh, 0f, 0.8f, 0.5f, 1f);
		}
		int statusW = status();
		int posW = post == null ? 0 : position();
		title(Math.max(posW, land ? statusW : 0));
		if (post == null) {
			empty(navH);
		} else {
			info(post, navH);
		}
		rail(post);
		if (!land) nav(navH);
		if (post != null && ui.view() == PhoneUi.View.COMMENTS) comments(post);
	}

	// --- Foto ---

	private void photo(ReelsPost post) {
		c.fill(sx, sy, sx + sw, sy + sh, EMPTY);
		ui.spot("photo", sx, sy, sw, sh);
		TextureRef t = ui.photo(post);
		if (t == null || !c.images()) return;
		// Die Ausrichtung zählt, wie das Foto wirklich ist (die Angabe im Post nur, wenn es quadratisch ist).
		boolean photoPortrait = t.height != t.width ? t.height > t.width : post.portrait;
		if (photoPortrait != land) {
			cover(c, t, sx, sy, sw, sh, WHITE);
		} else {
			cover(c, t, sx, sy, sw, sh, DIMMED);
			contain(c, t, sx, sy, sw, sh, WHITE);
		}
	}

	/** Füllt das Rechteck mit dem Bild (mittig beschnitten – über den Texturausschnitt, nicht über einen Scissor). */
	static void cover(Canvas c, TextureRef t, int x, int y, int w, int h, int argb) {
		if (t == null || w <= 0 || h <= 0) return;
		float s = Math.max(w / (float) t.width, h / (float) t.height);
		int cw = Math.max(1, Math.min(t.width, Math.round(w / s)));
		int ch = Math.max(1, Math.min(t.height, Math.round(h / s)));
		Affine.image(c, t, x, y, w, h, (t.width - cw) / 2f, (t.height - ch) / 2f, cw, ch, argb);
	}

	/** Zeigt das ganze Bild mittig im Rechteck (Ränder bleiben frei). */
	static void contain(Canvas c, TextureRef t, int x, int y, int w, int h, int argb) {
		if (t == null || w <= 0 || h <= 0) return;
		int[] r = containRect(t.width, t.height, x, y, w, h);
		Affine.image(c, t, r[0], r[1], r[2], r[3], 0, 0, t.width, t.height, argb);
	}

	/** Größtes Rechteck im Seitenverhältnis {@code tw}:{@code th}, mittig in (x, y, w, h): {x, y, Breite, Höhe}. */
	static int[] containRect(int tw, int th, int x, int y, int w, int h) {
		float s = Math.min(w / (float) tw, h / (float) th);
		int dw = Math.max(1, Math.min(w, Math.round(tw * s)));
		int dh = Math.max(1, Math.min(h, Math.round(th * s)));
		return new int[]{x + (w - dw) / 2, y + (h - dh) / 2, dw, dh};
	}

	/**
	 * Senkrechter Verlauf in Schwarz aus Bändern: Deckkraft {@code a0} oben, {@code aMid} an der Stelle {@code mid}
	 * (0…1), {@code a1} unten.
	 */
	private void gradient(int x, int y, int w, int h, float a0, float aMid, float mid, float a1) {
		if (w <= 0 || h <= 0) return;
		int bands = Math.min(GRADIENT_BANDS, h);
		for (int i = 0; i < bands; i++) {
			int y1 = y + i * h / bands;
			int y2 = y + (i + 1) * h / bands;
			float t = (i + 0.5f) / bands;
			float a = t < mid ? a0 + (aMid - a0) * (t / mid) : aMid + (a1 - aMid) * ((t - mid) / (1f - mid));
			int alpha = Math.max(0, Math.min(255, Math.round(a * 255f)));
			if (alpha > 0 && y2 > y1) c.fill(x, y1, x + w, y2, alpha << 24);
		}
	}

	// --- Kopfzeile: Uhr, Streak, Titel, Seitenzahl ---

	/** Uhr links (nur hochkant) und Streak rechts. Rückgabe: Breite des Streak-Blocks samt Rand. */
	private int status() {
		int m = PhoneText.scale(24, l.q);
		// In winzigen Fenstern ist die Zeile niedriger als die Schrift – dann zählt die Schrifthöhe.
		int h = Math.max(p(34), PhoneText.height(m));
		int pad = p(12);
		int ty = PhoneText.centerY(sy, h, m);
		String streak = String.valueOf(ui.streak());
		int fs = l.px(2);
		int fw = PhoneIcons.width(PhoneIcons.FLAME, fs);
		int fh = PhoneIcons.height(PhoneIcons.FLAME, fs);
		int gap = p(7);
		int tw = PhoneText.width(c, streak, m);
		int right = sx + sw - pad;
		int block = fw + gap + tw;
		if (block + 2 * pad > sw) return 0;
		PhoneIcons.draw(c, PhoneIcons.FLAME, right - block, sy + Math.max(0, (h - fh) / 2), fs, AMBER, true);
		PhoneText.draw(c, streak, right - tw, ty, m, AMBER_LIGHT, true);
		ui.spot("streak", right - block, sy, block, Math.max(h, fh));
		if (!land) {
			PhoneText.line(c, ui.clock(), sx + pad, ty, m, sw - 2 * pad - block - gap, WHITE, true);
		}
		return block + pad;
	}

	/** „2 / 7“ oben links. Rückgabe: Breite samt Rand. */
	private int position() {
		String text = I18n.tr("reels.pos", ui.page() + 1, ui.pages());
		int m = PhoneText.scale(22, l.q);
		int padX = p(8);
		int padY = Math.max(1, p(3));
		int left = sx + p(12);
		int top = sy + p(land ? 12 : 46);
		int max = sw / 3;
		int k = PhoneText.fit(c, text, m, max - 2 * padX);
		String t = PhoneText.clip(c, text, k, max - 2 * padX);
		if (t.isEmpty()) return 0;
		int w = PhoneText.width(c, t, k) + 2 * padX;
		int h = PhoneText.height(k) + 2 * padY;
		c.fill(left, top, left + w, top + h, CHIP);
		PhoneText.draw(c, t, left + padX, top + padY, k, WHITE, true);
		return p(12) + w;
	}

	/** „Reels“ mit rotem Strich, mittig. {@code sideW}: so viel Platz ist links bzw. rechts schon belegt. */
	private void title(int sideW) {
		int m = PhoneText.scale(32, l.q);
		int top = sy + p(land ? 8 : 40);
		int max = sw - 2 * (sideW + p(10));
		String text = I18n.tr("reels.tab");
		int w = PhoneText.centered(c, text, sx + sw / 2, top, m, max, WHITE, true);
		if (w <= 0) return;
		int lineY = top + PhoneText.height(m) + p(5);
		c.fill(sx + sw / 2 - w / 2, lineY, sx + sw / 2 - w / 2 + w, lineY + l.px(4), RED);
	}

	// --- Rechte Spalte: Herz, Kommentare (quer zusätzlich Kamera und Drehen) ---

	private void rail(final ReelsPost post) {
		int isz = l.px(6);
		int m = PhoneText.scale(24, l.q);
		int gapIcon = p(4);
		int gapItem = p(land ? 20 : 22);
		int iconW = PhoneIcons.width(PhoneIcons.HEART, isz);
		int iconH = PhoneIcons.height(PhoneIcons.HEART, isz);
		int countH = PhoneText.height(m);
		int itemH = iconH + gapIcon + countH;
		int camW = p(64), camH = Math.max(1, p(44));
		int flipS = l.px(4);
		int flipH = PhoneIcons.height(PhoneIcons.FLIP, flipS);
		int colW = land ? Math.max(iconW, camW) : iconW;

		int total = 0;
		if (post != null) total += 2 * itemH + gapItem;
		if (land) total += (post != null ? gapItem : 0) + camH + gapItem + flipH;
		if (total <= 0) return;
		int right = sx + sw - p(land ? 16 : 12);
		int cx = right - colW / 2;
		int y = land ? sy + (sh - total) / 2 : sy + sh - p(92) - total;
		// In sehr kleinen Fenstern nicht in die Kopfzeile rutschen.
		y = Math.max(y, sy + p(40));
		int pad = Math.max(1, p(8));

		if (post != null) {
			String likes = String.valueOf(Math.max(0, post.likes));
			PhoneIcons.draw(c, PhoneIcons.HEART, cx - iconW / 2, y, isz, post.likedByMe ? RED : WHITE, true);
			PhoneText.centered(c, likes, cx, y + iconH + gapIcon, m, colW + 2 * pad, WHITE, true);
			ui.tap("heart", cx - colW / 2 - pad, y - pad / 2, colW + 2 * pad, itemH + pad, new Runnable() {
				@Override
				public void run() {
					ui.like();
				}
			});
			y += itemH + gapItem;

			int n = post.comments == null ? 0 : post.comments.size();
			PhoneIcons.draw(c, PhoneIcons.TALK, cx - iconW / 2, y, isz, WHITE, true);
			PhoneText.centered(c, String.valueOf(n), cx, y + iconH + gapIcon, m, colW + 2 * pad, WHITE, true);
			ui.tap("comment", cx - colW / 2 - pad, y - pad / 2, colW + 2 * pad, itemH + pad, new Runnable() {
				@Override
				public void run() {
					ui.openComments();
				}
			});
			y += itemH + gapItem;
		}
		if (land) {
			cameraButton(cx - camW / 2, y, camW, camH);
			y += camH + gapItem;
			int flipW = PhoneIcons.width(PhoneIcons.FLIP, flipS);
			PhoneIcons.draw(c, PhoneIcons.FLIP, cx - flipW / 2, y, flipS, WHITE, true);
			ui.tap("rotate", cx - colW / 2 - pad, y - pad / 2, colW + 2 * pad, flipH + pad, new Runnable() {
				@Override
				public void run() {
					ui.rotate();
				}
			});
		}
	}

	/** Der rote Kamera-Knopf (heller Rand oben, dunkler unten, Kamera-Symbol). */
	private void cameraButton(int x, int y, int w, int h) {
		if (w <= 0 || h <= 0) return;
		c.fill(x, y, x + w, y + h, RED_BUTTON);
		int hi = Math.min(h / 3, l.px(3));
		int lo = Math.min(h / 3, l.px(4));
		if (hi > 0) c.fill(x, y, x + w, y + hi, 0x40FFFFFF);
		if (lo > 0) c.fill(x, y + h - lo, x + w, y + h, 0x4D000000);
		int isz = l.px(3);
		while (isz > 1 && (PhoneIcons.width(PhoneIcons.CAM, isz) > w - 2 || PhoneIcons.height(PhoneIcons.CAM, isz) > h - 2)) isz--;
		int iw = PhoneIcons.width(PhoneIcons.CAM, isz), ih = PhoneIcons.height(PhoneIcons.CAM, isz);
		PhoneIcons.draw(c, PhoneIcons.CAM, x + (w - iw) / 2, y + (h - ih) / 2, isz, WHITE, true);
		ui.tap("camera", x, y, w, h, new Runnable() {
			@Override
			public void run() {
				ui.openCamera();
			}
		});
	}

	// --- Unten links: Kopf, Name, Streak, Text, Zeit ---

	private void info(ReelsPost post, int navH) {
		int left = sx + p(land ? 16 : 14);
		int w = sw - p(land ? 16 : 14) - p(land ? 120 : 84);
		int bottom = sy + sh - (land ? p(14) : navH + p(16));
		if (w <= 0) return;

		int mName = PhoneText.scale(29, l.q);
		int mCap = PhoneText.scale(27, l.q);
		int mMeta = PhoneText.scale(21, l.q);
		int head = 8 * Math.max(1, Math.round(p(34) / 8f));
		int rowH = Math.max(head, PhoneText.height(mName));

		List<String> cap = PhoneText.wrap(c, post.caption, mCap, w, land ? 2 : 3);
		int capLine = Math.max(p(30), (PhoneText.GLYPH_H + 1) * mCap);
		int capGap = p(land ? 6 : 8);
		int capH = cap.isEmpty() ? 0 : capGap + (cap.size() - 1) * capLine + PhoneText.height(mCap);
		String meta = null;
		if (!land) {
			String[] ago = ReelsTime.ago(post.createdAt, ui.now());
			meta = I18n.tr(ago[0], ago[1]) + " · " + (post.selfie ? I18n.tr("reels.meta.selfie") : I18n.tr("reels.meta.phone"));
		}
		int metaGap = p(6);
		int metaH = meta == null ? 0 : metaGap + PhoneText.height(mMeta);

		int y = bottom - (rowH + capH + metaH);
		// Kopf und Name
		String author = post.author == null || post.author.isEmpty() ? "?" : post.author;
		boolean self = ui.isSelf(post.author);
		head(author, self, left, y + (rowH - head) / 2, head);
		int gap = p(10);
		int nameX = left + head + gap;
		int nameY = PhoneText.centerY(y, rowH, mName);
		int room = w - head - gap;
		int nameW = PhoneText.width(c, author, mName);
		int streak = self ? ui.streak() : 0;
		int chipW = streak > 0 ? streakChipWidth(streak) : 0;
		if (chipW > 0 && nameW + gap + chipW <= room) {
			PhoneText.draw(c, author, nameX, nameY, mName, WHITE, true);
			streakChip(streak, nameX + nameW + gap, y, rowH);
		} else {
			// Kein Platz: die Streak-Marke fällt weg, der Name wird notfalls kleiner bzw. gekürzt.
			PhoneText.line(c, author, nameX, nameY, mName, room, WHITE, true);
		}
		y += rowH;
		if (!cap.isEmpty()) {
			y += capGap;
			for (int i = 0; i < cap.size(); i++) {
				PhoneText.draw(c, cap.get(i), left, y + i * capLine, mCap, WHITE, true);
			}
			y += (cap.size() - 1) * capLine + PhoneText.height(mCap);
		}
		if (meta != null) {
			PhoneText.line(c, meta, left, y + metaGap, mMeta, w, TEXT_SOFT, true);
		}
	}

	/** Gesicht des Spielers aus seinem Skin (mit Hut-Schicht); für fremde Namen ein farbiges Feld mit Anfangsbuchstaben. */
	private void head(String author, boolean self, int x, int y, int size) {
		TextureRef skin = self ? ui.skin() : null;
		if (skin != null && c.images()) {
			Affine.image(c, skin, x, y, size, size, 8, 8, 8, 8, WHITE);
			Affine.image(c, skin, x, y, size, size, 40, 8, 8, 8, WHITE);
			return;
		}
		int base = 0xFF000000 | (author.hashCode() & 0x3F3F3F) | 0x303030;
		c.fill(x, y, x + size, y + size, base);
		int m = Math.max(1, size / 12);
		String initial = author.substring(0, 1).toUpperCase(Locale.ROOT);
		int tw = PhoneText.width(c, initial, m);
		if (tw <= size && PhoneText.height(m) <= size) {
			PhoneText.draw(c, initial, x + (size - tw) / 2, PhoneText.centerY(y, size, m), m, WHITE, true);
		}
	}

	private int streakChipWidth(int streak) {
		int m = PhoneText.scale(23, l.q);
		int fs = l.px(2);
		return p(7) + PhoneIcons.width(PhoneIcons.FLAME, fs) + p(5) + PhoneText.width(c, String.valueOf(streak), m) + p(9);
	}

	/** Die Streak-Marke hinter dem Namen (Flamme + Tage, mit Rahmen). */
	private void streakChip(int streak, int x, int rowY, int rowH) {
		int m = PhoneText.scale(23, l.q);
		int fs = l.px(2);
		int fw = PhoneIcons.width(PhoneIcons.FLAME, fs);
		int fh = PhoneIcons.height(PhoneIcons.FLAME, fs);
		String text = String.valueOf(streak);
		int w = streakChipWidth(streak);
		int h = Math.min(rowH, Math.max(fh, PhoneText.height(m)) + 2 * Math.max(1, p(3)));
		int y = rowY + (rowH - h) / 2;
		int line = Math.max(1, Math.min(h / 4, l.px(2)));
		c.fill(x, y, x + w, y + h, CHIP_STREAK);
		c.fill(x, y, x + w, y + line, AMBER_LINE);
		c.fill(x, y + h - line, x + w, y + h, AMBER_LINE);
		c.fill(x, y + line, x + line, y + h - line, AMBER_LINE);
		c.fill(x + w - line, y + line, x + w, y + h - line, AMBER_LINE);
		PhoneIcons.draw(c, PhoneIcons.FLAME, x + p(7), y + (h - fh) / 2, fs, AMBER, false);
		PhoneText.draw(c, text, x + p(7) + fw + p(5), PhoneText.centerY(y, h, m), m, AMBER_LIGHT, true);
		ui.spot("streakChip", x, y, w, h);
	}

	// --- Leerer Feed ---

	private void empty(int navH) {
		int side = p(land ? 130 : 24);
		int max = sw - 2 * side;
		if (max <= 0) return;
		int top = sy + p(land ? 56 : 96);
		int bottom = sy + sh - navH - p(12);
		int cx = sx + sw / 2;

		int isz = l.px(8);
		int iw = PhoneIcons.width(PhoneIcons.CAM, isz), ih = PhoneIcons.height(PhoneIcons.CAM, isz);
		int mBig = PhoneText.scale(36, l.q);
		int mHint = PhoneText.scale(24, l.q);
		int mKeys = PhoneText.scale(19, l.q);
		List<String> hint = PhoneText.wrap(c, I18n.tr("reels.empty.hint"), mHint, max, 2);
		List<String> keys = PhoneText.wrap(c, I18n.tr("reels.hint.keys", ui.rotateKeyLabel(), ui.openKeyLabel()), mKeys, max, 4);
		int hintLine = (PhoneText.GLYPH_H + 2) * mHint;
		int keysLine = (PhoneText.GLYPH_H + 2) * mKeys;
		int g1 = p(18), g2 = p(10), g3 = p(18);
		int textH = PhoneText.height(mBig) + g2 + hint.size() * hintLine + g3 + keys.size() * keysLine;
		boolean icon = ih + g1 + textH <= bottom - top;
		int total = (icon ? ih + g1 : 0) + textH;
		int y = top + Math.max(0, (bottom - top - total) / 2);

		if (icon) {
			PhoneIcons.draw(c, PhoneIcons.CAM, cx - iw / 2, y, isz, ICON_DIM, false);
			y += ih + g1;
		}
		PhoneText.centered(c, I18n.tr("reels.empty"), cx, y, mBig, max, WHITE, true);
		y += PhoneText.height(mBig) + g2;
		for (String line : hint) {
			if (y + PhoneText.height(mHint) > bottom) return;
			PhoneText.centered(c, line, cx, y, mHint, max, TEXT_HINT, true);
			y += hintLine;
		}
		y += g3;
		for (String line : keys) {
			if (y + PhoneText.height(mKeys) > bottom) return;
			PhoneText.centered(c, line, cx, y, mKeys, max, TEXT_DIM, false);
			y += keysLine;
		}
	}

	// --- Leiste unten (hochkant): Feed · Kamera · Drehen ---

	private void nav(int h) {
		if (h <= 0) return;
		int y0 = sy + sh - h;
		int line = Math.min(h / 4, l.px(3));
		c.fill(sx, y0, sx + sw, sy + sh, BAR);
		if (line > 0) c.fill(sx, y0, sx + sw, y0 + line, BAR_LINE);
		int top = y0 + line;
		int innerH = h - line;
		int third = sw / 3;

		navItem(PhoneIcons.HOME, I18n.tr("reels.nav.feed"), sx, top, third, innerH, WHITE);
		ui.tap("home", sx, y0, third, h, new Runnable() {
			@Override
			public void run() {
				ui.home();
			}
		});

		int bw = Math.min(third - 2, p(74));
		int bh = Math.min(innerH - 2, p(48));
		cameraButton(sx + sw / 2 - bw / 2, top + (innerH - bh) / 2, bw, bh);

		int rx = sx + sw - third;
		navItem(PhoneIcons.FLIP, I18n.tr("reels.nav.rotate"), rx, top, third, innerH, BAR_OFF);
		ui.tap("rotate", rx, y0, third, h, new Runnable() {
			@Override
			public void run() {
				ui.rotate();
			}
		});
	}

	/** Symbol mit Beschriftung darunter, mittig im Feld; die Beschriftung fällt weg, wenn sie nicht hineinpasst. */
	private void navItem(String[] icon, String label, int x, int top, int w, int h, int argb) {
		int isz = l.px(3);
		int iw = PhoneIcons.width(icon, isz), ih = PhoneIcons.height(icon, isz);
		int m = PhoneText.scale(19, l.q);
		int gap = Math.max(1, p(2));
		int max = w - 2 * Math.max(1, p(4));
		int k = PhoneText.fit(c, label, m, max);
		boolean text = ih + gap + PhoneText.height(k) <= h && PhoneText.width(c, label, k) <= max;
		int total = ih + (text ? gap + PhoneText.height(k) : 0);
		int y = top + Math.max(0, (h - total) / 2);
		int cx = x + w / 2;
		PhoneIcons.draw(c, icon, cx - iw / 2, y, isz, argb, false);
		if (text) PhoneText.draw(c, label, cx - PhoneText.width(c, label, k) / 2, y + ih + gap, k, argb, false);
	}

	// --- Kommentare ---

	private void comments(final ReelsPost post) {
		int sheetH = Math.max(1, Math.min(sh - p(40), p(land ? 290 : 400)));
		int y0 = sy + sh - sheetH;
		int line = Math.min(sheetH / 8, l.px(3));
		int pad = p(14);
		int left = sx + pad;
		int width = sw - 2 * pad;

		c.fill(sx, sy, sx + sw, y0, SHEET_DIM);
		ui.tap("commentsBack", sx, sy, sw, y0 - sy, new Runnable() {
			@Override
			public void run() {
				ui.closeComments();
			}
		});
		c.fill(sx, y0, sx + sw, sy + sh, BAR);
		if (line > 0) c.fill(sx, y0, sx + sw, y0 + line, BAR_LINE);
		// Klicks auf das Blatt selbst gehen nicht an das, was darunter liegt.
		ui.tap("commentSheet", sx, y0, sw, sheetH, null);
		if (width <= 0) return;

		// Kopf: „Kommentare · 3“ und das Kreuz zum Schließen.
		int mTitle = PhoneText.scale(24, l.q);
		int xs = l.px(3);
		int xw = PhoneIcons.width(PhoneIcons.X, xs), xh = PhoneIcons.height(PhoneIcons.X, xs);
		int headH = Math.max(PhoneText.height(mTitle), xh);
		int headY = y0 + line + pad;
		int n = post.comments == null ? 0 : post.comments.size();
		String title = n > 0 ? I18n.tr("reels.comments") + " · " + n : I18n.tr("reels.comments");
		PhoneText.line(c, title, left, PhoneText.centerY(headY, headH, mTitle), mTitle, width - xw - pad, WHITE, false);
		int closeX = sx + sw - pad - xw;
		PhoneIcons.draw(c, PhoneIcons.X, closeX, headY + (headH - xh) / 2, xs, BAR_OFF, false);
		int hit = Math.max(1, p(10));
		ui.tap("commentsClose", closeX - hit, headY - hit, xw + 2 * hit, headH + 2 * hit, new Runnable() {
			@Override
			public void run() {
				ui.closeComments();
			}
		});

		// Eingabefeld ganz unten.
		int mIn = PhoneText.scale(25, l.q);
		int inPadX = p(12), inPadY = Math.max(1, p(8));
		int fieldH = PhoneText.height(mIn) + 2 * inPadY;
		int fieldY = sy + sh - pad - fieldH;
		int border = Math.max(1, Math.min(inPadY, l.px(3)));
		c.fill(left, fieldY, left + width, fieldY + fieldH, FIELD);
		c.fill(left, fieldY, left + width, fieldY + border, FIELD_LINE);
		c.fill(left, fieldY + fieldH - border, left + width, fieldY + fieldH, FIELD_LINE);
		c.fill(left, fieldY + border, left + border, fieldY + fieldH - border, FIELD_LINE);
		c.fill(left + width - border, fieldY + border, left + width, fieldY + fieldH - border, FIELD_LINE);
		int caretW = Math.max(1, 2 * mIn);
		int textX = left + inPadX;
		int textY = PhoneText.centerY(fieldY, fieldH, mIn);
		int textMax = width - 2 * inPadX - caretW - mIn;
		String typed = ui.commentInput.text();
		int caretX = textX;
		if (typed.isEmpty()) {
			String hint = PhoneText.clip(c, I18n.tr("reels.comment.hint"), mIn, textMax);
			PhoneText.draw(c, hint, textX + caretW + mIn, textY, mIn, TEXT_DIM, false);
		} else {
			String shown = PhoneText.tail(c, typed, mIn, textMax);
			PhoneText.draw(c, shown, textX, textY, mIn, WHITE, false);
			caretX = textX + PhoneText.width(c, shown, mIn) + mIn;
		}
		if (ui.commentInput.focused() && ui.caretOn()) {
			c.fill(caretX, fieldY + inPadY, caretX + caretW, fieldY + fieldH - inPadY, RED);
		}
		ui.tap("commentInput", left, fieldY, width, fieldH, new Runnable() {
			@Override
			public void run() {
				ui.commentInput.setFocused(true);
			}
		});

		// Dazwischen die neuesten Kommentare, der neueste direkt über dem Feld.
		int listTop = headY + headH + p(10);
		int listBottom = fieldY - p(10);
		int mC = PhoneText.scale(21, l.q);
		int lineH = (PhoneText.GLYPH_H + 2) * mC;
		int gap = p(6);
		if (n == 0) {
			int ty = listTop + Math.max(0, (listBottom - listTop - PhoneText.height(mC)) / 2);
			if (listBottom - listTop >= PhoneText.height(mC)) {
				PhoneText.centered(c, I18n.tr("reels.comment.none"), sx + sw / 2, ty, mC, width, TEXT_DIM, false);
			}
			return;
		}
		int y = listBottom;
		int shown = 0;
		for (int i = n - 1; i >= 0 && shown < MAX_COMMENTS_SHOWN; i--) {
			ReelsComment comment = post.comments.get(i);
			if (comment == null || comment.text == null) continue;
			String author = comment.author == null ? "" : comment.author;
			String prefix = author.isEmpty() ? "" : author + ": ";
			List<String> lines = PhoneText.wrap(c, prefix + comment.text, mC, width, 2);
			if (lines.isEmpty()) continue;
			int blockH = (lines.size() - 1) * lineH + PhoneText.height(mC);
			if (y - blockH < listTop) break;
			y -= blockH;
			for (int j = 0; j < lines.size(); j++) {
				String text = lines.get(j);
				int ly = y + j * lineH;
				if (j == 0 && !prefix.isEmpty() && text.startsWith(prefix)) {
					// Name in Gelb, der Rest weiß – direkt dahinter (die Schrift hat feste Zeichenbreiten je Zeichen).
					PhoneText.draw(c, prefix, left, ly, mC, AMBER_LIGHT, false);
					PhoneText.draw(c, text.substring(prefix.length()), left + PhoneText.width(c, prefix, mC), ly, mC, WHITE, false);
				} else {
					PhoneText.draw(c, text, left, ly, mC, WHITE, false);
				}
			}
			y -= gap;
			shown++;
		}
		ui.commentsShown = shown;
	}
}
