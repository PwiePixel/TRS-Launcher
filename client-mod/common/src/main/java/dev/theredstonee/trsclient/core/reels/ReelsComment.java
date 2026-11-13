package dev.theredstonee.trsclient.core.reels;

/** Ein Kommentar unter einem Post (Gson-DTO, steht in {@code feed.json}). */
public final class ReelsComment {
	/** Höchstlänge eines Kommentars (Zeichen). */
	public static final int MAX_TEXT = 120;

	public String author;
	public String text;
	/** Zeitpunkt (Millisekunden seit 1970). */
	public long at;

	public ReelsComment() {
	}

	public ReelsComment(String author, String text, long at) {
		this.author = author;
		this.text = text;
		this.at = at;
	}

	public ReelsComment copy() {
		return new ReelsComment(author, text, at);
	}
}
