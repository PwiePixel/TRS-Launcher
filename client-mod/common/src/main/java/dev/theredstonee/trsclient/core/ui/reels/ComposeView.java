package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.TextureRef;

/**
 * „Neuer Post“ auf dem Bildschirm des Handys (MC-Reels): das gerade aufgenommene Foto, darunter das Textfeld, die
 * Knöpfe „Nochmal“ und „Posten“ und zwei Hinweiszeilen (was mit der Streak passiert; alles bleibt auf diesem PC). Nach
 * dem freigegebenen Entwurf {@code bemcScreen()} – ohne dessen Kopfzeile „BeMC“ und ohne das kleine zweite Foto, die
 * erst mit einem Server Sinn ergeben.
 *
 * <p><b>Quer</b> (im Entwurf nicht gezeichnet, abgeleitet): links das Foto über die ganze Höhe, rechts eine Spalte mit
 * Titel, Textfeld, Knöpfen und Hinweisen.
 *
 * <p>Nichts ragt über den Bildschirm: jeder Text hat eine Höchstbreite, und was unten nicht mehr passt (zuerst die
 * Hinweise), fällt weg.
 */
final class ComposeView {
	static final int BUTTON_GREY = 0xFF2A2A36;
	static final int HINT = 0xFFC9C9D6;
	/** Quer ist das Handy größer: Textfeld und Knöpfe bekommen eine kleinere Entwurfsgröße, damit mehr Text hineinpasst. */
	static final float FIELD_LAND = 20f;
	static final float BUTTON_LAND = 22f;

	private final PhoneUi ui;
	private Canvas c;
	private PhoneLayout l;
	private int sx, sy, sw, sh;

	ComposeView(PhoneUi ui) {
		this.ui = ui;
	}

	private int p(float phoneLocal) {
		return Math.round(phoneLocal * l.q);
	}

	void draw(Canvas canvas, PhoneLayout layout) {
		c = canvas;
		l = layout;
		sx = l.screenX;
		sy = l.screenY;
		sw = l.screenW;
		sh = l.screenH;
		if (l.hold != PhoneHold.PORTRAIT) landscape();
		else portrait();
	}

	// --- Hochkant: Kopf, Foto, Feld mit Knöpfen ---

	private void portrait() {
		int statusH = Math.max(p(34), PhoneText.height(PhoneText.scale(24, l.q)));
		FeedView.statusBar(c, ui, l, true);

		// Kopfleiste
		int mTitle = PhoneText.scale(36, l.q);
		int headH = Math.max(p(70), PhoneText.height(mTitle) + 2);
		int headY = sy + statusH;
		int line = Math.max(1, Math.min(headH / 6, l.px(3)));
		if (headY + headH > sy + sh) return;
		c.fill(sx, headY, sx + sw, headY + headH, FeedView.BAR);
		c.fill(sx, headY + headH - line, sx + sw, headY + headH, FeedView.BAR_LINE);
		title(sx + p(14), headY, sw - 2 * p(14), headH - line, mTitle, true);

		// Unterer Teil: so hoch wie im Entwurf, mindestens so hoch wie sein Inhalt.
		int pad = p(14);
		int avail = sy + sh - (headY + headH);
		int[] rows = rows(true);
		int need = line + pad + rows[0] + rows[1] + rows[2];
		if (need + Math.min(pad, p(14)) > avail) {
			// Zu eng: ohne die Hinweise.
			rows[2] = 0;
			need = line + pad + rows[0] + rows[1];
		}
		int panelH = Math.min(avail, Math.max(p(236), need + pad));
		int panelY = sy + sh - panelH;

		photo(sx, headY + headH, sw, panelY - (headY + headH));

		c.fill(sx, panelY, sx + sw, sy + sh, FeedView.BAR);
		if (panelH > line) c.fill(sx, panelY, sx + sw, panelY + line, FeedView.BAR_LINE);
		ui.tap("composePanel", sx, panelY, sw, panelH, null);
		content(sx + pad, panelY + line + pad, sw - 2 * pad, sy + sh, rows);
	}

	// --- Quer: Foto links, Spalte rechts ---

	private void landscape() {
		int panelW = Math.max(1, Math.min(sw * 45 / 100, p(336)));
		int panelX = sx + sw - panelW;
		int line = Math.max(1, Math.min(panelW / 8, l.px(3)));
		photo(sx, sy, sw - panelW, sh);
		c.fill(panelX, sy, sx + sw, sy + sh, FeedView.BAR);
		c.fill(panelX, sy, panelX + line, sy + sh, FeedView.BAR_LINE);
		ui.tap("composePanel", panelX, sy, panelW, sh, null);
		int statusW = FeedView.statusBar(c, ui, l, false);

		int pad = p(14);
		int left = panelX + line + pad;
		int width = panelW - line - 2 * pad;
		if (width <= 0) return;
		int mTitle = PhoneText.scale(30, l.q);
		int statusH = Math.max(p(34), PhoneText.height(PhoneText.scale(24, l.q)));
		// Der Titel steht neben der Streak, wenn Platz ist – sonst darunter.
		int titleH = Math.max(p(40), PhoneText.height(mTitle) + 2);
		int y = sy + Math.max(0, p(4));
		int titleW = width - (statusW > 0 ? statusW : 0);
		if (titleW < PhoneText.width(c, I18n.tr("reels.compose.title"), Math.max(1, mTitle - 1)) + p(40)) {
			y = sy + statusH;
			titleW = width;
		}
		if (y + titleH > sy + sh) return;
		title(left, y, titleW, titleH, mTitle, false);
		y += titleH + p(8);

		int[] rows = rows(false);
		if (y + rows[0] + rows[1] + rows[2] > sy + sh) rows[2] = 0;
		content(left, y, width, sy + sh, rows);
	}

