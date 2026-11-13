package dev.theredstonee.trsclient.core.reels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Filter und Reihenfolge des Feeds (MC-Reels) – rechnet nur, ändert nie die Liste des {@link ReelsBackend}. */
public final class FeedQuery {
	private static final Comparator<ReelsPost> POPULAR = new Comparator<ReelsPost>() {
		@Override
		public int compare(ReelsPost a, ReelsPost b) {
			if (a.likes != b.likes) return a.likes > b.likes ? -1 : 1;
			if (a.createdAt != b.createdAt) return a.createdAt > b.createdAt ? -1 : 1;
			String x = a.id == null ? "" : a.id;
			String y = b.id == null ? "" : b.id;
			return y.compareTo(x);
		}
	};

	private FeedQuery() {
	}

	/**
	 * Die Posts, die der Feed zeigt, als neue, nicht änderbare Liste.
	 *
	 * @param feed         die Liste des Backends (neueste zuerst) – bleibt, wie sie ist
	 * @param self         der eigene Spielername („Ich“ vergleicht ohne Groß-/Kleinschreibung)
	 * @param friendsLower Namen der Freunde in Kleinbuchstaben; null = unbekannt, dann zeigt „Freunde“ nichts
	 */
	public static List<ReelsPost> apply(List<ReelsPost> feed, FeedFilter filter, FeedSort sort, String self,
			Set<String> friendsLower) {
		List<ReelsPost> out = new ArrayList<ReelsPost>();
		if (feed != null) {
			String me = self == null ? null : self.toLowerCase(Locale.ROOT);
			for (ReelsPost p : feed) {
				if (p == null) continue;
				if (filter == FeedFilter.MINE) {
					if (me == null || me.isEmpty() || !me.equals(lower(p.author))) continue;
				} else if (filter == FeedFilter.FRIENDS) {
					if (friendsLower == null || !friendsLower.contains(lower(p.author))) continue;
				}
				out.add(p);
			}
		}
		if (sort == FeedSort.POPULAR) Collections.sort(out, POPULAR);
		return Collections.unmodifiableList(out);
	}

	/** Stelle des Posts mit dieser ID, oder -1. */
	public static int indexOf(List<ReelsPost> list, String id) {
		if (list == null || id == null) return -1;
		for (int i = 0; i < list.size(); i++) {
			ReelsPost p = list.get(i);
			if (p != null && id.equals(p.id)) return i;
		}
		return -1;
	}

	private static String lower(String s) {
		return s == null ? "" : s.toLowerCase(Locale.ROOT);
	}
}
