package dev.theredstonee.trsclient.core.ui.reels;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der sichtbare Teil eines Eingabefelds mit beweglicher Schreibmarke ({@link PhoneText#window}); 6 px je Zeichen. */
class PhoneTextTest {
	private final PhoneUiTest.RecCanvas c = new PhoneUiTest.RecCanvas(1);

	@Test
	void windowKeepsTheCaretVisibleAndNeverSplitsAPair() {
		String pair = "😀";
		// 40 Zeichen, jedes zehnte ein Emoji-Paar; das Feld zeigt 10 Zeichen (m = 2: 12 px je Zeichen).
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 40; i++) sb.append(i % 10 == 9 ? pair : String.valueOf((char) ('a' + i % 26)));
		String s = sb.toString();
		int m = 2, max = 10 * 12 + 5;
		int start = 0;
		// Die Schreibmarke läuft vom Ende zum Anfang und wieder zurück – über jede Stelle, auch die mitten im Paar.
		int[] order = new int[2 * s.length() + 2];
		for (int i = 0; i <= s.length(); i++) {
			order[i] = s.length() - i;
			order[s.length() + 1 + i] = i;
		}
		for (int caret : order) {
			int[] w = PhoneText.window(c, s, caret, start, m, max);
			String tag = "Schreibmarke " + caret + ": " + w[0] + "…" + w[1];
			int at = PhoneText.whole(s, caret);
			assertTrue(w[0] <= at && at <= w[1], tag);
			String shown = s.substring(w[0], w[1]);
			assertTrue(PhoneText.width(c, shown, m) <= max, tag);
			assertFalse(Character.isLowSurrogate(shown.charAt(0)), tag + " beginnt mitten im Paar");
			assertFalse(Character.isHighSurrogate(shown.charAt(shown.length() - 1)), tag + " endet mitten im Paar");
			// So viel wie möglich: ein Zeichen mehr passte nicht.
			if (w[1] < s.length()) assertTrue(PhoneText.width(c, s.substring(w[0], PhoneText.after(s, w[1])), m) > max, tag);
			start = w[0];
		}
		// Am Ende getippt: das Feld zeigt den Schluss, wie bisher.
		int[] end = PhoneText.window(c, s, s.length(), 0, m, max);
		assertEquals(s.length(), end[1]);
		assertEquals(PhoneText.tail(c, s, m, max), s.substring(end[0], end[1]));
		// Die Schreibmarke läuft nach links aus dem Feld: das Feld beginnt bei ihr.
		int[] left = PhoneText.window(c, s, 5, end[0], m, max);
		assertEquals(5, left[0]);
		// Bleibt sie im Feld, bleibt der Anfang stehen.
		assertEquals(5, PhoneText.window(c, s, 8, 5, m, max)[0]);
		// Am Ende gelöscht: der Anfang rückt nach links, damit das Feld voll bleibt.
		String shorter = s.substring(0, 20);
		int[] back = PhoneText.window(c, shorter, shorter.length(), 15, m, max);
		assertEquals(shorter.length(), back[1]);
		assertEquals(PhoneText.tail(c, shorter, m, max), shorter.substring(back[0], back[1]));
	}

	@Test
	void wrapWordsKeepsAHyphenatedNameTogether() {
		String s = "Photos from friends arrive once MC-Reels goes online.";
		int max = 36 * 6;
		// Die gewöhnliche Umbruchregel trennt am Bindestrich …
		assertEquals("Photos from friends arrive once MC-", PhoneText.wrap(c, s, 1, max, 4).get(0));
		// … diese nicht.
		assertEquals(java.util.Arrays.asList("Photos from friends arrive once", "MC-Reels goes online."), PhoneText.wrapWords(c, s, 1, max, 4));
		// Letzte erlaubte Zeile: der Rest kommt dazu und wird gekürzt.
		java.util.List<String> two = PhoneText.wrapWords(c, s, 1, 12 * 6, 2);
		assertEquals(2, two.size());
		assertEquals("Photos from", two.get(0));
		assertTrue(two.get(1).endsWith("…") && PhoneText.width(c, two.get(1), 1) <= 12 * 6, two.get(1));
		// Ein Wort allein zu breit: dann doch am Bindestrich.
		assertEquals(PhoneText.wrap(c, "Ein MC-Reels-Foto", 1, 6 * 6, 3), PhoneText.wrapWords(c, "Ein MC-Reels-Foto", 1, 6 * 6, 3));
		assertTrue(PhoneText.wrapWords(c, "", 1, max, 2).isEmpty());
	}

	@Test
	void windowShowsTheWholeTextWhenItFits() {
		String s = "Hallo Welt";
		for (int caret = 0; caret <= s.length(); caret++) {
			assertArrayEquals(new int[]{0, s.length()}, PhoneText.window(c, s, caret, 7, 2, 10 * 12), "Schreibmarke " + caret);
		}
		assertArrayEquals(new int[]{0, 0}, PhoneText.window(c, "", 0, 3, 2, 100));
		assertArrayEquals(new int[]{0, 0}, PhoneText.window(c, null, 0, 0, 2, 100));
		// Kein Platz: nichts ist zu sehen, aber die Schreibmarke hat ihre Stelle.
		assertArrayEquals(new int[]{4, 4}, PhoneText.window(c, s, 4, 0, 2, 0));
		// Eine Schreibmarke außerhalb des Textes gilt als Anfang bzw. Ende.
		assertArrayEquals(new int[]{0, s.length()}, PhoneText.window(c, s, 99, 0, 2, 10 * 12));
		assertEquals(s.length(), PhoneText.window(c, s, 99, 0, 2, 5 * 12)[1]);
		assertEquals(0, PhoneText.window(c, s, -5, 3, 2, 5 * 12)[0]);
	}
}
