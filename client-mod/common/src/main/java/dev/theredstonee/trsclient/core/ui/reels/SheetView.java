package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.reels.FeedFilter;
import dev.theredstonee.trsclient.core.reels.FeedSort;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.reels.StreakGuard;
import dev.theredstonee.trsclient.core.ui.Canvas;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Blätter über dem Feed (MC-Reels), alle im Aussehen des Kommentar-Blatts – abgedunkeltes Foto darüber, dunkles
 * Blatt mit heller Oberkante, Titel links und Kreuz rechts:
 * <ul>
 *   <li><b>„Bild“</b>: „Bild speichern“ (in den Screenshots-Ordner), „Bild kopieren“ (Zwischenablage) und „Ordner
 *   öffnen“. Läuft eines davon gerade, sind alle drei grau.</li>
 *   <li><b>„Feed“</b>: zwei Reihen zum Umschalten – Anzeigen (Alle · Freunde · Ich) und Sortieren (Neu · Beliebt).</li>
 *   <li><b>„Streak“</b>: die Streak, ein Satz zum heutigen Stand, „Heute pausieren“ und (nur am Tag nach einem
 *   verpassten Tag) „Gestern retten“, was vom Streak-Schutz in diesem Monat noch übrig ist, und ein Satz zur Regel.</li>
 * </ul>
 *
 * <p>Jedes Blatt ist so hoch wie sein Inhalt und nie höher als der Bildschirm ohne die Kopfzeile. Reicht der Platz nicht
 * (sehr kleine Fenster), gehen zuerst die Hinweiszeilen, dann die unteren Knöpfe. Nichts ragt über den Bildschirm: jeder
 * Text hat eine Höchstbreite ({@link PhoneText}).
 */
final class SheetView {
	/** Zeilenabstand von Fließtext in Schrift-Pixeln (8 hoch + 2 Luft). */
	private static final int LINE = PhoneText.GLYPH_H + 2;

	private final PhoneUi ui;
	private Canvas c;
	private PhoneLayout l;
	private boolean land;
	private int sx, sy, sw, sh;
	/** Rand des Blatts und nutzbare Breite. */
	private int pad, width;

	SheetView(PhoneUi ui) {
		this.ui = ui;
	}

	private int p(float phoneLocal) {
		return Math.round(phoneLocal * l.q);
	}

	void draw(Canvas canvas, PhoneLayout layout) {
		PhoneUi.Sheet sheet = ui.sheet();
		if (sheet == PhoneUi.Sheet.NONE) return;
		c = canvas;
		l = layout;
		land = l.hold != PhoneHold.PORTRAIT;
		sx = l.screenX;
		sy = l.screenY;
		sw = l.screenW;
		sh = l.screenH;
		pad = p(14);
		width = sw - 2 * pad;
		if (sheet == PhoneUi.Sheet.SAVE) save();
		else if (sheet == PhoneUi.Sheet.FILTER) filter();
		else streak();
	}

	// --- Rahmen ---

	/** Höhe von Oberkante, Rand, Kopf und unterem Rand – was ein Blatt ohne Inhalt braucht. */
	private int frameH() {
		return l.px(3) + pad + headH() + p(12) + pad;
	}

	private int headH() {
		return Math.max(PhoneText.height(PhoneText.scale(24, l.q)), PhoneIcons.height(PhoneIcons.X, l.px(3)));
	}

	/** So hoch darf der Inhalt eines Blatts höchstens sein. */
	private int roomH() {
		return Math.max(0, sh - p(40) - frameH());
	}

