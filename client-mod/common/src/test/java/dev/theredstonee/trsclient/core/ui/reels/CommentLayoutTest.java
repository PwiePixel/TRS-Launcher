package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.reels.ReelsComment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kommentare als farbige Stücke und ihr Umbruch (MC-Reels) – reine Textrechnung, 6 Schrift-Pixel je Zeichen. */
class CommentLayoutTest {
	private static final CommentLayout.Measure SIX = new CommentLayout.Measure() {
		@Override
		public int width(String text) {
			return text.codePointCount(0, text.length()) * 6;
		}
	};
	private static final String EDITED = "(bearbeitet)";

	private static ReelsComment comment(String author, String text, boolean edited) {
		ReelsComment c = new ReelsComment(author, text, 1000L);
		c.id = "20261113-120000-0001";
		if (edited) c.editedAt = Long.valueOf(2000L);
		return c;
	}

	private static String flat(List<CommentLayout.Piece> line) {
		StringBuilder sb = new StringBuilder();
		for (CommentLayout.Piece p : line) sb.append(p.text);
		return sb.toString();
	}

	/** Alle Stücke einer Art hintereinander, Zeilen durch ein Leerzeichen getrennt. */
	private static String ofKind(List<List<CommentLayout.Piece>> lines, int kind) {
		StringBuilder sb = new StringBuilder();
		for (List<CommentLayout.Piece> line : lines) {
			for (CommentLayout.Piece p : line) if (p.kind == kind) sb.append(p.text);
			sb.append(' ');
		}
		return sb.toString().replaceAll(" +", " ").trim();
	}

	@Test
	void wrapKeepsTheKindOfEveryPiece() {
		ReelsComment c = comment("Steve_Baut", "Stark! @PixelCST wo ist das? @Alex kommt auch gleich vorbei", true);
		List<CommentLayout.Piece> pieces = CommentLayout.pieces(c, "PixelCST", EDITED);
		assertEquals("Steve_Baut: ", pieces.get(0).text);
		assertEquals(CommentLayout.NAME, pieces.get(0).kind);
		for (int chars : new int[]{84, 29, 20, 12}) {
			List<List<CommentLayout.Piece>> lines = CommentLayout.wrap(pieces, SIX, chars * 6, 20);
			String tag = chars + " Zeichen: " + lines;
			for (List<CommentLayout.Piece> line : lines) {
				assertTrue(SIX.width(flat(line)) <= chars * 6, tag);
				assertFalse(flat(line).startsWith(" "), tag);
				assertFalse(flat(line).endsWith(" "), tag);
				for (int i = 1; i < line.size(); i++) {
					assertTrue(line.get(i).kind != line.get(i - 1).kind, "Nachbarn derselben Art sind ein Stück – " + tag);
				}
			}
			assertEquals("Steve_Baut:", ofKind(lines, CommentLayout.NAME), tag);
			assertEquals("@PixelCST", ofKind(lines, CommentLayout.MENTION_SELF), tag);
			assertEquals("@Alex", ofKind(lines, CommentLayout.MENTION), tag);
			assertEquals(EDITED, ofKind(lines, CommentLayout.EDITED), tag);
			assertEquals("Stark! wo ist das? kommt auch gleich vorbei", ofKind(lines, CommentLayout.TEXT), tag);
		}
		// Auf einer Zeile: fünf Stücke Text, dazwischen die Erwähnungen – und in einem Stück, was zusammengehört.
		List<List<CommentLayout.Piece>> one = CommentLayout.wrap(pieces, SIX, 2000, 3);
		assertEquals(1, one.size());
		assertEquals("Steve_Baut: Stark! @PixelCST wo ist das? @Alex kommt auch gleich vorbei " + EDITED, flat(one.get(0)));
		assertEquals(" wo ist das? ", one.get(0).get(3).text);
	}

	@Test
	void aWordWiderThanTheLineIsSplitWithoutBreakingAPair() {
		String pair = "😀";
		// 3 Zeichen je Zeile; das Paar ist das dritte bzw. vierte Zeichen des langen Worts.
		ReelsComment c = comment("", "ab" + pair + "cd" + pair + pair + "e x", false);
		List<List<CommentLayout.Piece>> lines = CommentLayout.wrap(CommentLayout.pieces(c, "Ich", EDITED), SIX, 18, 10);
		assertEquals(4, lines.size(), lines.toString());
		assertEquals("ab" + pair, flat(lines.get(0)));
		assertEquals("cd" + pair, flat(lines.get(1)));
		assertEquals(pair + "e", flat(lines.get(2)));
		assertEquals("x", flat(lines.get(3)));
		for (List<CommentLayout.Piece> line : lines) {
			String s = flat(line);
			assertFalse(Character.isLowSurrogate(s.charAt(0)), "Zeile beginnt mit einer halben Hälfte: " + lines);
			assertFalse(Character.isHighSurrogate(s.charAt(s.length() - 1)), "Zeile endet mit einer halben Hälfte: " + lines);
		}
		// Ein kurzes Wort wird nie geteilt: es rückt ganz in die nächste Zeile.
		lines = CommentLayout.wrap(CommentLayout.pieces(comment("", "aaaa bbbb cc", false), "Ich", EDITED), SIX, 42, 10);
		assertEquals("aaaa", flat(lines.get(0)));
		assertEquals("bbbb cc", flat(lines.get(1)));
		// Selbst wenn kein ganzes Zeichen in die Zeile passt, kommt eines je Zeile – nie eine Endlosschleife.
		lines = CommentLayout.wrap(CommentLayout.pieces(comment("", "abc", false), "Ich", EDITED), SIX, 3, 10);
		assertEquals(3, lines.size());
	}

