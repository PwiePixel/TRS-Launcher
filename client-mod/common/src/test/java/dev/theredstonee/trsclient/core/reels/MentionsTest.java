package dev.theredstonee.trsclient.core.reels;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MC-Reels: @Erwähnungen in Kommentaren – finden, vorschlagen, vervollständigen. */
class MentionsTest {
	/** Die Erwähnungen eines Texts als Wörter. */
	private static List<String> found(String text) {
		List<String> out = new ArrayList<String>();
		for (int[] s : Mentions.spans(text)) out.add(text.substring(s[0], s[1]));
		return out;
	}

	private static List<Collection<String>> sources(Collection<String>... lists) {
		return Arrays.asList(lists);
	}

	@Test
	void findsMentionsAtStartAfterSpaceAndAfterPunctuation() {
		assertEquals(Arrays.asList("@PixelCST"), found("@PixelCST wo ist das?"));
		assertEquals(Arrays.asList("@Steve_Baut"), found("Stark! @Steve_Baut am Fluss"));
		assertEquals(Arrays.asList("@a", "@b_2", "@C"), found("(@a),@b_2!und:@C"));
		assertEquals(Arrays.asList("@x"), found("@x@y"), "ein @ direkt hinter einem Namen fängt keine neue Erwähnung an");
		assertEquals(Arrays.asList("@x", "@y"), found("@x @y"));
		assertEquals(Arrays.asList("@Ende"), found("am @Ende"));
		assertEquals(Arrays.asList("@1234567890123456"), found("@1234567890123456"), "16 Zeichen gehen noch");
		assertEquals(Arrays.asList("@Max"), found("@Max-Mustermann"));
		assertEquals(Arrays.asList("@Jos"), found("@José"), "nur A–Z, a–z, 0–9 und _");
		int[] span = Mentions.spans("Hi @Bea!").get(0);
		assertEquals(3, span[0]);
		assertEquals(7, span[1]);
		assertTrue(Mentions.spans(null).isEmpty());
		assertTrue(Mentions.spans("").isEmpty());
		assertTrue(Mentions.spans("@").isEmpty());
		assertTrue(Mentions.spans("@ Bea @@ @!").isEmpty());
	}

	@Test
	void mailAddressesAndSeventeenCharacterRunsAreNoMentions() {
		assertTrue(found("a@b").isEmpty());
		assertTrue(found("schreib an pixel@example.org").isEmpty());
		assertTrue(found("name_@Bea").isEmpty(), "auch nach _ und Ziffern nicht");
		assertTrue(found("9@Bea").isEmpty());
		assertTrue(found("@12345678901234567").isEmpty(), "17 Zeichen sind kein Spielername");
		assertTrue(found("@12345678901234567890 und mehr").isEmpty());
		assertEquals(Arrays.asList("@Bea"), found("@12345678901234567 @Bea"));
		assertFalse(Mentions.mentions("a@Bea", "Bea"));
	}

	@Test
	void mentionsIgnoresCaseAndNeedsTheWholeName() {
		assertTrue(Mentions.mentions("Stark! @pixelcst wo ist das?", "PixelCST"));
		assertTrue(Mentions.mentions("@PIXELCST", "PixelCST"));
		assertFalse(Mentions.mentions("@PixelCST2 schau", "PixelCST"));
		assertFalse(Mentions.mentions("@Pixel schau", "PixelCST"));
		assertFalse(Mentions.mentions("PixelCST ohne Zeichen", "PixelCST"));
		assertFalse(Mentions.mentions("@PixelCST", null));
		assertFalse(Mentions.mentions("@PixelCST", ""));
		assertFalse(Mentions.mentions(null, "PixelCST"));
		assertTrue(Mentions.mentions("@Bea und @PixelCST", "PixelCST"));
	}