	/**
	 * Abgedunkeltes Foto, Blatt, Titel und Kreuz. Klickflächen: {@code <name>Back} (über dem Blatt, schließt),
	 * {@code <name>Sheet} (schluckt Klicks auf das Blatt) und {@code <name>Close} (das Kreuz).
	 *
	 * @return y, ab dem der Inhalt steht, oder -1, wenn nicht einmal der Kopf passt
	 */
	private int frame(String name, String title, int contentH) {
		Runnable close = new Runnable() {
			@Override
			public void run() {
				ui.closeSheet();
			}
		};
		int sheetH = Math.max(1, Math.min(sh - p(40), frameH() + contentH));
		int line = Math.min(sheetH / 8, l.px(3));
		int y0 = sy + sh - sheetH;
		c.fill(sx, sy, sx + sw, y0, FeedView.SHEET_DIM);
		ui.tap(name + "Back", sx, sy, sw, y0 - sy, close);
		c.fill(sx, y0, sx + sw, sy + sh, FeedView.BAR);
		if (line > 0) c.fill(sx, y0, sx + sw, y0 + line, FeedView.BAR_LINE);
		// Klicks auf das Blatt selbst gehen nicht an das, was darunter liegt.
		ui.tap(name + "Sheet", sx, y0, sw, sheetH, null);
		if (width <= 0) return -1;

		int mTitle = PhoneText.scale(24, l.q);
		int xs = l.px(3);
		int xw = PhoneIcons.width(PhoneIcons.X, xs), xh = PhoneIcons.height(PhoneIcons.X, xs);
		int headH = headH();
		int headY = y0 + line + pad;
		if (headY + headH > sy + sh) return -1;
		PhoneText.line(c, title, sx + pad, PhoneText.centerY(headY, headH, mTitle), mTitle, width - xw - pad, FeedView.WHITE, false);
		int closeX = sx + sw - pad - xw;
		if (closeX >= sx) {
			PhoneIcons.draw(c, PhoneIcons.X, closeX, headY + (headH - xh) / 2, xs, FeedView.BAR_OFF, false);
			int hit = Math.max(1, p(10));
			ui.tap(name + "Close", closeX - hit, headY - hit, xw + 2 * hit, headH + 2 * hit, close);
		}
		return headY + headH + p(12);
	}

	/** Höhe von {@code lines} Zeilen Fließtext bei Vergrößerung {@code m} (0 Zeilen: 0). */
	private static int textH(int lines, int m) {
		return lines <= 0 ? 0 : (lines - 1) * LINE * m + PhoneText.height(m);
	}

	/** Fließtext linksbündig ab (x, y); Rückgabe: y darunter. */
	private int text(List<String> lines, int y, int m, int argb) {
		for (int i = 0; i < lines.size(); i++) PhoneText.draw(c, lines.get(i), sx + pad, y + i * LINE * m, m, argb, false);
		return y + textH(lines.size(), m);
	}

	/**
	 * Knopf im Aussehen von „Neuer Post“: heller Rand oben, dunkler unten; die Beschriftung wird notfalls kleiner bzw.
	 * gekürzt. {@code frame} ≠ 0: Rahmen in dieser Farbe (der Knopf fragt nach). {@code action} null: der Knopf ruht.
	 */
	private void button(String spot, int x, int y, int w, int h, boolean red, int frame, String label, int m, int text,
			Runnable action) {
		if (w <= 0 || h <= 0) return;
		c.fill(x, y, x + w, y + h, red ? FeedView.RED_BUTTON : ComposeView.BUTTON_GREY);
		int hi = Math.min(h / 4, l.px(3));
		int lo = Math.min(h / 4, l.px(4));
		if (hi > 0) c.fill(x, y, x + w, y + hi, red ? 0x40FFFFFF : 0x1FFFFFFF);
		if (lo > 0) c.fill(x, y + h - lo, x + w, y + h, red ? 0x4D000000 : 0x59000000);
		if (frame != 0) {
			int t = Math.max(1, Math.min(Math.min(w, h) / 6, l.px(3)));
			c.fill(x, y, x + w, y + t, frame);
			c.fill(x, y + h - t, x + w, y + h, frame);
			c.fill(x, y + t, x + t, y + h - t, frame);
			c.fill(x + w - t, y + t, x + w, y + h - t, frame);
		}
		PhoneText.centered(c, label, x + w / 2, PhoneText.centerY(y, h, m), m, w - 2 * Math.max(1, p(6)), text, false);
		ui.tap(spot, x, y, w, h, action);
	}

	// --- „Bild“: speichern, kopieren, Ordner öffnen ---

