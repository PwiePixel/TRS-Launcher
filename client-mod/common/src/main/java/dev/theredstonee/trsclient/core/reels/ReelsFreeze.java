package dev.theredstonee.trsclient.core.reels;

/**
 * Ein geschützter Tag der Streak (Gson-DTO, steht unter {@code freezes} in {@code feed.json}). Der Tag ist das
 * Kalenderdatum, das der Spieler gesehen hat – er hängt nicht an der Zeitzone, die später gilt. Einmal eingelöste
 * Schutztage werden nie wieder entfernt.
 */
public final class ReelsFreeze {
	/** Kalenderdatum {@code yyyy-MM-dd}. */
	public String day;
	/** Zeitpunkt des Einlösens (Millisekunden seit 1970). */
	public long usedAt;

	public ReelsFreeze() {
	}

	public ReelsFreeze(String day, long usedAt) {
		this.day = day;
		this.usedAt = usedAt;
	}

	public ReelsFreeze copy() {
		return new ReelsFreeze(day, usedAt);
	}
}
