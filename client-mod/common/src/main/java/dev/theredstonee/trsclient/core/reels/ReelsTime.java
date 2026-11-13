package dev.theredstonee.trsclient.core.reels;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Zeitrechnung für MC-Reels: Kalendertage, Streak und „vor 5 Min.“. Ohne Uhr und ohne Zeitzonen-Datenbank testbar. */
public final class ReelsTime {
	private static final long DAY_MS = 86400000L;

	private ReelsTime() {
	}

	/** Kalendertag (Tage seit 1970) eines Zeitpunkts in der Zeitzone mit diesem Versatz. */
	public static long epochDay(long millis, int tzOffsetMillis) {
		return Math.floorDiv(millis + tzOffsetMillis, DAY_MS);
	}

	/**
	 * Streak: Tage in Folge mit einem Post, die heute enden – oder gestern, solange heute noch kein Post da ist (der Tag
	 * ist ja noch nicht vorbei). Sonst 0.
	 */
	public static int streak(Collection<Long> days, long today) {
		if (days == null || days.isEmpty()) return 0;
		Set<Long> set = new HashSet<Long>();
		for (Long d : days) if (d != null) set.add(d);
		long day = set.contains(Long.valueOf(today)) ? today : today - 1;
		int n = 0;
		while (set.contains(Long.valueOf(day))) {
			n++;
			day--;
		}
		return n;
	}

	/**
	 * Alter eines Posts als Übersetzungsschlüssel und Zahl: {@code {"reels.ago.now", "0"}}, {@code {"reels.ago.min", "5"}},
	 * {@code {"reels.ago.hours", "2"}} oder {@code {"reels.ago.days", "3"}}. Zeitpunkte in der Zukunft (Uhr verstellt)
	 * gelten als „gerade eben“.
	 */
	public static String[] ago(long thenMs, long nowMs) {
		long s = Math.max(0L, nowMs - thenMs) / 1000L;
		if (s < 60L) return new String[]{"reels.ago.now", "0"};
		if (s < 3600L) return new String[]{"reels.ago.min", String.valueOf(s / 60L)};
		if (s < 86400L) return new String[]{"reels.ago.hours", String.valueOf(s / 3600L)};
		return new String[]{"reels.ago.days", String.valueOf(s / 86400L)};
	}
}