	@Test
	void pastTheLineLimitTheLastLineEndsWithDots() {
		ReelsComment c = comment("Rotkaeppchen", "eins zwei drei vier fünf sechs sieben acht neun zehn elf zwölf", false);
		List<CommentLayout.Piece> pieces = CommentLayout.pieces(c, "Ich", EDITED);
		List<List<CommentLayout.Piece>> all = CommentLayout.wrap(pieces, SIX, 20 * 6, 50);
		assertTrue(all.size() > 3, all.toString());
		assertFalse(flat(all.get(all.size() - 1)).endsWith("…"), "alles passt: keine Punkte");
		List<List<CommentLayout.Piece>> cut = CommentLayout.wrap(pieces, SIX, 20 * 6, 3);
		assertEquals(3, cut.size());
		assertEquals(flat(all.get(0)), flat(cut.get(0)));
		assertEquals(flat(all.get(1)), flat(cut.get(1)));
		String last = flat(cut.get(2));
		assertTrue(last.endsWith("…"), last);
		assertFalse(last.endsWith(" …"), "kein Leerzeichen vor den Punkten: " + last);
		assertTrue(SIX.width(last) <= 20 * 6, last);
		assertTrue(flat(all.get(2)).startsWith(last.substring(0, last.length() - 1)), last);
		// Passt es genau in die erlaubten Zeilen, bleibt es ohne Punkte.
		List<List<CommentLayout.Piece>> exact = CommentLayout.wrap(pieces, SIX, 20 * 6, all.size());
		assertEquals(flat(all.get(all.size() - 1)), flat(exact.get(exact.size() - 1)));
		assertTrue(CommentLayout.wrap(pieces, SIX, 20 * 6, 0).isEmpty());
		assertTrue(CommentLayout.wrap(pieces, SIX, 0, 3).isEmpty());
	}

	@Test
	void editedLabelFlowsWithTheText() {
		// Auf derselben Zeile, solange es passt …
		List<List<CommentLayout.Piece>> lines = CommentLayout.wrap(CommentLayout.pieces(comment("Du", "Schön.", true), "Ich", EDITED), SIX,
				29 * 6, 7);
		assertEquals(1, lines.size());
		assertEquals("Du: Schön. " + EDITED, flat(lines.get(0)));
		CommentLayout.Piece last = lines.get(0).get(lines.get(0).size() - 1);
		assertEquals(CommentLayout.EDITED, last.kind);
		assertEquals(" " + EDITED, last.text);
		// … sonst als Ganzes auf der nächsten, ohne Leerzeichen davor.
		lines = CommentLayout.wrap(CommentLayout.pieces(comment("Du", "Schön ist es hier.", true), "Ich", EDITED), SIX, 24 * 6, 7);
		assertEquals(2, lines.size(), lines.toString());
		assertEquals("Du: Schön ist es hier.", flat(lines.get(0)));
		assertEquals(EDITED, flat(lines.get(1)));
		assertEquals(CommentLayout.EDITED, lines.get(1).get(0).kind);
		// Nie bearbeitet: kein Stück. Und ohne Namen beginnt der Kommentar mit dem Text.
		List<CommentLayout.Piece> plain = CommentLayout.pieces(comment("", "Schön.", false), "Ich", EDITED);
		assertEquals(1, plain.size());
		assertEquals(CommentLayout.TEXT, plain.get(0).kind);
	}

	@Test
	void ownMentionGetsItsOwnKind() {
		List<CommentLayout.Piece> pieces = CommentLayout.pieces(comment("Steve_Baut", "@pixelcst und @PixelCST_2 und mail@pixelcst", false),
				"PixelCST", EDITED);
		assertEquals(5, pieces.size(), pieces.toString());
		assertEquals(CommentLayout.NAME, pieces.get(0).kind);
		assertEquals("@pixelcst", pieces.get(1).text);
		assertEquals(CommentLayout.MENTION_SELF, pieces.get(1).kind, "ohne Groß-/Kleinschreibung");
		assertEquals(" und ", pieces.get(2).text);
		assertEquals("@PixelCST_2", pieces.get(3).text);
		assertEquals(CommentLayout.MENTION, pieces.get(3).kind, "nur der ganze Name ist der eigene");
		assertEquals(" und mail@pixelcst", pieces.get(4).text);
		assertEquals(CommentLayout.TEXT, pieces.get(4).kind, "eine Mail-Adresse ist keine Erwähnung");
		// Ohne eigenen Namen ist jede Erwähnung eine fremde.
		assertEquals(CommentLayout.MENTION, CommentLayout.pieces(comment("A", "@PixelCST", false), null, EDITED).get(1).kind);
	}
}