	/** Kamera-Symbol und „Neuer Post“, rechts daneben (wenn Platz ist) „Foto“ bzw. „Selfie“. */
	private void title(int x, int y, int w, int h, int m, boolean mode) {
		if (w <= 0) return;
		String text = I18n.tr("reels.compose.title");
		int isz = l.px(3);
		while (isz > 1 && PhoneIcons.height(PhoneIcons.CAM, isz) > h - 2) isz--;
		int iw = PhoneIcons.width(PhoneIcons.CAM, isz), ih = PhoneIcons.height(PhoneIcons.CAM, isz);
		int gap = Math.max(1, p(10));
		boolean icon = iw + gap < w / 2 && ih <= h;
		int tx = x + (icon ? iw + gap : 0);
		if (icon) PhoneIcons.draw(c, PhoneIcons.CAM, x, y + (h - ih) / 2, isz, FeedView.AMBER, false);
		int drawn = PhoneText.line(c, text, tx, PhoneText.centerY(y, h, m), m, x + w - tx, FeedView.WHITE, false);
		if (!mode) return;
		Reels.Shot shot = ui.shot();
		String kind = shot != null && shot.selfie ? I18n.tr("reels.camera.selfie") : I18n.tr("reels.camera.photo");
		int mk = PhoneText.scale(22, l.q);
		int room = x + w - (tx + drawn) - gap;
		if (drawn > 0 && PhoneText.width(c, kind, mk) <= room) {
			PhoneText.draw(c, kind, x + w - PhoneText.width(c, kind, mk), PhoneText.centerY(y, h, mk), mk, FeedView.AMBER_LIGHT, false);
		}
	}

	/** Das aufgenommene Foto: füllt die Fläche, wenn seine Ausrichtung passt – sonst ganz, über sich selbst abgedunkelt. */
	private void photo(int x, int y, int w, int h) {
		if (w <= 0 || h <= 0) return;
		c.fill(x, y, x + w, y + h, FeedView.EMPTY);
		ui.spot("composePhoto", x, y, w, h);
		TextureRef t = ui.preview();
		if (t == null || !c.images()) return;
		boolean photoPortrait = t.height > t.width;
		boolean areaPortrait = h > w;
		if (photoPortrait == areaPortrait) {
			FeedView.cover(c, t, x, y, w, h, FeedView.WHITE);
		} else {
			FeedView.cover(c, t, x, y, w, h, FeedView.DIMMED);
			FeedView.contain(c, t, x, y, w, h, FeedView.WHITE);
		}
	}

	/** Höhen samt Abstand danach: {Textfeld, Knöpfe, Hinweise}. */
	private int[] rows(boolean portrait) {
		int mIn = PhoneText.scale(portrait ? 25 : FIELD_LAND, l.q);
		int mBtn = PhoneText.scale(portrait ? 28 : BUTTON_LAND, l.q);
		int mHint = PhoneText.scale(portrait ? 23 : 21, l.q);
		int gap = p(10);
		int field = PhoneText.height(mIn) + 2 * Math.max(1, p(8)) + gap;
		int buttons = Math.max(p(portrait ? 56 : 52), PhoneText.height(mBtn) + 2) + gap;
		int hints = 2 * hintLine(mHint);
		return new int[]{field, buttons, hints};
	}

	private int hintLine(int m) {
		return Math.max(p(26), PhoneText.height(m) + 1);
	}