	private void save() {
		int m = PhoneText.scale(land ? 24 : 25, l.q);
		int bh = Math.max(p(land ? 52 : 50), PhoneText.height(m) + 2);
		int gap = Math.max(1, p(land ? 10 : 8));
		int mHint = PhoneText.scale(19, l.q);
		int hintGap = p(8);
		int room = roomH();
		// Zu eng: erst die Hinweiszeilen, dann (hochkant) die unteren Knöpfe.
		int buttons = 3;
		int hintLines = land ? 2 : 3;
		List<String> hint;
		int content;
		while (true) {
			hint = hintLines > 0 && width > 0 ? PhoneText.wrap(c, I18n.tr("reels.save.hint"), mHint, width, hintLines)
					: new ArrayList<String>();
			int rows = land ? 1 : buttons;
			content = rows * bh + (rows - 1) * gap + (hint.isEmpty() ? 0 : hintGap + textH(hint.size(), mHint));
			if (content <= room) break;
			if (hintLines > 0) hintLines--;
			else if (!land && buttons > 1) buttons--;
			else break;
		}
		int y = frame("save", I18n.tr("reels.save.title"), content);
		if (y < 0) return;

		boolean busy = ui.sheetBusy();
		String[] spots = {"saveDo", "saveCopy", "saveFolder"};
		String[] labels = {I18n.tr("reels.save.do"), I18n.tr("reels.save.copy"), I18n.tr("reels.save.folder")};
		Runnable[] actions = {new Runnable() {
			@Override
			public void run() {
				ui.savePicture();
			}
		}, new Runnable() {
			@Override
			public void run() {
				ui.copyPicture();
			}
		}, new Runnable() {
			@Override
			public void run() {
				ui.openPictureFolder();
			}
		}};
		int bw = land ? (width - 2 * gap) / 3 : width;
		for (int i = 0; i < buttons; i++) {
			int bx = sx + pad + (land ? i * (bw + gap) : 0);
			int by = y + (land ? 0 : i * (bh + gap));
			if (by + bh > sy + sh) break;
			// Läuft gerade etwas, ruhen alle drei (ein Doppelklick startet nichts zweimal).
			button(spots[i], bx, by, bw, bh, i == 0 && !busy, 0, labels[i], m, busy ? FeedView.TEXT_DIM : FeedView.WHITE,
					busy ? null : actions[i]);
		}
		y += (land ? bh : buttons * bh + (buttons - 1) * gap) + hintGap;
		if (!hint.isEmpty() && y + textH(hint.size(), mHint) <= sy + sh) text(hint, y, mHint, FeedView.TEXT_DIM);
	}

	// --- „Feed“: Filter und Reihenfolge ---

	private void filter() {
		int mLabel = PhoneText.scale(19, l.q);
		int m = PhoneText.scale(24, l.q);
		int segH = Math.max(p(land ? 44 : 48), PhoneText.height(m) + 2);
		int labelGap = p(5);
		int groupH = PhoneText.height(mLabel) + labelGap + segH;
		int between = p(12);
		int y = frame("filter", I18n.tr("reels.filter.title"), land ? groupH : 2 * groupH + between);
		if (y < 0) return;

		final FeedFilter[] filters = FeedFilter.values();
		String[] filterSpots = {"filterAll", "filterFriends", "filterMine"};
		String[] filterLabels = new String[filters.length];
		Runnable[] filterActions = new Runnable[filters.length];
		for (int i = 0; i < filters.length; i++) {
			final FeedFilter f = filters[i];
			filterLabels[i] = ui.filterLabel(f);
			filterActions[i] = new Runnable() {
				@Override
				public void run() {
					ui.setFilter(f);
				}
			};
		}
		final FeedSort[] sorts = FeedSort.values();
		String[] sortSpots = {"sortNewest", "sortPopular"};
		String[] sortLabels = new String[sorts.length];
		Runnable[] sortActions = new Runnable[sorts.length];
		for (int i = 0; i < sorts.length; i++) {
			final FeedSort s = sorts[i];
			sortLabels[i] = ui.sortLabel(s);
			sortActions[i] = new Runnable() {
				@Override
				public void run() {
					ui.setSort(s);
				}
			};
		}
		int left = sx + pad;
		if (land) {
			// Beide Gruppen in einer Reihe: Anzeigen links (drei Fünftel), Sortieren rechts.
			int gg = p(20);
			int wf = (width - gg) * 3 / 5;
			int ws = width - gg - wf;
			group(I18n.tr("reels.filter.show"), left, y, wf, mLabel, m, segH, filterSpots, filterLabels, filterActions,
					ui.filter().ordinal());
			group(I18n.tr("reels.filter.sort"), left + wf + gg, y, ws, mLabel, m, segH, sortSpots, sortLabels, sortActions,
					ui.sort().ordinal());
		} else {
			group(I18n.tr("reels.filter.show"), left, y, width, mLabel, m, segH, filterSpots, filterLabels, filterActions,
					ui.filter().ordinal());
			group(I18n.tr("reels.filter.sort"), left, y + groupH + between, width, mLabel, m, segH, sortSpots, sortLabels,
					sortActions, ui.sort().ordinal());
		}
	}

