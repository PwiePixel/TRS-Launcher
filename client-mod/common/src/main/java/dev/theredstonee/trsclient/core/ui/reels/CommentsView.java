package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.reels.CommentThread;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.reels.ReelsComment;
import dev.theredstonee.trsclient.core.reels.ReelsPost;
import dev.theredstonee.trsclient.core.ui.Canvas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Kommentar-Blatt über dem unteren Teil des Feeds (MC-Reels): Kopf („Kommentare · 3“ und das Kreuz), die Liste der
 * Kommentare und ganz unten das Eingabefeld.
 *
 * <p><b>Liste:</b> Kommentare der obersten Ebene vom ältesten zum neuesten, jeder gefolgt von seinen Antworten
 * ({@link CommentThread}) – die Antworten eingerückt, mit einem schmalen Balken links. Gezeichnet wird von unten nach
 * oben, ab der Stelle, bis zu der geblättert wurde; eine Zeile, die nicht ganz passt, fällt weg (ohne Scissor). Unter
 * jedem Kommentar steht blass „Antworten“ und bei eigenen „Bearbeiten“; ein bearbeiteter endet mit „(bearbeitet)“.
 * Erwähnungen ({@code @Name}) sind gelb, die des eigenen Namens zusätzlich hinterlegt. Der Kommentar, der gerade
 * bearbeitet oder beantwortet wird, hat links einen gelben Balken.
 *
 * <p><b>Über dem Feld</b> (jedes nur, wenn nötig, vom Feld aufwärts): die Leiste „Antwort an Name“ bzw. „Kommentar
 * bearbeiten“ mit einem Kreuz, die Leiste mit Namensvorschlägen, und blass der Hinweis, dass niemand benachrichtigt
 * wird. Reicht der Platz nicht (sehr kleine Fenster), geht zuerst der Hinweis, dann die Namen, dann die Leiste.
 *
 * <p><b>Feld:</b> zeigt den Teil des Textes um die Schreibmarke ({@link PhoneText#window}). Am Textende ist sie der
 * rote Block wie bisher, mitten im Text ein schmaler Strich zwischen zwei Zeichen.
 */
final class CommentsView {
	/** So viele Zeilen hat ein Kommentar höchstens (quer bzw. hochkant) – genug für 120 Zeichen samt Name. */
	static final int MAX_LINES_LAND = 3;
	static final int MAX_LINES_PORT = 7;
	/** Hinterlegung einer Erwähnung des eigenen Namens. */
	static final int MENTION_BACK = 0x40FFB84D;
	/** Zeilenabstand in Schrift-Pixeln (8 hoch + 2 Luft). */
	private static final int LINE = PhoneText.GLYPH_H + 2;

	private final PhoneUi ui;
	private Canvas c;
	private PhoneLayout l;
	private boolean land;
	private int sx, sy, sw, sh;
	private final CommentLayout.Measure measure = new CommentLayout.Measure() {
		@Override
		public int width(String text) {
			return c == null ? 0 : c.textWidth(text);
		}
	};
	/** Umbrochene Kommentare je Kommentar-ID – gültig für diesen Feed, dieses Layout, diese Sprache und diesen Namen. */
	private final Map<String, List<List<CommentLayout.Piece>>> wrapped = new HashMap<String, List<List<CommentLayout.Piece>>>();
	private Object wrappedFeed;
	private PhoneLayout wrappedLayout;
	private int wrappedLanguage;
	private String wrappedSelf;

	CommentsView(PhoneUi ui) {
		this.ui = ui;
	}

	private int p(float phoneLocal) {
		return Math.round(phoneLocal * l.q);
	}

	void draw(Canvas canvas, PhoneLayout layout, ReelsPost post) {
		c = canvas;
		l = layout;
		land = l.hold != PhoneHold.PORTRAIT;
		sx = l.screenX;
		sy = l.screenY;
		sw = l.screenW;
		sh = l.screenH;
		ui.commentsShown = 0;

		int sheetH = Math.max(1, Math.min(sh - p(40), p(land ? 290 : 400)));
		int y0 = sy + sh - sheetH;
		int line = Math.min(sheetH / 8, l.px(3));
		int pad = p(14);
		int left = sx + pad;
		int width = sw - 2 * pad;
		Runnable close = new Runnable() {
			@Override
			public void run() {
				ui.closeComments();
			}
		};

		c.fill(sx, sy, sx + sw, y0, FeedView.SHEET_DIM);
		ui.tap("commentsBack", sx, sy, sw, y0 - sy, close);
		c.fill(sx, y0, sx + sw, sy + sh, FeedView.BAR);
		if (line > 0) c.fill(sx, y0, sx + sw, y0 + line, FeedView.BAR_LINE);
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
		PhoneText.line(c, title, left, PhoneText.centerY(headY, headH, mTitle), mTitle, width - xw - pad, FeedView.WHITE, false);
		int closeX = sx + sw - pad - xw;
		PhoneIcons.draw(c, PhoneIcons.X, closeX, headY + (headH - xh) / 2, xs, FeedView.BAR_OFF, false);

		int fieldY = field(left, width, pad);

		// Über dem Feld, von unten nach oben: Leiste des Modus, Namensvorschläge, Hinweis.
		int listTop = headY + headH + p(10);
		int mC = PhoneText.scale(21, l.q);
		int lineH = LINE * mC;
		int rowOne = lineH + PhoneText.height(mC);
		int top = fieldY;
		if (ui.commentMode() != PhoneUi.CommentMode.NEW) {
			int barH = Math.max(p(land ? 32 : 42), PhoneText.height(mC) + 2);
			int y = top - p(8) - barH;
			if (y >= listTop) {
				banner(post, left, y, width, barH, mC);
				top = y;
			}
		}
		List<String> names = ui.suggestions();
		if (!names.isEmpty()) {
			int stripH = Math.max(p(land ? 34 : 44), PhoneText.height(mC) + 2);
			int y = top - p(6) - stripH;
			if (y >= listTop) {
				strip(names, left, y, width, stripH, mC);
				top = y;
			}
		}
		if (ui.commentNote()) {
			int mN = PhoneText.scale(19, l.q);
			List<String> lines = PhoneText.wrap(c, I18n.tr("reels.comment.local"), mN, width, land ? 1 : 2);
			int noteH = lines.isEmpty() ? 0 : (lines.size() - 1) * LINE * mN + PhoneText.height(mN);
			int y = top - p(6) - noteH;
			// Der Hinweis ist das Erste, was geht: er bleibt nur, wenn darüber noch ein Kommentar Platz hat.
			if (noteH > 0 && y - p(10) - rowOne >= listTop) {
				for (int i = 0; i < lines.size(); i++) {
					PhoneText.draw(c, lines.get(i), left, y + i * LINE * mN, mN, FeedView.TEXT_DIM, false);
				}
				ui.spot("commentNote", left, y, width, noteH);
				top = y;
			}
		}
		list(post, left, width, listTop, top - p(10), mC);

		// Das Kreuz zuletzt: wo sich Klickflächen berühren (sehr kleine Fenster), gewinnt es.
		int hit = Math.max(1, p(10));
		ui.tap("commentsClose", closeX - hit, headY - hit, xw + 2 * hit, headH + 2 * hit, close);
	}

	// --- Eingabefeld ---

	/** Das Eingabefeld ganz unten. Rückgabe: seine Oberkante. */
	private int field(int left, int width, int pad) {
		int mIn = PhoneText.scale(25, l.q);
		int inPadX = p(12), inPadY = Math.max(1, p(8));
		int fieldH = PhoneText.height(mIn) + 2 * inPadY;
		int fieldY = sy + sh - pad - fieldH;
		int border = Math.max(1, Math.min(inPadY, l.px(3)));
		c.fill(left, fieldY, left + width, fieldY + fieldH, FeedView.FIELD);
		c.fill(left, fieldY, left + width, fieldY + border, FeedView.FIELD_LINE);
		c.fill(left, fieldY + fieldH - border, left + width, fieldY + fieldH, FeedView.FIELD_LINE);
		c.fill(left, fieldY + border, left + border, fieldY + fieldH - border, FeedView.FIELD_LINE);
		c.fill(left + width - border, fieldY + border, left + width, fieldY + fieldH - border, FeedView.FIELD_LINE);
		int caretW = Math.max(1, 2 * mIn);
		int textX = left + inPadX;
		int textY = PhoneText.centerY(fieldY, fieldH, mIn);
		int textMax = width - 2 * inPadX - caretW - mIn;
		String typed = ui.commentInput.text();
		int caretX = textX;
		if (typed.isEmpty()) {
			ui.commentFieldStart = 0;
			boolean reply = ui.commentMode() == PhoneUi.CommentMode.REPLY;
			String hint = PhoneText.clip(c, I18n.tr(reply ? "reels.comment.hint.reply" : "reels.comment.hint"), mIn, textMax);
			PhoneText.draw(c, hint, textX + caretW + mIn, textY, mIn, FeedView.TEXT_DIM, false);
		} else {
			int caret = PhoneText.whole(typed, Math.max(0, Math.min(typed.length(), ui.commentInput.cursor())));
			int[] win = PhoneText.window(c, typed, caret, ui.commentFieldStart, mIn, textMax);
			ui.commentFieldStart = win[0];
			PhoneText.draw(c, typed.substring(win[0], win[1]), textX, textY, mIn, FeedView.WHITE, false);
			int before = PhoneText.width(c, typed.substring(win[0], Math.max(win[0], caret)), mIn);
			if (caret >= typed.length()) {
				// Am Ende: der Block hinter dem Text, wie bisher.
				caretX = textX + before + mIn;
			} else {
				// Mitten im Text: ein schmaler Strich in der Lücke zwischen zwei Zeichen – nichts verschiebt sich.
				caretW = Math.max(1, mIn);
				caretX = textX + before - caretW;
			}
		}
		if (ui.commentInput.focused()) {
			ui.spot("commentCaret", caretX, fieldY + inPadY, caretW, fieldH - 2 * inPadY);
			if (ui.caretOn()) c.fill(caretX, fieldY + inPadY, caretX + caretW, fieldY + fieldH - inPadY, FeedView.RED);
		}
		ui.tap("commentInput", left, fieldY, width, fieldH, new Runnable() {
			@Override
			public void run() {
				ui.focusComment();
			}
		});
		return fieldY;
	}

	// --- Über dem Feld ---

	/** Leiste „Antwort an Name“ bzw. „Kommentar bearbeiten“: gelber Balken links, Kreuz rechts (beendet den Modus). */
	private void banner(ReelsPost post, int x, int y, int w, int h, int m) {
		c.fill(x, y, x + w, y + h, FeedView.FIELD);
		int bar = Math.min(w, l.px(3));
		c.fill(x, y, x + bar, y + h, FeedView.AMBER);
		String text;
		if (ui.commentMode() == PhoneUi.CommentMode.EDIT) {
			text = I18n.tr("reels.comment.editing");
		} else {
			ReelsComment target = ui.commentTarget(post);
			String name = target == null || target.author == null || target.author.isEmpty() ? "?" : target.author;
			text = I18n.tr("reels.comment.replyTo", name);
		}
		// Das Kreuz in einem Quadrat so hoch wie die Leiste.
		boolean cross = h <= w / 2;
		int textX = x + bar + p(10);
		int room = x + w - (cross ? h : p(10)) - textX;
		PhoneText.line(c, text, textX, PhoneText.centerY(y, h, m), m, room, FeedView.TEXT_SOFT, false);
		ui.spot("commentBanner", x, y, w, h);
		if (!cross) return;
		int xs = l.px(2);
		while (xs > 1 && PhoneIcons.height(PhoneIcons.X, xs) > h - 2) xs--;
		int xw = PhoneIcons.width(PhoneIcons.X, xs), xh = PhoneIcons.height(PhoneIcons.X, xs);
		if (xh <= h && xw <= h) PhoneIcons.draw(c, PhoneIcons.X, x + w - h + (h - xw) / 2, y + (h - xh) / 2, xs, FeedView.BAR_OFF, false);
		ui.tap("commentCancel", x + w - h, y, h, h, new Runnable() {
			@Override
			public void run() {
				ui.cancelCommentMode();
			}
		});
	}

	/**
	 * Namensvorschläge „@Name“ in einer Zeile: so viele, wie hineinpassen. Die Zeile beginnt so weit hinten, dass der
	 * markierte Vorschlag ganz zu sehen ist; ist schon der erste breiter als die Zeile, wird er gekürzt.
	 */
	private void strip(List<String> names, int x, int y, int w, int h, int m) {
		int padX = p(10);
		int gap = p(6);
		int frame = Math.max(1, Math.min(h / 4, l.px(2)));
		int count = names.size();
		int[] widths = new int[count];
		for (int i = 0; i < count; i++) widths[i] = Math.min(w, PhoneText.width(c, "@" + names.get(i), m) + 2 * padX);
		int marked = Math.max(0, Math.min(count - 1, ui.suggestIndex()));
		int first = 0;
		while (first < marked) {
			int span = 0;
			for (int i = first; i <= marked; i++) span += widths[i] + (i > first ? gap : 0);
			if (span <= w) break;
			first++;
		}
		int cx = x;
		for (int i = first; i < count; i++) {
			int cw = widths[i];
			if (cx + cw > x + w) break;
			boolean on = i == marked;
			int edge = on ? FeedView.AMBER_LINE : FeedView.FIELD_LINE;
			c.fill(cx, y, cx + cw, y + h, FeedView.FIELD);
			c.fill(cx, y, cx + cw, y + frame, edge);
			c.fill(cx, y + h - frame, cx + cw, y + h, edge);
			c.fill(cx, y + frame, cx + frame, y + h - frame, edge);
			c.fill(cx + cw - frame, y + frame, cx + cw, y + h - frame, edge);
			String text = PhoneText.clip(c, "@" + names.get(i), m, cw - 2 * padX);
			PhoneText.draw(c, text, cx + padX, PhoneText.centerY(y, h, m), m, on ? FeedView.WHITE : FeedView.TEXT_SOFT, false);
			final int index = i;
			ui.tap("suggest" + i, cx, y, cw, h, new Runnable() {
				@Override
				public void run() {
					ui.completeSuggestion(index);
				}
			});
			cx += cw + gap;
		}
	}

	// --- Liste ---

	private void list(ReelsPost post, int left, int width, int listTop, int listBottom, int mC) {
		int lineH = LINE * mC;
		int textH = PhoneText.height(mC);
		int gap = p(6);
		List<CommentThread.Row> rows = ui.commentRows(post);
		int n = rows.size();
		if (n == 0) {
			ui.commentScrollLimit(1);
			int ty = listTop + Math.max(0, (listBottom - listTop - textH) / 2);
			if (listBottom - listTop >= textH) {
				PhoneText.centered(c, I18n.tr("reels.comment.none"), sx + sw / 2, ty, mC, width, FeedView.TEXT_DIM, false);
			}
			return;
		}
		fresh();
		int maxLines = land ? MAX_LINES_LAND : MAX_LINES_PORT;
		int skip = Math.max(0, Math.min(n - 1, ui.commentSkip()));
		String targetId = ui.commentMode() == PhoneUi.CommentMode.NEW ? null : ui.commentTargetId();
		int y = listBottom;
		int shown = 0;
		boolean oldest = false;
		for (int i = n - 1 - skip; i >= 0; i--) {
			CommentThread.Row row = rows.get(i);
			int indent = row.reply ? Math.min(p(18), width / 4) : 0;
			int w = width - indent;
			if (w < 6 * mC) break;
			List<List<CommentLayout.Piece>> lines = lines(row.comment, w / mC, maxLines, true);
			if (lines.isEmpty()) {
				if (i == 0) oldest = true;
				continue;
			}
			int rowH = lines.size() * lineH + textH;
			if (y - rowH < listTop) {
				if (shown > 0) break;
				// Der unterste Kommentar passt allein nicht ganz (viel steht über dem Feld): dann mit weniger Zeilen.
				int fit = (y - listTop - textH) / lineH;
				if (fit < 1) break;
				lines = lines(row.comment, w / mC, fit, false);
				if (lines.isEmpty()) break;
				rowH = lines.size() * lineH + textH;
			}
			y -= rowH;
			boolean target = targetId != null && targetId.equals(row.comment.id);
			if (row.reply) {
				c.fill(left, y, left + Math.min(indent, l.px(3)), y + rowH, target ? FeedView.AMBER : FeedView.BAR_LINE);
			} else if (target) {
				// Oberste Ebene: der Balken steht links neben dem Text, im Rand des Blatts.
				int bx = left - p(8);
				c.fill(bx, y, Math.min(left, bx + l.px(3)), y + rowH, FeedView.AMBER);
			}
			row(i, row.comment, lines, left + indent, y, w, rowH, mC, gap);
			if (target) ui.spot("commentTarget", left + indent, y, w, rowH);
			shown++;
			if (i == 0) oldest = true;
			y -= gap;
		}
		ui.commentsShown = shown;
		// Weiter nach oben blättern geht nur, solange der älteste Kommentar noch nicht zu sehen ist.
		ui.commentScrollLimit(oldest ? skip + 1 : n);
	}

	/** Der Zwischenspeicher der umbrochenen Kommentare gilt nur für denselben Feed, dasselbe Layout, dieselbe Sprache. */
	private void fresh() {
		Object feed = ui.feedIdentity();
		int language = I18n.generation();
		String self = ui.selfName();
		if (feed == wrappedFeed && l == wrappedLayout && language == wrappedLanguage && (self == null ? wrappedSelf == null : self.equals(wrappedSelf))) {
			return;
		}
		wrapped.clear();
		wrappedFeed = feed;
		wrappedLayout = l;
		wrappedLanguage = language;
		wrappedSelf = self;
	}

	private List<List<CommentLayout.Piece>> lines(ReelsComment comment, int fontPx, int maxLines, boolean keep) {
		String key = !keep || comment.id == null ? null : comment.id + "/" + fontPx + "/" + maxLines;
		List<List<CommentLayout.Piece>> lines = key == null ? null : wrapped.get(key);
		if (lines == null) {
			lines = CommentLayout.wrap(CommentLayout.pieces(comment, ui.selfName(), I18n.tr("reels.comment.edited")), measure, fontPx, maxLines);
			if (key != null) wrapped.put(key, lines);
		}
		return lines;
	}

	/**
	 * Ein Kommentar: seine Zeilen (Name gelb, Text weiß, Erwähnungen gelb, „(bearbeitet)“ blass), darunter die blasse
	 * Zeile „Antworten“ und – bei eigenen – „Bearbeiten“. Die Klickfläche eines Links reicht über die letzte Textzeile,
	 * die Link-Zeile und je einen halben Abstand darüber und darunter; die Flächen benachbarter Kommentare berühren
	 * sich deshalb nie.
	 */
	private void row(int index, final ReelsComment comment, List<List<CommentLayout.Piece>> lines, int x, int y, int w, int rowH, int mC,
			int gap) {
		int lineH = LINE * mC;
		for (int j = 0; j < lines.size(); j++) {
			int ly = y + j * lineH;
			int px = x;
			for (CommentLayout.Piece piece : lines.get(j)) {
				int pw = PhoneText.width(c, piece.text, mC);
				int argb = FeedView.WHITE;
				if (piece.kind == CommentLayout.NAME) {
					argb = FeedView.AMBER_LIGHT;
				} else if (piece.kind == CommentLayout.MENTION) {
					argb = FeedView.AMBER;
				} else if (piece.kind == CommentLayout.MENTION_SELF) {
					argb = FeedView.AMBER;
					c.fill(Math.max(x, px - mC), ly - mC, Math.min(x + w, px + pw), ly + PhoneText.height(mC), MENTION_BACK);
				} else if (piece.kind == CommentLayout.EDITED) {
					argb = FeedView.TEXT_DIM;
				}
				PhoneText.draw(c, piece.text, px, ly, mC, argb, false);
				px += pw;
			}
		}
		ui.spot("commentRow" + index, x, y, w, rowH);

		int linkY = y + lines.size() * lineH;
		String reply = PhoneText.clip(c, I18n.tr("reels.comment.reply"), mC, w);
		if (reply.isEmpty() || comment.id == null) return;
		int replyW = PhoneText.width(c, reply, mC);
		PhoneText.draw(c, reply, x, linkY, mC, FeedView.BAR_OFF, false);
		int hx = p(8);
		int hy1 = linkY - lineH - gap / 2;
		int hy2 = linkY + PhoneText.height(mC) + (gap - gap / 2);
		int x1 = Math.max(sx, x - hx);
		int x2 = Math.min(sx + sw, x + replyW + hx);
		ui.tap("commentReply" + index, x1, hy1, x2 - x1, hy2 - hy1, new Runnable() {
			@Override
			public void run() {
				ui.startReply(comment.id);
			}
		});
		if (!ui.isSelf(comment.author)) return;
		String edit = I18n.tr("reels.comment.edit");
		int editX = x + replyW + p(16);
		int editW = PhoneText.width(c, edit, mC);
		// Nur ganz: passt „Bearbeiten“ nicht mehr neben „Antworten“ (winzige Fenster), fällt es weg.
		if (editX + editW > x + w) return;
		PhoneText.draw(c, edit, editX, linkY, mC, FeedView.BAR_OFF, false);
		int x3 = Math.min(sx + sw, editX + editW + hx);
		ui.tap("commentEdit" + index, x2, hy1, x3 - x2, hy2 - hy1, new Runnable() {
			@Override
			public void run() {
				ui.startEdit(comment.id);
			}
		});
	}
}
