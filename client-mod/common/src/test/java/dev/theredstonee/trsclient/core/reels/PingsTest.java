package dev.theredstonee.trsclient.core.reels;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MC-Reels: welche Kommentare den Spieler betreffen (Erwähnung, Antwort auf einen eigenen Kommentar). */
class PingsTest {
	private static final String ME = "PixelCST";

	private static ReelsPost post(String id, ReelsComment... comments) {
		ReelsPost p = new ReelsPost();
		p.id = id;
		p.author = ME;
		p.comments.addAll(Arrays.asList(comments));
		return p;
	}

	private static ReelsComment comment(String id, String author, String text, long at, String replyTo) {
		ReelsComment c = new ReelsComment(author, text, at);
		c.id = id;
		c.replyTo = replyTo;
		return c;
	}

	@Test
	void mentionAndReplyFromOthersCount() {
		ReelsComment own = comment("c1", ME, "mein Kommentar", 100, null);
		ReelsComment mention = comment("c2", "Steve_Baut", "Stark! @pixelcst wo ist das?", 200, null);
		ReelsComment reply = comment("c3", "Bea", "finde ich auch", 300, "c1");
		ReelsComment both = comment("c4", "Bea", "@PixelCST genau", 400, "c1");
		ReelsComment plain = comment("c5", "Bea", "schönes Foto", 500, null);
		ReelsComment toOther = comment("c6", "Bea", "Antwort an Steve", 600, "c2");
		ReelsComment otherName = comment("c7", "Bea", "@PixelCST2 und @Steve_Baut", 700, null);
		ReelsPost p = post("p1", own, mention, reply, both, plain, toOther, otherName);
		assertTrue(Pings.counts(p, mention, ME));
		assertTrue(Pings.counts(p, reply, ME), "Antwort auf einen eigenen Kommentar, auch ohne Erwähnung");
		assertTrue(Pings.counts(p, both, ME));
		assertFalse(Pings.counts(p, plain, ME));
		assertFalse(Pings.counts(p, toOther, ME));
		assertFalse(Pings.counts(p, otherName, ME), "nur der ganze Name");
		assertEquals(3, Pings.unseen(p, ME));
		assertEquals(3, Pings.unseen(p, "pixelCST"), "der eigene Name zählt ohne Groß-/Kleinschreibung");
		assertEquals(3, Pings.unseen(Arrays.asList(post("p0"), p), ME));
		assertEquals(6, Pings.unseen(Arrays.asList(p, p), ME));
		assertEquals(2, Pings.unseen(p, "Steve_Baut"), "für Steve zählen Beas Antwort auf seinen Kommentar und ihre Erwähnung");
	}

	@Test
	void ownCommentsAndSeenOnesDoNot() {
		ReelsComment own = comment("c1", ME, "ich erwähne mich: @PixelCST", 100, null);
		ReelsComment ownReply = comment("c2", "pixelcst", "ich antworte mir", 200, "c1");
		ReelsComment seen = comment("c3", "Bea", "@PixelCST schon gesehen", 300, null);
		seen.seen = Boolean.TRUE;
		ReelsComment notSeen = comment("c4", "Bea", "@PixelCST neu", 400, null);
		notSeen.seen = Boolean.FALSE;
		ReelsPost p = post("p1", own, ownReply, seen, notSeen);
		assertFalse(Pings.counts(p, own, ME));
		assertFalse(Pings.counts(p, ownReply, ME));
		assertFalse(Pings.counts(p, seen, ME));
		assertTrue(Pings.counts(p, notSeen, ME));
		assertEquals(1, Pings.unseen(p, ME));
		assertEquals(0, Pings.unseen(p, null), "ohne eigenen Namen betrifft einen nichts");
		assertEquals(0, Pings.unseen(p, ""));
		assertEquals(0, Pings.unseen((ReelsPost) null, ME));
		assertEquals(0, Pings.unseen((List<ReelsPost>) null, ME));
		assertEquals(0, Pings.unseen(Collections.<ReelsPost>emptyList(), ME));
		ReelsPost empty = new ReelsPost();
		empty.comments = null;
		assertEquals(0, Pings.unseen(empty, ME));
	}

	@Test
	void firstIsTheOldestUnseenAndSaysWhetherItIsAMention() {
		ReelsComment own = comment("c1", ME, "mein Kommentar", 100, null);
		ReelsComment lateMention = comment("c2", "Steve_Baut", "@PixelCST spät", 900, null);
		ReelsComment reply = comment("c3", "Bea", "Antwort ohne Namen", 500, "c1");
		ReelsComment seenOld = comment("c4", "Bea", "@PixelCST alt", 50, null);
		seenOld.seen = Boolean.TRUE;
		ReelsPost newer = post("p-neu", lateMention);
		ReelsPost older = post("p-alt", own, reply, seenOld);
		List<ReelsPost> feed = Arrays.asList(newer, older);
		Pings.Hit hit = Pings.first(feed, ME);
		assertEquals("p-alt", hit.postId, "der älteste ungesehene, nicht der im neuesten Post");
		assertSame(reply, hit.comment);
		assertFalse(hit.mention, "eine Antwort ohne Erwähnung");

		reply.seen = Boolean.TRUE;
		hit = Pings.first(feed, ME);
		assertEquals("p-neu", hit.postId);
		assertSame(lateMention, hit.comment);
		assertTrue(hit.mention);

		lateMention.seen = Boolean.TRUE;
		assertNull(Pings.first(feed, ME));
		assertNull(Pings.first(null, ME));
		assertNull(Pings.first(feed, null));

		// Gleich alt: der zuerst gefundene (Reihenfolge des Feeds) bleibt.
		ReelsComment a = comment("a", "Bea", "@PixelCST a", 10, null);
		ReelsComment b = comment("b", "Bea", "@PixelCST b", 10, null);
		assertSame(a, Pings.first(Arrays.asList(post("p1", a), post("p2", b)), ME).comment);
	}
}