	/**
	 * Beschriftung und darunter eine Reihe Segmente. Die Breiten richten sich nach dem Text (jedes Segment bekommt
	 * denselben Zuschlag) – so passt „Arkadaşlar“ neben „Ja“ in voller Größe. Das gewählte Segment ist rot.
	 */
	private void group(String label, int x, int y, int w, int mLabel, int m, int segH, String[] spots, String[] labels,
			Runnable[] actions, int chosen) {
		int labelH = PhoneText.height(mLabel);
		if (w <= 0 || y + labelH + p(5) + segH > sy + sh) return;
		PhoneText.line(c, label, x, y, mLabel, w, FeedView.TEXT_DIM, false);
		int top = y + labelH + p(5);
		int n = labels.length;
		int gap = Math.max(1, p(5));
		int[] widths = new int[n];
		int natural = 0;
		for (int i = 0; i < n; i++) {
			widths[i] = PhoneText.width(c, labels[i], m) + 2 * p(8);
			natural += widths[i];
		}
		int extra = w - (n - 1) * gap - natural;
		if (extra >= 0) {
			for (int i = 0; i < n; i++) widths[i] += extra / n;
		} else {
			// Zu eng für die volle Schrift: gleich breite Segmente, die Texte werden kleiner bzw. gekürzt.
			for (int i = 0; i < n; i++) widths[i] = (w - (n - 1) * gap) / n;
		}
		int sum = 0;
		for (int i = 0; i < n - 1; i++) sum += widths[i] + gap;
		// Das letzte Segment nimmt den Rest (Rundung).
		widths[n - 1] = w - sum;
		int sxNow = x;
		for (int i = 0; i < n; i++) {
			button(spots[i], sxNow, top, widths[i], segH, i == chosen, 0, labels[i], m, FeedView.WHITE, actions[i]);
			sxNow += widths[i] + gap;
		}
	}

	// --- „Streak“: Streak-Schutz ---

