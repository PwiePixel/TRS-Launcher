package dev.theredstonee.trsclient.core.reels;

/** Ein Kommentar unter einem Post (Gson-DTO, steht in {@code feed.json}). */
public final class ReelsComment {
	/** Höchstlänge eines Kommentars (Zeichen). */
	public static final int MAX_TEXT = 120;

	/** Dieselbe Form wie die ID eines Posts ({@link ReelsPost#validId}); eindeutig innerhalb des Posts. */
	public String id;
	public String author;
	public String text;
	/** Zeitpunkt (Millisekunden seit 1970). */
	public long at;
	/** ID des Kommentars der obersten Ebene, auf den dieser antwortet – null bei einem Kommentar der obersten Ebene. */
	public String replyTo;
	/** Zeitpunkt der letzten Bearbeitung – null, wenn nie bearbeitet. */
	public Long editedAt;
	/** TRUE, sobald der Spieler diese Erwähnung bzw. Antwort gesehen hat – sonst null. */
	public Boolean seen;

	public ReelsComment() {
	}

	public ReelsComment(String author, String text, long at) {
		this.author = author;
		this.text = text;
		this.at = at;
	}

	public ReelsComment copy() {
		ReelsComment c = new ReelsComment(author, text, at);
		c.id = id;
		c.replyTo = replyTo;
		c.editedAt = editedAt;
		c.seen = seen;
		return c;
	}
}