	/** Textfeld, Knöpfe und Hinweise untereinander ab (x, y); nichts reicht über {@code bottom}. */
	private void content(int x, int y, int w, int bottom, int[] rows) {
		if (w <= 0) return;
		boolean portrait = l.hold == PhoneHold.PORTRAIT;
		int gap = p(10);

		// Textfeld
		int fieldH = rows[0] - gap;
		if (y + fieldH > bottom) return;
		field(x, y, w, fieldH);
		y += rows[0];

		// „Nochmal“ und „Posten“ (Posten ist breiter)
		int btnH = rows[1] - gap;
		if (y + btnH > bottom) return;
		int between = Math.max(1, p(10));
		int retryW = Math.max(1, Math.round((w - between) / 2.6f));
		int mBtn = PhoneText.scale(portrait ? 28 : BUTTON_LAND, l.q);
		button(x, y, retryW, btnH, BUTTON_GREY, 0x1FFFFFFF, 0x59000000, I18n.tr("reels.compose.retry"), mBtn, "composeRetry",
				new Runnable() {
					@Override
					public void run() {
						ui.retry();
					}
				});
		button(x + retryW + between, y, w - retryW - between, btnH, FeedView.RED_BUTTON, 0x40FFFFFF, 0x4D000000,
				I18n.tr("reels.compose.post"), mBtn, "composePost", new Runnable() {
					@Override
					public void run() {
						ui.postShot();
					}
				});
		y += rows[1];

		// Hinweise: die Streak (gelb) und „Bleibt vorerst auf diesem PC.“
		if (rows[2] <= 0) return;
		int mHint = PhoneText.scale(portrait ? 23 : 21, l.q);
		int lineH = hintLine(mHint);
		if (y + 2 * lineH > bottom) return;
		int[] streak = ui.streakChange();
		// „1 Tag“ hat eine eigene Fassung – die übrigen Zahlen nehmen die Mehrzahl.
		String first;
		if (streak[1] > streak[0]) {
			first = streak[1] == 1 ? I18n.tr("reels.compose.streak.one", streak[0], streak[1])
					: I18n.tr("reels.compose.streak", streak[0], streak[1]);
		} else {
			first = streak[1] == 1 ? I18n.tr("reels.compose.streak.same.one", streak[1]) : I18n.tr("reels.compose.streak.same", streak[1]);
		}
		int cx = x + w / 2;
		PhoneText.centered(c, first, cx, PhoneText.centerY(y, lineH, mHint), mHint, w, FeedView.AMBER_LIGHT, false);
		PhoneText.centered(c, I18n.tr("reels.compose.local"), cx, PhoneText.centerY(y + lineH, lineH, mHint), mHint, w, HINT, false);
	}

	/** Das Textfeld für den Text zum Foto (zeigt beim Tippen das Ende, mit blinkendem Cursor). */
	private void field(int x, int y, int w, int h) {
		int m = PhoneText.scale(l.hold == PhoneHold.PORTRAIT ? 25 : FIELD_LAND, l.q);
		int padX = p(12);
		int border = Math.max(1, Math.min(h / 4, l.px(3)));
		c.fill(x, y, x + w, y + h, FeedView.FIELD);
		c.fill(x, y, x + w, y + border, FeedView.FIELD_LINE);
		c.fill(x, y + h - border, x + w, y + h, FeedView.FIELD_LINE);
		c.fill(x, y + border, x + border, y + h - border, FeedView.FIELD_LINE);
		c.fill(x + w - border, y + border, x + w, y + h - border, FeedView.FIELD_LINE);
		int caretW = Math.max(1, 2 * m);
		int textX = x + Math.max(border + 1, padX);
		int textY = PhoneText.centerY(y, h, m);
		int textMax = w - 2 * Math.max(border + 1, padX) - caretW - m;
		String typed = ui.captionInput.text();
		int caretX = textX;
		if (typed.isEmpty()) {
			String hint = PhoneText.clip(c, I18n.tr("reels.compose.hint"), m, textMax);
			PhoneText.draw(c, hint, textX + caretW + m, textY, m, FeedView.TEXT_DIM, false);
		} else {
			String shown = PhoneText.tail(c, typed, m, textMax);
			PhoneText.draw(c, shown, textX, textY, m, FeedView.WHITE, false);
			caretX = textX + PhoneText.width(c, shown, m) + m;
		}
		int caretTop = y + Math.max(border, (h - PhoneText.height(m)) / 2);
		if (ui.captionInput.focused() && ui.caretOn() && caretX + caretW <= x + w - border) {
			c.fill(caretX, caretTop, caretX + caretW, y + h - (caretTop - y), FeedView.RED);
		}
		ui.tap("composeField", x, y, w, h, new Runnable() {
			@Override
			public void run() {
				ui.captionInput.setFocused(true);
			}
		});
	}

	/** Knopf mit hellem Rand oben und dunklem unten; die Beschriftung wird notfalls kleiner bzw. gekürzt. */
	private void button(int x, int y, int w, int h, int argb, int light, int dark, String label, int m, String spot, Runnable action) {
		if (w <= 0 || h <= 0) return;
		c.fill(x, y, x + w, y + h, argb);
		int hi = Math.min(h / 4, l.px(3));
		int lo = Math.min(h / 4, l.px(4));
		if (hi > 0) c.fill(x, y, x + w, y + hi, light);
		if (lo > 0) c.fill(x, y + h - lo, x + w, y + h, dark);
		PhoneText.centered(c, label, x + w / 2, PhoneText.centerY(y, h, m), m, w - 2 * Math.max(1, p(6)), FeedView.WHITE, false);
		ui.tap(spot, x, y, w, h, action);
	}
}