	private void streak() {
		StreakGuard g = ui.streakGuard();
		int armed = ui.streakArmed();
		int m1 = PhoneText.scale(27, l.q);
		int m2 = PhoneText.scale(21, l.q);
		int m3 = PhoneText.scale(19, l.q);
		int m = PhoneText.scale(24, l.q);
		int bh = Math.max(p(land ? 44 : 48), PhoneText.height(m) + 2);
		int gap = Math.max(1, p(land ? 10 : 8));
		boolean rescue = g.rescueOffered;
		int buttonsH = land || !rescue ? bh : 2 * bh + gap;

		// Der Satz zum heutigen Stand – der erste, der zutrifft.
		String stateText;
		if (g.rescueOffered && g.leftForYesterday > 0) stateText = I18n.tr("reels.streak.state.broken", g.streak, g.streakIfRescued);
		else if (g.rescueOffered) stateText = I18n.tr("reels.streak.state.missed");
		else if (g.todayFrozen && !g.postedToday) stateText = I18n.tr("reels.streak.state.paused");
		else if (g.postedToday) stateText = I18n.tr("reels.streak.state.posted");
		else if (g.streak > 0) stateText = I18n.tr("reels.streak.state.open");
		else stateText = I18n.tr("reels.streak.state.none");
		// Was übrig ist – oder warum gerade nichts geht.
		String quotaText;
		if (g.readOnly) quotaText = I18n.tr("reels.streak.readonly");
		else if (g.clockBehind) quotaText = I18n.tr("reels.streak.clock");
		else quotaText = I18n.tr("reels.streak.left", g.leftThisMonth, StreakGuard.PER_MONTH);
		String first = g.streak == 1 ? I18n.tr("reels.compose.streak.same.one", g.streak) : I18n.tr("reels.compose.streak.same", g.streak);

		// Zu eng: erst die Regel, dann die Zeile zum Kontingent, dann wird der Satz zum Stand kürzer.
		int stateLines = land ? 2 : 3, quotaLines = land ? 2 : 3, ruleLines = land ? 2 : 3;
		int room = roomH();
		List<String> state, quota, rule;
		int content;
		while (true) {
			state = wrap(stateText, m2, stateLines);
			quota = wrap(quotaText, m3, quotaLines);
			rule = wrap(I18n.tr("reels.streak.rule"), m3, ruleLines);
			content = PhoneText.height(m1) + p(6) + textH(state.size(), m2) + p(10) + buttonsH
					+ (quota.isEmpty() ? 0 : p(8) + textH(quota.size(), m3)) + (rule.isEmpty() ? 0 : p(4) + textH(rule.size(), m3));
			if (content <= room) break;
			if (ruleLines > 0) ruleLines--;
			else if (quotaLines > 0) quotaLines--;
			else if (stateLines > 1) stateLines--;
			else break;
		}
		int y = frame("streak", I18n.tr("reels.streak.title"), content);
		if (y < 0) return;
		int bottom = sy + sh;
		int left = sx + pad;

		if (y + PhoneText.height(m1) > bottom) return;
		PhoneText.line(c, first, left, y, m1, width, FeedView.AMBER_LIGHT, false);
		y += PhoneText.height(m1) + p(6);
		if (y + textH(state.size(), m2) > bottom) return;
		y = text(state, y, m2, FeedView.TEXT_SOFT) + p(10);

		// „Heute pausieren“ steht immer da, „Gestern retten“ nur, wenn es etwas zu retten gibt.
		if (y + bh > bottom) return;
		int pauseW = land && rescue ? (width - gap) / 2 : width;
		protectButton("streakPause", left, y, pauseW, bh, m, g.canPauseToday, armed == 1, I18n.tr("reels.streak.pause"), false);
		if (rescue) {
			int rx = land ? left + pauseW + gap : left;
			int ry = land ? y : y + bh + gap;
			if (ry + bh <= bottom) {
				protectButton("streakRescue", rx, ry, land ? width - pauseW - gap : width, bh, m, g.canRescueYesterday, armed == 2,
						I18n.tr("reels.streak.rescue"), true);
			}
		}
		y += buttonsH;
		if (!quota.isEmpty()) {
			y += p(8);
			if (y + textH(quota.size(), m3) > bottom) return;
			y = text(quota, y, m3, FeedView.TEXT_DIM);
		}
		if (!rule.isEmpty()) {
			y += p(4);
			if (y + textH(rule.size(), m3) > bottom) return;
			text(rule, y, m3, FeedView.TEXT_DIM);
		}
	}

	private List<String> wrap(String s, int m, int maxLines) {
		if (maxLines <= 0 || width <= 0) return new ArrayList<String>();
		return PhoneText.wrap(c, s, m, width, maxLines);
	}

	/**
	 * „Heute pausieren“ / „Gestern retten“: rot, wenn möglich; grau mit blasser Schrift und ohne Wirkung, wenn nicht;
	 * nach dem ersten Druck „Sicher? Nochmal tippen“ mit gelbem Rahmen.
	 */
	private void protectButton(String spot, int x, int y, int w, int h, int m, boolean possible, boolean armed, String label,
			final boolean yesterday) {
		Runnable action = !possible ? null : new Runnable() {
			@Override
			public void run() {
				ui.useProtection(yesterday, false);
			}
		};
		boolean ask = possible && armed;
		button(spot, x, y, w, h, possible, ask ? FeedView.AMBER_LINE : 0, ask ? I18n.tr("reels.streak.confirm") : label, m,
				possible ? FeedView.WHITE : FeedView.TEXT_DIM, action);
	}
}
