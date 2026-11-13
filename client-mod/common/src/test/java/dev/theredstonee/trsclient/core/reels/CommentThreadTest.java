package dev.theredstonee.trsclient.core.reels;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MC-Reels: Reihenfolge der Kommentare mit ihren Antworten. */
class CommentThreadTest {
	private static ReelsComment comment(String id, String text, long at, String replyTo) {
		ReelsComment c = new ReelsComment("A", text, at);
		c.id = id;
		c.replyTo = replyTo;
		return c;
	}

	/** Die Zeilen als Text; Antworten mit „> “ davor. */
	private static List<String> shown(List<ReelsComment> comments) {
		List<String> out = new ArrayList<String>();
		for (CommentThread.Row row : CommentThread.rows(comments)) out.add((row.reply ? "> " : "") + row.comment.text);
		return out;
	}

	@Test
	void repliesFollowTheirParentInTimeOrder() {
		List<ReelsComment> comments = Arrays.asList(
				comment("c1", "erster", 100, null),
				comment("c2", "zweiter", 200, null),
				comment("c3", "späte Antwort auf den ersten", 400, "c1"),
				comment("c4", "dritter", 300, null),
				comment("c5", "frühe Antwort auf den ersten", 250, "c1"),
				comment("c6", "Antwort auf den zweiten", 500, "c2"));
		assertEquals(Arrays.asList("erster", "> frühe Antwort auf den ersten", "> späte Antwort auf den ersten", "zweiter",
				"> Antwort auf den zweiten", "dritter"), shown(comments));
		// Die Liste des Posts bleibt, wie sie ist.
		assertEquals("erster", comments.get(0).text);
		assertEquals("Antwort auf den zweiten", comments.get(5).text);
		// Eine Antwort, die vor ihrem Kommentar in der Datei steht oder älter ist, hängt trotzdem darunter.
		assertEquals(Arrays.asList("oben", "> Antwort"),
				shown(Arrays.asList(comment("r", "Antwort", 5, "p"), comment("p", "oben", 50, null))));
	}

	@Test
	void orphansAreTopLevel() {
		List<ReelsComment> comments = Arrays.asList(
				comment("c1", "erster", 100, null),
				comment("c2", "Antwort ins Leere", 150, "gibt-es-nicht"),
				comment("c3", "Antwort", 200, "c1"),
				comment("c4", "Antwort auf eine Antwort", 300, "c3"),
				comment("c5", "auf sich selbst", 400, "c5"),
				comment(null, "ohne ID", 500, null));
		assertEquals(Arrays.asList("erster", "> Antwort", "Antwort ins Leere", "Antwort auf eine Antwort", "auf sich selbst", "ohne ID"),
				shown(comments));
		assertTrue(CommentThread.rows(null).isEmpty());
		assertTrue(CommentThread.rows(new ArrayList<ReelsComment>()).isEmpty());
		assertEquals(Arrays.asList("einer"), shown(Arrays.asList(null, comment("x", "einer", 1, null))));
	}

	@Test
	void equalTimesKeepFileOrder() {
		List<ReelsComment> comments = Arrays.asList(
				comment("c1", "a", 100, null),
				comment("c2", "b", 100, null),
				comment("c3", "Antwort 1", 100, "c2"),
				comment("c4", "c", 100, null),
				comment("c5", "Antwort 2", 100, "c2"),
				comment("c6", "früher", 50, null));
		assertEquals(Arrays.asList("früher", "a", "b", "> Antwort 1", "> Antwort 2", "c"), shown(comments));
	}
}
