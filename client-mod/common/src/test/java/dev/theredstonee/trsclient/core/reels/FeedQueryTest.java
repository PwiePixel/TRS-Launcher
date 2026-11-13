package dev.theredstonee.trsclient.core.reels;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MC-Reels: Filter (alle / Freunde / ich) und Reihenfolge (neu / beliebt) des Feeds. */
class FeedQueryTest {
	private static ReelsPost post(String id, String author, int likes, long createdAt) {
		ReelsPost p = new ReelsPost();
		p.id = id;
		p.author = author;
		p.likes = likes;
		p.createdAt = createdAt;
		return p;
	}

	/** Neueste zuerst, wie das Backend sie liefert. */
	private static List<ReelsPost> feed() {
		return Arrays.asList(
				post("20261113-120005-0005", "PixelCST", 0, 500),
				post("20261113-120004-0004", "Steve_Baut", 3, 400),
				post("20261113-120003-0003", "pixelcst", 1, 300),
				post("20261113-120002-0002", "Bea", 3, 200),
				post("20261113-120001-0001", null, 0, 100));
	}

	private static List<String> ids(List<ReelsPost> list) {
		List<String> out = new ArrayList<String>();
		for (ReelsPost p : list) out.add(p.id.substring(18));
		return out;
	}

	private static Set<String> set(String... names) {
		return new HashSet<String>(Arrays.asList(names));
	}

	@Test
	void allKeepsTheBackendOrder() {
		List<ReelsPost> feed = feed();
		List<ReelsPost> all = FeedQuery.apply(feed, FeedFilter.ALL, FeedSort.NEWEST, "PixelCST", null);
		assertEquals(feed, all);
		assertNotSame(feed, all, "immer eine neue Liste");
		assertEquals(feed, FeedQuery.apply(feed, null, null, null, null), "ohne Angaben: alles, wie es kommt");
		assertTrue(FeedQuery.apply(null, FeedFilter.ALL, FeedSort.NEWEST, "PixelCST", null).isEmpty());
		assertTrue(FeedQuery.apply(Collections.<ReelsPost>emptyList(), FeedFilter.ALL, FeedSort.POPULAR, "x", null).isEmpty());
	}

	@Test
	void mineMatchesTheNameIgnoringCase() {
		assertEquals(Arrays.asList("05", "03"), ids(FeedQuery.apply(feed(), FeedFilter.MINE, FeedSort.NEWEST, "PixelCST", null)));
		assertEquals(Arrays.asList("05", "03"), ids(FeedQuery.apply(feed(), FeedFilter.MINE, FeedSort.NEWEST, "PIXELCST", set("bea"))));
		assertEquals(Arrays.asList("02"), ids(FeedQuery.apply(feed(), FeedFilter.MINE, FeedSort.NEWEST, "bea", null)));
		assertTrue(FeedQuery.apply(feed(), FeedFilter.MINE, FeedSort.NEWEST, "Pixel", null).isEmpty(), "nur der ganze Name");
		assertTrue(FeedQuery.apply(feed(), FeedFilter.MINE, FeedSort.NEWEST, null, null).isEmpty());
		assertTrue(FeedQuery.apply(feed(), FeedFilter.MINE, FeedSort.NEWEST, "", null).isEmpty(), "ein Post ohne Namen gehört niemandem");
	}

	@Test
	void friendsMatchesFriendNamesAndIsEmptyWhenTheListIsUnknown() {
		assertTrue(FeedQuery.apply(feed(), FeedFilter.FRIENDS, FeedSort.NEWEST, "PixelCST", null).isEmpty(), "Liste unbekannt");
		assertTrue(FeedQuery.apply(feed(), FeedFilter.FRIENDS, FeedSort.NEWEST, "PixelCST", set()).isEmpty(), "keine Freunde");
		assertEquals(Arrays.asList("04", "02"),
				ids(FeedQuery.apply(feed(), FeedFilter.FRIENDS, FeedSort.NEWEST, "PixelCST", set("steve_baut", "bea", "niemand"))));
		assertEquals(Arrays.asList("04"), ids(FeedQuery.apply(feed(), FeedFilter.FRIENDS, FeedSort.NEWEST, "PixelCST", set("steve_baut"))));
		assertEquals(Arrays.asList("04", "02"),
				ids(FeedQuery.apply(feed(), FeedFilter.FRIENDS, FeedSort.POPULAR, "PixelCST", set("steve_baut", "bea"))));
	}

	@Test
	void popularSortsByLikesThenNewestThenId() {
		assertEquals(Arrays.asList("04", "02", "03", "05", "01"),
				ids(FeedQuery.apply(feed(), FeedFilter.ALL, FeedSort.POPULAR, "PixelCST", null)));
		// Gleiche Likes und gleiche Zeit: die größere ID zuerst – wie im Backend.
		List<ReelsPost> tie = Arrays.asList(
				post("20261113-120000-0001", "A", 2, 100),
				post("20261113-120000-0003", "A", 2, 100),
				post("20261113-120000-0002", "A", 2, 100),
				post("20261113-120000-0009", "A", 1, 900));
		assertEquals(Arrays.asList("03", "02", "01", "09"), ids(FeedQuery.apply(tie, FeedFilter.ALL, FeedSort.POPULAR, "A", null)));
		assertEquals(Arrays.asList("03", "05"), ids(FeedQuery.apply(feed(), FeedFilter.MINE, FeedSort.POPULAR, "PixelCST", null)));
	}

	@Test
	void applyNeverChangesTheBackendList() {
		final List<ReelsPost> feed = Collections.unmodifiableList(feed());
		List<String> before = ids(feed);
		final List<ReelsPost> popular = FeedQuery.apply(feed, FeedFilter.ALL, FeedSort.POPULAR, "PixelCST", null);
		assertEquals(before, ids(feed));
		assertThrows(UnsupportedOperationException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				popular.remove(0);
			}
		});
		for (int i = 0; i < feed.size(); i++) assertTrue(popular.contains(feed.get(i)), "dieselben Posts, keine Kopien");
	}

	@Test
	void indexOfFindsById() {
		List<ReelsPost> feed = feed();
		assertEquals(0, FeedQuery.indexOf(feed, "20261113-120005-0005"));
		assertEquals(4, FeedQuery.indexOf(feed, "20261113-120001-0001"));
		assertEquals(-1, FeedQuery.indexOf(feed, "20261113-120009-0009"));
		assertEquals(-1, FeedQuery.indexOf(feed, null));
		assertEquals(-1, FeedQuery.indexOf(null, "20261113-120005-0005"));
		List<ReelsPost> popular = FeedQuery.apply(feed, FeedFilter.ALL, FeedSort.POPULAR, "PixelCST", null);
		assertEquals(3, FeedQuery.indexOf(popular, "20261113-120005-0005"), "die Stelle in der gezeigten Liste");
	}
}
