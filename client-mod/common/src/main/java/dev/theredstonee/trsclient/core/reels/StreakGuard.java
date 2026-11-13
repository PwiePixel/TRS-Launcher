package dev.theredstonee.trsclient.core.reels;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Streak-Schutz (MC-Reels), als unveränderliche Momentaufnahme: höchstens {@link #PER_MONTH} geschützte Tage je
 * Kalendermonat, gezählt nach dem Monat des geschützten Tages. Ein geschützter Tag überbrückt die Streak, zählt aber
 * nicht mit ({@link ReelsTime#streak(Collection, Collection, long)}). Geschützt werden kann nur heute („pausieren“)
 * oder gestern („retten“), und nichts, solange die Uhr hinter dem neuesten bekannten Tag liegt.
 */
public final class StreakGuard {
	/** Geschützte Tage je Kalendermonat. */
	public static final int PER_MONTH = 2;

	/** Ergebnis von {@link ReelsBackend#protect}. */
	public enum Result {
		/** Der Tag ist jetzt geschützt. */
		OK,
		/** Heute wurde schon gepostet – nichts zu pausieren. */
		POSTED_TODAY,
		/** Heute ist schon geschützt. */
		ALREADY,
		/** Es gibt keine Streak, die sich schützen ließe. */
		NO_STREAK,
		/** Gestern fehlt nicht, oder ein Schutz für gestern brächte nichts. */
		NOT_NEEDED,
		/** In dem Monat ist kein Schutz mehr übrig. */
		NONE_LEFT,
		/** Die Uhr liegt hinter dem neuesten bekannten Tag. */
		CLOCK,
		/** Zweitfenster: nichts wird gesichert. */
		READ_ONLY,
		/** Dieser Feed kennt keinen Streak-Schutz. */
		UNAVAILABLE
	}

	/** Ohne Streak-Schutz: nichts möglich, nichts übrig. */
	public static final StreakGuard NONE = new StreakGuard(0, 0, false, false, false, 0, 0, false, false, false);

	/** Die Streak, wie sie gerade gilt. */
	public final int streak;
	/** Die Streak, die gälte, wenn gestern geschützt wäre. */
	public final int streakIfRescued;
	public final boolean postedToday;
	public final boolean todayFrozen;
	public final boolean yesterdayFrozen;
	/** Übrige Schutztage im Monat von heute. */
	public final int leftThisMonth;
	/** Übrige Schutztage im Monat von gestern (am 1. ist das der Vormonat). */
	public final int leftForYesterday;
	/** Gestern fehlt (kein Post, kein Schutz), und ein Schutz für gestern machte die Streak länger. */
	public final boolean rescueOffered;
	public final boolean canPauseToday;
	public final boolean canRescueYesterday;
	/** Heute liegt vor dem neuesten bekannten Tag (Post, Schutz, Handy herausgeholt): die Uhr wurde zurückgestellt. */
	public final boolean clockBehind;
	/** Zweitfenster: nichts wird gesichert. */
	public final boolean readOnly;

	private StreakGuard(int streak, int streakIfRescued, boolean postedToday, boolean todayFrozen, boolean yesterdayFrozen,
			int leftThisMonth, int leftForYesterday, boolean rescueOffered, boolean clockBehind, boolean readOnly) {
		this.streak = streak;
		this.streakIfRescued = streakIfRescued;
		this.postedToday = postedToday;
		this.todayFrozen = todayFrozen;
		this.yesterdayFrozen = yesterdayFrozen;
		this.leftThisMonth = leftThisMonth;
		this.leftForYesterday = leftForYesterday;
		this.rescueOffered = rescueOffered;
		this.clockBehind = clockBehind;
		this.readOnly = readOnly;
		this.canPauseToday = !readOnly && !clockBehind && !postedToday && !todayFrozen && streak > 0 && leftThisMonth > 0;
		this.canRescueYesterday = rescueOffered && !readOnly && !clockBehind && leftForYesterday > 0;
	}

	/**
	 * @param postDays       Kalendertage (seit 1970) mit mindestens einem Post
	 * @param freezes        die eingelösten Schutztage (Einträge ohne gültiges Datum zählen nicht)
	 * @param today          heute (Tage seit 1970)
	 * @param newestEventDay der neueste bekannte Tag: Post, Schutz oder „Handy herausgeholt“
	 * @param readOnly       Zweitfenster
	 */
	public static StreakGuard of(Collection<Long> postDays, List<ReelsFreeze> freezes, long today, long newestEventDay,
			boolean readOnly) {
		Set<Long> posts = new HashSet<Long>();
		if (postDays != null) {
			for (Long d : postDays) if (d != null) posts.add(d);
		}
		Set<Long> frozen = new HashSet<Long>();
		if (freezes != null) {
			for (ReelsFreeze f : freezes) {
				long day = f == null ? Long.MIN_VALUE : ReelsTime.civilDay(f.day);
				if (day != Long.MIN_VALUE) frozen.add(Long.valueOf(day));
			}
		}
		Long t = Long.valueOf(today);
		Long y = Long.valueOf(today - 1);
		int streak = ReelsTime.streak(posts, frozen, today);
		boolean yesterdayFrozen = frozen.contains(y);
		Set<Long> bridged = new HashSet<Long>(frozen);
		bridged.add(y);
		int rescued = ReelsTime.streak(posts, bridged, today);
		boolean offered = !posts.contains(y) && !yesterdayFrozen && rescued > streak;
		return new StreakGuard(streak, rescued, posts.contains(t), frozen.contains(t), yesterdayFrozen,
				left(freezes, ReelsTime.civil(today).substring(0, 7)), left(freezes, ReelsTime.civil(today - 1).substring(0, 7)),
				offered, today < newestEventDay, readOnly);
	}

	/** Übrige Schutztage in einem Monat ({@code yyyy-MM}): {@link #PER_MONTH} minus die dort eingelösten, nie unter 0. */
	public static int left(List<ReelsFreeze> freezes, String month) {
		int used = 0;
		if (freezes != null && month != null) {
			for (ReelsFreeze f : freezes) {
				if (f != null && f.day != null && f.day.startsWith(month + "-") && ReelsTime.civilDay(f.day) != Long.MIN_VALUE) used++;
			}
		}
		return Math.max(0, PER_MONTH - used);
	}
}
