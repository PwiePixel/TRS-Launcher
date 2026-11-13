package dev.theredstonee.trsclient.core.reels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Kommentare eines Posts in der Reihenfolge, in der das Handy sie zeigt: Kommentare der obersten Ebene vom ältesten
 * zum neuesten, jeder gefolgt von seinen Antworten (eine Ebene, ebenfalls vom ältesten zum neuesten). Bei gleicher Zeit
 * gilt die Reihenfolge in der Datei. Eine Antwort, deren Kommentar es nicht (mehr) gibt, zählt als oberste Ebene.
 */
public final class CommentThread {
	/** Eine Zeile der Liste. */
	public static final class Row {
		public final ReelsComment comment;
		/** Antwort auf den Kommentar der obersten Ebene davor (eingerückt). */
		public final boolean reply;

		Row(ReelsComment comment, boolean reply) {
			this.comment = comment;
			this.reply = reply;
		}
	}

	/** Ein Kommentar mit seiner Stelle in der Datei. */
	private static final class Item {
		final ReelsComment comment;
		final int index;
		final List<Item> replies = new ArrayList<Item>();

		Item(ReelsComment comment, int index) {
			this.comment = comment;
			this.index = index;
		}
	}

	private static final Comparator<Item> BY_TIME = new Comparator<Item>() {
		@Override
		public int compare(Item a, Item b) {
			if (a.comment.at != b.comment.at) return a.comment.at < b.comment.at ? -1 : 1;
			return a.index < b.index ? -1 : a.index > b.index ? 1 : 0;
		}
	};

	private CommentThread() {
	}

	public static List<Row> rows(List<ReelsComment> comments) {
		List<Row> out = new ArrayList<Row>();
		if (comments == null) return out;
		// Oberste Ebene: alles ohne replyTo – nur darauf kann eine Antwort zeigen.
		Map<String, Item> top = new HashMap<String, Item>();
		List<Item> all = new ArrayList<Item>();
		for (int i = 0; i < comments.size(); i++) {
			ReelsComment c = comments.get(i);
			if (c == null) continue;
			Item item = new Item(c, i);
			all.add(item);
			if (c.replyTo == null && c.id != null && !top.containsKey(c.id)) top.put(c.id, item);
		}
		List<Item> parents = new ArrayList<Item>();
		for (Item item : all) {
			Item parent = item.comment.replyTo == null ? null : top.get(item.comment.replyTo);
			if (parent == null || parent == item) parents.add(item);
			else parent.replies.add(item);
		}
		Collections.sort(parents, BY_TIME);
		for (Item parent : parents) {
			out.add(new Row(parent.comment, false));
			Collections.sort(parent.replies, BY_TIME);
			for (Item reply : parent.replies) out.add(new Row(reply.comment, true));
		}
		return out;
	}
}