	@Test
	void mentionsOtherIgnoresTheOwnName() {
		assertFalse(Mentions.mentionsOther("@PixelCST selbst", "pixelcst"));
		assertTrue(Mentions.mentionsOther("@PixelCST und @Bea", "PixelCST"));
		assertTrue(Mentions.mentionsOther("@Bea", "PixelCST"));
		assertFalse(Mentions.mentionsOther("niemand", "PixelCST"));
		assertFalse(Mentions.mentionsOther(null, "PixelCST"));
		assertTrue(Mentions.mentionsOther("@Bea", null));
	}

	@Test
	void tokenIsTheNamePartBeforeTheCaret() {
		assertEquals("Ro", Mentions.token("@Ro", 3));
		assertEquals("", Mentions.token("@", 1), "gleich nach dem @: alle Namen");
		assertEquals("St", Mentions.token("Hi @St", 6));
		assertEquals("St", Mentions.token("Hi @St und mehr", 6), "vor einem Leerzeichen");
		assertEquals("b", Mentions.token("(@a),@b", 7));
		assertEquals("", Mentions.token("Hi @ Bea", 4));
		assertEquals("1234567890123456", Mentions.token("@1234567890123456", 17));
		assertNull(Mentions.token("@12345678901234567", 18), "zu lang für einen Namen");
		assertNull(Mentions.token("Hi @St", 2), "vor dem @");
		assertNull(Mentions.token("Hi @St ", 7), "nach dem Leerzeichen ist der Name zu Ende");
		assertNull(Mentions.token("a@b", 3), "kein @ mitten im Wort");
		assertNull(Mentions.token("ohne", 4));
		assertNull(Mentions.token("", 0));
		assertNull(Mentions.token(null, 0));
		assertNull(Mentions.token("@Ro", 0));
		assertNull(Mentions.token("@Ro", 9), "Schreibmarke außerhalb");
		assertNull(Mentions.token("@Ro", -1));
	}

	@Test
	void noTokenInTheMiddleOfAName() {
		assertNull(Mentions.token("@Steve", 3), "die Schreibmarke steht mitten im Namen");
		assertNull(Mentions.token("Hi @Steve_Baut!", 8));
		assertEquals("Steve", Mentions.token("@Steve", 6));
		assertEquals("Steve_Baut", Mentions.token("Hi @Steve_Baut!", 14));
		assertNull(Mentions.token("@", 0));
	}

	@Test
	@SuppressWarnings("unchecked")
	void suggestKeepsSourceOrderDropsSelfAndDuplicatesAndCapsAtFive() {
		List<Collection<String>> sources = sources(
				Arrays.asList("Steve_Baut", "PixelCST", "bea"),
				Arrays.asList("Zoe", "alex", "STEVE_BAUT", "Carl"),
				null,
				Arrays.asList("Dora", "Emil", "Finn"));
		assertEquals(Arrays.asList("bea", "Steve_Baut", "alex", "Carl", "Zoe"), Mentions.suggest("", sources, "pixelcst", 5),
				"Quelle für Quelle, in jeder nach dem Alphabet; ohne den eigenen Namen und ohne Doppelte");
		assertEquals(Arrays.asList("bea", "Steve_Baut", "alex", "Carl", "Zoe", "Dora", "Emil", "Finn"),
				Mentions.suggest(null, sources, "PixelCST", 50));
		assertEquals(Arrays.asList("Steve_Baut"), Mentions.suggest("st", sources, "PixelCST", 5));
		assertEquals(Arrays.asList("Steve_Baut"), Mentions.suggest("STEVE_B", sources, "PixelCST", 5));
		assertEquals(Arrays.asList("PixelCST"), Mentions.suggest("pix", sources, null, 5), "ohne eigenen Namen bleibt er");
		assertTrue(Mentions.suggest("pix", sources, "PixelCST", 5).isEmpty());
		assertTrue(Mentions.suggest("x", sources, "PixelCST", 5).isEmpty());
		assertTrue(Mentions.suggest("", sources, "PixelCST", 0).isEmpty());
		assertTrue(Mentions.suggest("", null, "PixelCST", 5).isEmpty());
		assertEquals(Arrays.asList("bea"), Mentions.suggest("", sources, "PixelCST", 1));
	}

