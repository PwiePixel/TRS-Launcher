package dev.theredstonee.trsclient.core.reels;

import java.util.Collection;
import java.util.Collections;
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
		return streak(days, Collections.<Long>emptySet(), today);
	}

	/**
	 * Streak mit geschützten Tagen: ein geschützter Tag ohne Post überbrückt, zählt aber nicht mit. Gezählt wird ab
	 * heute, wenn heute ein Post da ist oder der Tag geschützt ist, sonst ab gestern; rückwärts zählt jeder Tag mit Post
	 * 1, ein geschützter Tag ohne Post 0, und der erste Tag ohne beides beendet die Zählung. Geschützte Tage allein sind
	 * also keine Streak.
	 */
	public static int streak(Collection<Long> postDays, Collection<Long> frozenDays, long today) {
		if (postDays == null || postDays.isEmpty()) return 0;
		Set<Long> posts = new HashSet<Long>();
		for (Long d : postDays) if (d != null) posts.add(d);
		Set<Long> frozen = new HashSet<Long>();
		if (frozenDays != null) {
			for (Long d : frozenDays) if (d != null) frozen.add(d);
		}
		Long t = Long.valueOf(today);
		long day = posts.contains(t) || frozen.contains(t) ? today : today - 1;
		int n = 0;
		while (true) {
			Long d = Long.valueOf(day);
			if (posts.contains(d)) n++;
			else if (!frozen.contains(d)) break;
			day--;
		}
		return n;
	}

	/** Kalenderdatum {@code yyyy-MM-dd} eines Tages (Tage seit 1970) – reine Ganzzahl-Rechnung, ohne Zeitzone. */
	public static String civil(long epochDay) {
		long z = epochDay + 719468L;
		long era = Math.floorDiv(z, 146097L);
		long doe = z - era * 146097L;
		long yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L;
		long doy = doe - (365L * yoe + yoe / 4L - yoe / 100L);
		long mp = (5L * doy + 2L) / 153L;
		long d = doy - (153L * mp + 2L) / 5L + 1L;
		long m = mp < 10L ? mp + 3L : mp - 9L;
		long y = yoe + era * 400L + (m <= 2L ? 1L : 0L);
		StringBuilder b = new StringBuilder(10);
		if (y < 0L) {
			b.append('-');
			y = -y;
		}
		String year = String.valueOf(y);
		for (int i = year.length(); i < 4; i++) b.append('0');
		b.append(year).append('-');
		if (m < 10L) b.append('0');
		b.append(m).append('-');
		if (d < 10L) b.append('0');
		return b.append(d).toString();
	}

	/**
	 * Tag (Tage seit 1970) eines Kalenderdatums in genau der Form {@code yyyy-MM-dd} (Jahre 0000 bis 9999) –
	 * {@link Long#MIN_VALUE}, wenn der Text kein gültiges Datum ist (falsche Form, Monat 13, 30. Februar …).
	 */
	public static long civilDay(String isoDate) {
		if (isoDate == null || isoDate.length() != 10) return Long.MIN_VALUE;
		for (int i = 0; i < 10; i++) {
			char c = isoDate.charAt(i);
			if (i == 4 || i == 7) {
				if (c != '-') return Long.MIN_VALUE;
			} else if (c < '0' || c > '9') {
				return Long.MIN_VALUE;
			}
		}
		int y = Integer.parseInt(isoDate.substring(0, 4));
		int m = Integer.parseInt(isoDate.substring(5, 7));
		int d = Integer.parseInt(isoDate.substring(8, 10));
		if (m < 1 || m > 12 || d < 1) return Long.MIN_VALUE;
		boolean leap = y % 4 == 0 && (y % 100 != 0 || y % 400 == 0);
		int[] length = {31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
		if (d > length[m - 1]) return Long.MIN_VALUE;
		long yy = m <= 2 ? y - 1L : y;
		long era = Math.floorDiv(yy, 400L);
		long yoe = yy - era * 400L;
		long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2L) / 5L + d - 1L;
		long doe = yoe * 365L + yoe / 4L - yoe / 100L + doy;
		return era * 146097L + doe - 719468L;
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
