package dev.theredstonee.trsclient.core.reels;

import java.util.List;

/**
 * Erwähnungen und Antworten, die den Spieler betreffen (MC-Reels). Ein Kommentar zählt, wenn ihn jemand anderes
 * geschrieben hat, er noch nicht gesehen wurde ({@link ReelsComment#seen}) und er entweder den eigenen Namen erwähnt
 * ({@link Mentions}) oder auf einen eigenen Kommentar antwortet. „Eigen“ heißt: derselbe Name, ohne
 * Groß-/Kleinschreibung.
 */
public final class Pings {
	/** Der älteste ungesehene Kommentar, der den Spieler betrifft. */
	public static final class Hit {
		public final String postId;
		public final ReelsComment comment;
		/** Erwähnt den eigenen Namen (sonst: Antwort auf einen eigenen Kommentar, ohne Erwähnung). */
		public final boolean mention;

		Hit(String postId, ReelsComment comment, boolean mention) {
			this.postId = postId;
			this.comment = comment;
			this.mention = mention;
		}
	}

	private Pings() {
	}

	/** Betrifft dieser Kommentar den Spieler, und hat er ihn noch nicht gesehen? */
	public static boolean counts(ReelsPost post, ReelsComment c, String self) {
		if (post == null || c == null || self == null || self.isEmpty()) return false;
		if (Boolean.TRUE.equals(c.seen) || self.equalsIgnoreCase(c.author)) return false;
		return Mentions.mentions(c.text, self) || repliesTo(post, c, self);
	}

	private static boolean repliesTo(ReelsPost post, ReelsComment c, String self) {
		if (c.replyTo == null || post.comments == null) return false;
		for (ReelsComment parent : post.comments) {
			if (parent != null && parent != c && c.replyTo.equals(parent.id)) return self.equalsIgnoreCase(parent.author);
		}
		return false;
	}

	/** Zahl der ungesehenen Erwähnungen und Antworten unter einem Post. */
	public static int unseen(ReelsPost post, String self) {
		if (post == null || post.comments == null) return 0;
		int n = 0;
		for (ReelsComment c : post.comments) if (counts(post, c, self)) n++;
		return n;
	}

	/** Zahl der ungesehenen Erwähnungen und Antworten im ganzen Feed. */
	public static int unseen(List<ReelsPost> feed, String self) {
		if (feed == null) return 0;
		int n = 0;
		for (ReelsPost p : feed) n += unseen(p, self);
		return n;
	}

	/** Der älteste ungesehene Kommentar, der den Spieler betrifft – oder null. */
	public static Hit first(List<ReelsPost> feed, String self) {
		if (feed == null) return null;
		ReelsPost bestPost = null;
		ReelsComment best = null;
		for (ReelsPost p : feed) {
			if (p == null || p.comments == null) continue;
			for (ReelsComment c : p.comments) {
				if (!counts(p, c, self)) continue;
				if (best == null || c.at < best.at) {
					best = c;
					bestPost = p;
				}
			}
		}
		return best == null ? null : new Hit(bestPost.id, best, Mentions.mentions(best.text, self));
	}
}