	@Test
	@SuppressWarnings("unchecked")
	void suggestDropsNamesThatAreNoPlayerNames() {
		List<Collection<String>> sources = sources(Arrays.asList("Max Mustermann", "", null, "§cRot", "Ok_Name", "12345678901234567",
				"José", "a-b", "Bea"));
		assertEquals(Arrays.asList("Bea", "Ok_Name"), Mentions.suggest("", sources, "PixelCST", 5));
		assertTrue(Mentions.validName("Ok_Name"));
		assertTrue(Mentions.validName("a"));
		assertTrue(Mentions.validName("1234567890123456"));
		assertFalse(Mentions.validName("12345678901234567"));
		assertFalse(Mentions.validName("Max Mustermann"));
		assertFalse(Mentions.validName(""));
		assertFalse(Mentions.validName(null));
		assertFalse(Mentions.validName("@Bea"));
	}

	@Test
	void completeReplacesTheTokenAndPutsTheCaretBehindIt() {
		Mentions.Edit e = Mentions.complete("@St", 3, "Steve_Baut", 120);
		assertEquals("@Steve_Baut ", e.text);
		assertEquals(12, e.caret);

		e = Mentions.complete("Hi @st wo bist du?", 6, "Steve_Baut", 120);
		assertEquals("Hi @Steve_Baut wo bist du?", e.text, "steht dahinter schon ein Leerzeichen, kommt kein zweites dazu");
		assertEquals(15, e.caret, "hinter dem Leerzeichen");

		e = Mentions.complete("Hi @st!", 6, "Steve_Baut", 120);
		assertEquals("Hi @Steve_Baut !", e.text, "der Rest bleibt, wie er ist");
		assertEquals(15, e.caret);

		e = Mentions.complete("@", 1, "Bea", 120);
		assertEquals("@Bea ", e.text);
		assertEquals(5, e.caret);

		// Wo kein Name getippt wird, ändert sich nichts.
		e = Mentions.complete("Hi @Steve", 6, "Steve_Baut", 120);
		assertEquals("Hi @Steve", e.text);
		assertEquals(6, e.caret);
		e = Mentions.complete("ohne", 4, "Bea", 120);
		assertEquals("ohne", e.text);
		assertEquals(4, e.caret);
		e = Mentions.complete("@Ma", 3, "Max Mustermann", 120);
		assertEquals("@Ma", e.text, "kein gültiger Spielername");
		assertEquals(3, e.caret);
	}

	@Test
	void completeRespectsTheLengthLimit() {
		Mentions.Edit e = Mentions.complete("@St", 3, "Steve_Baut", 12);
		assertEquals("@Steve_Baut ", e.text);
		e = Mentions.complete("@St", 3, "Steve_Baut", 11);
		assertEquals("@Steve_Baut", e.text, "das Leerzeichen fällt weg");
		assertEquals(11, e.caret);
		e = Mentions.complete("@St", 3, "Steve_Baut", 10);
		assertEquals("@St", e.text, "passt nicht: unverändert");
		assertEquals(3, e.caret);
		e = Mentions.complete("abc @St xyz", 7, "Steve_Baut", 19);
		assertEquals("abc @Steve_Baut xyz", e.text, "genau an der Grenze");
		assertEquals(16, e.caret);
		e = Mentions.complete("abc @St xyz", 7, "Steve_Baut", 18);
		assertEquals("abc @St xyz", e.text);
		assertEquals(7, e.caret);
		e = Mentions.complete("abc @St!", 7, "Steve_Baut", 16);
		assertEquals("abc @Steve_Baut!", e.text, "das Leerzeichen fällt weg");
		assertEquals(15, e.caret);
	}
}
