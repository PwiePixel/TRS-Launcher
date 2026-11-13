package dev.theredstonee.trsclient.core.reels;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MC-Reels: Streak-Schutz ohne Minecraft – Rechnung mit geschützten Tagen, Kalenderdaten, Regeln, Datei. */
class StreakGuardTest {
	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	private static final long DAY = 86400000L;
	/** 2026-11-13 12:00:00 UTC. */
	private static final long NOON = 1794571200000L;

	@TempDir
	Path tmp;

	private int salt;

	/** Mittag (UTC) an diesem Kalendertag. */
	private static long at(String iso) {
		long day = ReelsTime.civilDay(iso);
		assertTrue(day != Long.MIN_VALUE, iso);
		return day * DAY + DAY / 2;
	}

	private static List<Long> days(String... iso) {
		List<Long> out = new ArrayList<Long>();
		for (String s : iso) out.add(Long.valueOf(ReelsTime.civilDay(s)));
		return out;
	}

	private static long day(String iso) {
		return ReelsTime.civilDay(iso);
	}

	private LocalReelsBackend backend(String folder) {
		return new LocalReelsBackend(folder == null ? null : tmp.resolve(folder), UTC);
	}

	private void post(LocalReelsBackend b, String iso) {
		long now = at(iso);
		assertTrue(b.post(ReelsPost.newId(now, UTC, ++salt) + ".png", 1280, 720, false, false, "", "A", now) != null);
	}

	private static void secondWindow(LocalReelsBackend b) {
		b.readOnly = new java.util.function.BooleanSupplier() {
			@Override
			public boolean getAsBoolean() {
				return true;
			}
		};
	}

	private static String read(Path file) throws IOException {
		return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) n++;
		return n;
	}

	// --- Rechnung und Kalenderdaten ---

	@Test
	void fixtureIsTheThirteenthOfNovember() {
		assertEquals(NOON, at("2026-11-13"));
		assertEquals("2026-11-13", ReelsTime.civil(ReelsTime.epochDay(NOON, 0)));
	}

	@Test
	void protectedDayBridgesButDoesNotCount() {
		long today = day("2026-11-13");
		assertEquals(0, ReelsTime.streak(days("2026-11-10", "2026-11-11"), today), "ohne Schutz ist die Streak gerissen");
		assertEquals(1, ReelsTime.streak(days("2026-11-10", "2026-11-11", "2026-11-13"), today));
		assertEquals(2, ReelsTime.streak(days("2026-11-10", "2026-11-11"), days("2026-11-12"), today), "überbrückt, zählt aber nicht");
		assertEquals(3, ReelsTime.streak(days("2026-11-10", "2026-11-11", "2026-11-13"), days("2026-11-12"), today));
		assertEquals(2, ReelsTime.streak(days("2026-11-10", "2026-11-11", "2026-11-13"), Collections.<Long>singleton(null), today - 2),
				"ein leerer Eintrag stört nicht");
		assertEquals(1, ReelsTime.streak(days("2026-11-13"), null, today), "ohne geschützte Tage wie bisher");
	}

	@Test
	void twoProtectedDaysInARowBridge() {
		long today = day("2026-11-13");
		List<Long> posts = days("2026-11-09", "2026-11-10", "2026-11-13");
		assertEquals(3, ReelsTime.streak(posts, days("2026-11-11", "2026-11-12"), today));
		assertEquals(1, ReelsTime.streak(posts, days("2026-11-12"), today), "ein Tag Lücke bleibt eine Lücke");
		assertEquals(1, ReelsTime.streak(posts, days("2026-11-11"), today));
	}

	@Test
	void protectedDaysAloneAreNoStreak() {
		long today = day("2026-11-13");
		assertEquals(0, ReelsTime.streak(Collections.<Long>emptyList(), days("2026-11-12", "2026-11-13"), today));
		assertEquals(0, ReelsTime.streak(null, days("2026-11-13"), today));
		assertEquals(0, ReelsTime.streak(days("2026-11-05"), days("2026-11-12", "2026-11-13"), today), "der Post liegt nicht daran");
		assertEquals(1, ReelsTime.streak(days("2026-11-11"), days("2026-11-12", "2026-11-13"), today));
	}

	@Test
	void postOnAProtectedDayCountsAsAPost() {
		long today = day("2026-11-13");
		assertEquals(2, ReelsTime.streak(days("2026-11-12", "2026-11-13"), days("2026-11-13"), today));
		assertEquals(3, ReelsTime.streak(days("2026-11-11", "2026-11-12", "2026-11-13"), days("2026-11-12"), today));
	}

	@Test
	void protectedTodayKeepsTheStreakOfYesterday() {
		List<Long> posts = days("2026-11-11", "2026-11-12");
		long today = day("2026-11-13");
		assertEquals(2, ReelsTime.streak(posts, today), "heute ist noch nicht vorbei");
		assertEquals(2, ReelsTime.streak(posts, days("2026-11-13"), today), "pausiert: dieselbe Streak");
		assertEquals(0, ReelsTime.streak(posts, today + 1), "morgen ohne Schutz: gerissen");
		assertEquals(2, ReelsTime.streak(posts, days("2026-11-13"), today + 1), "morgen mit Schutz: gehalten");
		assertEquals(0, ReelsTime.streak(posts, days("2026-11-13"), today + 2), "der Schutz gilt für genau einen Tag");
	}

	@Test
	void civilDatesRoundTripAcrossLeapYearsAndBefore1970() {
		assertEquals("1970-01-01", ReelsTime.civil(0));
		assertEquals(0L, ReelsTime.civilDay("1970-01-01"));
		assertEquals("1969-12-31", ReelsTime.civil(-1));
		assertEquals(-1L, ReelsTime.civilDay("1969-12-31"));
		assertEquals(NOON / DAY, ReelsTime.civilDay("2026-11-13"));
		assertEquals(1L, ReelsTime.civilDay("2024-03-01") - ReelsTime.civilDay("2024-02-29"));
		assertEquals(1L, ReelsTime.civilDay("2026-03-01") - ReelsTime.civilDay("2026-02-28"));
		assertEquals(1L, ReelsTime.civilDay("2000-03-01") - ReelsTime.civilDay("2000-02-29"), "2000 ist ein Schaltjahr");
		assertEquals(1L, ReelsTime.civilDay("1900-03-01") - ReelsTime.civilDay("1900-02-28"), "1900 nicht");
		assertEquals(1L, ReelsTime.civilDay("2027-01-01") - ReelsTime.civilDay("2026-12-31"));
		// Gegen den Kalender von Java, über alle Jahre 0000–9999 in Schritten, und Tag für Tag um 1970 und 2026.
		for (long d = LocalDate.of(0, 1, 1).toEpochDay(); d <= LocalDate.of(9999, 12, 31).toEpochDay(); d += 97) {
			String iso = LocalDate.ofEpochDay(d).toString();
			assertEquals(iso, ReelsTime.civil(d));
			assertEquals(d, ReelsTime.civilDay(iso), iso);
		}
		for (long d = -1500; d <= 22000; d++) {
			String iso = LocalDate.ofEpochDay(d).toString();
			assertEquals(iso, ReelsTime.civil(d));
			assertEquals(d, ReelsTime.civilDay(iso), iso);
		}
		assertEquals("0000-01-01", ReelsTime.civil(LocalDate.of(0, 1, 1).toEpochDay()));
		assertEquals("9999-12-31", ReelsTime.civil(LocalDate.of(9999, 12, 31).toEpochDay()));
	}

	@Test
	void invalidDatesAreNoDay() {
		for (String bad : Arrays.asList("", "2026-13-01", "2026-00-10", "2026-11-00", "2026-11-31", "2026-02-29", "2026-02-30",
				"1900-02-29", "2026-1-01", "2026-11-1", "2026/11/13", "2026-11-13 ", " 2026-11-13", "abcd-ef-gh", "+026-11-13",
				"-026-11-13", "20261113", "2026-11-13T12", "12026-11-13", "２０２６-11-13")) {
			assertEquals(Long.MIN_VALUE, ReelsTime.civilDay(bad), bad);
		}
		assertEquals(Long.MIN_VALUE, ReelsTime.civilDay(null));
		assertTrue(ReelsTime.civilDay("2024-02-29") != Long.MIN_VALUE);
	}

	// --- Regeln ---

	@Test
	void atMostTwoProtectedDaysPerCalendarMonth() {
		LocalReelsBackend b = backend(null);
		post(b, "2026-11-10");
		assertEquals(2, b.streakGuard(at("2026-11-11")).leftThisMonth);
		assertSame(StreakGuard.Result.OK, b.protect(false, at("2026-11-11")));
		assertEquals(1, b.streakGuard(at("2026-11-11")).leftThisMonth);
		assertSame(StreakGuard.Result.OK, b.protect(false, at("2026-11-12")), "zwei geschützte Tage in Folge gehen");
		StreakGuard g = b.streakGuard(at("2026-11-13"));
		assertEquals(1, g.streak, "die Streak steht noch");
		assertEquals(0, g.leftThisMonth);
		assertFalse(g.canPauseToday);
		assertSame(StreakGuard.Result.NONE_LEFT, b.protect(false, at("2026-11-13")));
		assertEquals(0, b.streak(at("2026-11-14")), "ohne dritten Schutz ist sie am Tag darauf gerissen");

		// Ein neuer Monat hat wieder zwei.
		post(b, "2026-11-30");
		StreakGuard december = b.streakGuard(at("2026-12-01"));
		assertEquals(2, december.leftThisMonth);
		assertEquals(0, december.leftForYesterday, "gestern war noch November");
		assertTrue(december.canPauseToday);
		assertSame(StreakGuard.Result.OK, b.protect(false, at("2026-12-01")));
		assertEquals(1, b.streakGuard(at("2026-12-01")).leftThisMonth);
		assertEquals(2, StreakGuard.PER_MONTH);
	}

	@Test
	void rescueOnTheFirstCountsForLastMonth() {
		// November ist aufgebraucht: am 1. Dezember lässt sich der 30. November nicht mehr retten.
		LocalReelsBackend used = backend(null);
		post(used, "2026-11-20");
		assertSame(StreakGuard.Result.OK, used.protect(false, at("2026-11-21")));
		assertSame(StreakGuard.Result.OK, used.protect(false, at("2026-11-22")));
		for (int d = 23; d <= 29; d++) post(used, "2026-11-" + d);
		post(used, "2026-12-01");
		StreakGuard g = used.streakGuard(at("2026-12-01"));
		assertTrue(g.rescueOffered, "gestern fehlt, und ein Schutz würde helfen");
		assertEquals(1, g.streak);
		assertEquals(9, g.streakIfRescued);
		assertEquals(2, g.leftThisMonth);
		assertEquals(0, g.leftForYesterday);
		assertFalse(g.canRescueYesterday);
		assertSame(StreakGuard.Result.NONE_LEFT, used.protect(true, at("2026-12-01")));
		assertEquals(1, used.streak(at("2026-12-01")));

		// Mit einem übrigen Schutz im November geht es – und der Dezember behält seine zwei.
		LocalReelsBackend open = backend(null);
		post(open, "2026-11-20");
		assertSame(StreakGuard.Result.OK, open.protect(false, at("2026-11-21")));
		for (int d = 22; d <= 29; d++) post(open, "2026-11-" + d);
		post(open, "2026-12-01");
		StreakGuard h = open.streakGuard(at("2026-12-01"));
		assertEquals(1, h.leftForYesterday);
		assertTrue(h.canRescueYesterday);
		assertSame(StreakGuard.Result.OK, open.protect(true, at("2026-12-01")));
		StreakGuard after = open.streakGuard(at("2026-12-01"));
		assertEquals(10, after.streak);
		assertEquals(2, after.leftThisMonth, "gezählt wird im Monat des geschützten Tages");
		assertEquals(0, after.leftForYesterday);
		assertTrue(after.yesterdayFrozen);
		assertFalse(after.rescueOffered);
	}

	@Test
	void rescueIsOfferedOnlyForExactlyYesterdayAndOnlyWhenItHelps() {
		LocalReelsBackend b = backend(null);
		assertFalse(b.streakGuard(NOON).rescueOffered, "leerer Feed");
		assertSame(StreakGuard.Result.NOT_NEEDED, b.protect(true, NOON));
		post(b, "2026-11-09");
		post(b, "2026-11-10");
		StreakGuard same = b.streakGuard(at("2026-11-10"));
		assertFalse(same.rescueOffered, "gestern wurde gepostet");
		assertSame(StreakGuard.Result.NOT_NEEDED, b.protect(true, at("2026-11-10")));
		assertFalse(b.streakGuard(at("2026-11-11")).rescueOffered, "gestern wurde gepostet, heute ist noch offen");

		StreakGuard dayAfter = b.streakGuard(at("2026-11-12"));
		assertTrue(dayAfter.rescueOffered, "am Tag nach dem verpassten Tag");
		assertEquals(0, dayAfter.streak);
		assertEquals(2, dayAfter.streakIfRescued);
		assertTrue(dayAfter.canRescueYesterday);
		assertFalse(dayAfter.canPauseToday, "ohne Streak gibt es nichts zu pausieren");

		StreakGuard tooLate = b.streakGuard(at("2026-11-13"));
		assertFalse(tooLate.rescueOffered, "zwei Tage fehlen: gestern zu schützen brächte nichts");
		assertEquals(0, tooLate.streakIfRescued);
		assertFalse(tooLate.canRescueYesterday);
		assertSame(StreakGuard.Result.NOT_NEEDED, b.protect(true, at("2026-11-13")));
		assertSame(StreakGuard.Result.NO_STREAK, b.protect(false, at("2026-11-13")));

		// Der allererste Post: gestern fehlt, aber davor war auch nichts.
		LocalReelsBackend first = backend(null);
		post(first, "2026-11-13");
		StreakGuard g = first.streakGuard(NOON);
		assertEquals(1, g.streak);
		assertEquals(1, g.streakIfRescued);
		assertFalse(g.rescueOffered);
	}

	@Test
	void streakIfRescuedIsTheBridgedStreak() {
		LocalReelsBackend b = backend(null);
		post(b, "2026-11-09");
		post(b, "2026-11-10");
		post(b, "2026-11-11");
		post(b, "2026-11-13");
		StreakGuard g = b.streakGuard(NOON);
		assertTrue(g.postedToday);
		assertEquals(1, g.streak);
		assertEquals(4, g.streakIfRescued, "der geschützte Tag überbrückt, zählt aber nicht");
		assertTrue(g.rescueOffered);
		assertTrue(g.canRescueYesterday);
		assertFalse(g.canPauseToday, "heute ist schon gepostet");
		assertSame(StreakGuard.Result.OK, b.protect(true, NOON));
		assertEquals(4, b.streak(NOON));
		assertEquals(4, b.streakGuard(NOON).streak);
	}

	@Test
	void pauseTodayNeedsAStreakAndNoPostToday() {
		LocalReelsBackend b = backend(null);
		assertSame(StreakGuard.Result.NO_STREAK, b.protect(false, NOON));
		post(b, "2026-11-12");
		StreakGuard g = b.streakGuard(NOON);
		assertEquals(1, g.streak);
		assertFalse(g.postedToday);
		assertFalse(g.todayFrozen);
		assertTrue(g.canPauseToday);
		List<ReelsPost> before = b.feed();
		assertSame(StreakGuard.Result.OK, b.protect(false, NOON));
		assertNotSame(before, b.feed(), "die Oberfläche merkt die Änderung an der neuen Liste");
		StreakGuard paused = b.streakGuard(NOON);
		assertTrue(paused.todayFrozen);
		assertFalse(paused.canPauseToday);
		assertEquals(1, paused.streak);
		assertSame(StreakGuard.Result.ALREADY, b.protect(false, NOON));
		assertEquals(1, b.streak(NOON + DAY), "am Tag darauf steht die Streak noch");

		LocalReelsBackend posted = backend(null);
		post(posted, "2026-11-12");
		post(posted, "2026-11-13");
		assertFalse(posted.streakGuard(NOON).canPauseToday);
		assertSame(StreakGuard.Result.POSTED_TODAY, posted.protect(false, NOON));
		assertEquals(2, posted.streakGuard(NOON).leftThisMonth, "nichts verbraucht");
	}

	@Test
	void pauseTodayIsPossibleRightAfterARescue() {
		LocalReelsBackend b = backend(null);
		post(b, "2026-11-10");
		post(b, "2026-11-11");
		long now = NOON;
		assertFalse(b.streakGuard(now).canPauseToday, "gerissen: erst retten");
		assertSame(StreakGuard.Result.OK, b.protect(true, now));
		StreakGuard g = b.streakGuard(now);
		assertEquals(2, g.streak);
		assertEquals(1, g.leftThisMonth);
		assertTrue(g.canPauseToday);
		assertFalse(g.clockBehind);
		assertSame(StreakGuard.Result.OK, b.protect(false, now));
		assertEquals(0, b.streakGuard(now).leftThisMonth);
		assertEquals(2, b.streak(now + DAY));
		assertEquals(0, b.streak(now + 2 * DAY));
	}

	@Test
	void aClockSetBackBehindTheNewestPostGivesNoProtection() {
		LocalReelsBackend b = backend(null);
		post(b, "2026-11-11");
		post(b, "2026-11-13");
		StreakGuard g = b.streakGuard(at("2026-11-12"));
		assertTrue(g.clockBehind);
		assertEquals(1, g.streak, "gerechnet wird trotzdem");
		assertFalse(g.canPauseToday);
		assertFalse(g.canRescueYesterday);
		assertSame(StreakGuard.Result.CLOCK, b.protect(false, at("2026-11-12")));
		assertSame(StreakGuard.Result.CLOCK, b.protect(true, at("2026-11-12")));
		assertFalse(b.streakGuard(NOON).clockBehind, "am Tag des neuesten Posts ist alles in Ordnung");
	}

	@Test
	void clockSetBackAfterThePhoneWasOpenGivesNoProtection() throws IOException {
		LocalReelsBackend b = backend("uhr");
		post(b, "2026-11-10");
		assertTrue(b.streakGuard(at("2026-11-11")).canPauseToday, "am 11. ginge es");
		// Am 13. holt der Spieler das Handy heraus und sieht die gerissene Streak …
		b.noteClock(NOON);
		assertFalse(b.streakGuard(NOON).clockBehind);
		// … und stellt die Uhr auf den 11. zurück.
		StreakGuard g = b.streakGuard(at("2026-11-11"));
		assertTrue(g.clockBehind);
		assertFalse(g.canPauseToday);
		assertSame(StreakGuard.Result.CLOCK, b.protect(false, at("2026-11-11")));
		assertSame(StreakGuard.Result.CLOCK, b.protect(true, at("2026-11-12")));
		b.flush();
		// Auch nach einem Neustart.
		LocalReelsBackend again = backend("uhr");
		assertSame(StreakGuard.Result.CLOCK, again.protect(false, at("2026-11-11")));
		assertFalse(again.streakGuard(NOON).clockBehind);
		assertFalse(read(tmp.resolve("uhr").resolve("feed.json")).contains("\"freezes\""));
	}

	@Test
	void aClockSetBackCannotAddAThirdDayToAMonth() throws IOException {
		LocalReelsBackend b = backend("drei");
		post(b, "2026-11-10");
		assertSame(StreakGuard.Result.OK, b.protect(false, at("2026-11-11")));
		assertSame(StreakGuard.Result.OK, b.protect(false, at("2026-11-12")));
		for (int d = 1; d <= 30; d++) {
			String iso = "2026-11-" + (d < 10 ? "0" : "") + d;
			assertFalse(b.protect(false, at(iso)) == StreakGuard.Result.OK, "pausieren am " + iso);
			assertFalse(b.protect(true, at(iso)) == StreakGuard.Result.OK, "retten am " + iso);
			assertEquals(0, b.streakGuard(at(iso)).leftThisMonth, iso);
		}
		assertSame(StreakGuard.Result.CLOCK, b.protect(false, at("2026-11-05")));
		assertSame(StreakGuard.Result.NOT_NEEDED, b.protect(true, at("2026-12-01")), "auch vom 1. aus nicht");
		b.flush();
		assertEquals(2, count(read(tmp.resolve("drei").resolve("feed.json")), "\"day\": \"2026-11-"));
	}

	@Test
	void usedProtectionsAreNeverRefunded() {
		LocalReelsBackend b = backend(null);
		post(b, "2026-11-12");
		assertSame(StreakGuard.Result.OK, b.protect(false, NOON));
		assertEquals(1, b.streakGuard(NOON).leftThisMonth);
		post(b, "2026-11-13");
		StreakGuard g = b.streakGuard(NOON + 1000);
		assertTrue(g.postedToday);
		assertTrue(g.todayFrozen);
		assertEquals(2, g.streak, "der Post zählt als Post");
		assertEquals(1, g.leftThisMonth, "der Schutz bleibt verbraucht");
		assertFalse(g.canPauseToday);
	}

	@Test
	void readOnlyWindowRefusesAndChangesNothing() throws IOException {
		LocalReelsBackend first = backend("zweit");
		post(first, "2026-11-12");
		first.flush();
		String before = read(tmp.resolve("zweit").resolve("feed.json"));

		LocalReelsBackend second = backend("zweit");
		secondWindow(second);
		List<ReelsPost> feed = second.feed();
		StreakGuard g = second.streakGuard(NOON);
		assertTrue(g.readOnly);
		assertEquals(1, g.streak);
		assertEquals(2, g.leftThisMonth);
		assertFalse(g.canPauseToday);
		assertSame(StreakGuard.Result.READ_ONLY, second.protect(false, NOON));
		assertSame(StreakGuard.Result.READ_ONLY, second.protect(true, NOON));
		assertSame(feed, second.feed());
		assertFalse(second.dirty());
		second.flush();
		assertEquals(before, read(tmp.resolve("zweit").resolve("feed.json")));
		assertFalse(first.streakGuard(NOON).readOnly);
	}

	@Test
	void streakAfterPostRespectsProtectedDays() {
		LocalReelsBackend b = backend(null);
		post(b, "2026-11-10");
		post(b, "2026-11-11");
		assertSame(StreakGuard.Result.OK, b.protect(false, at("2026-11-12")));
		assertEquals(2, b.streak(NOON));
		assertEquals(3, b.streakAfterPost(NOON), "der geschützte 12. überbrückt");
		assertEquals(3, b.streakAfterPost(at("2026-11-12")), "am geschützten Tag selbst zählt der Post als Post");
		assertEquals(1, b.streakAfterPost(at("2026-11-14")), "der 13. fehlt");
		assertEquals(2, b.streak(NOON), "nur gerechnet");
	}

	// --- Datei ---

	@Test
	void protectionsSurviveFlushAndReload() throws IOException {
		LocalReelsBackend a = backend("f");
		post(a, "2026-11-11");
		assertSame(StreakGuard.Result.OK, a.protect(false, at("2026-11-12")));
		assertTrue(a.dirty());
		a.flush();
		String json = read(tmp.resolve("f").resolve("feed.json"));
		assertTrue(json.contains("\"freezes\""), json);
		assertTrue(json.contains("\"day\": \"2026-11-12\""), json);
		assertTrue(json.contains("\"usedAt\": " + at("2026-11-12")), json);

		LocalReelsBackend b = backend("f");
		assertFalse(b.dirty());
		assertEquals(1, b.streak(NOON));
		StreakGuard g = b.streakGuard(NOON);
		assertTrue(g.yesterdayFrozen);
		assertEquals(1, g.leftThisMonth);
		assertSame(StreakGuard.Result.OK, b.protect(false, NOON));
		b.flush();
		assertEquals(2, count(read(tmp.resolve("f").resolve("feed.json")), "\"day\""));
		assertEquals(0, backend("f").streakGuard(NOON).leftThisMonth);
	}

	private static final String ONE_POST = "{\"id\":\"20261112-120000-0001\",\"author\":\"A\",\"photo\":\"20261112-120000-0001.png\","
			+ "\"width\":1280,\"height\":720,\"createdAt\":" + (NOON - DAY) + ",\"comments\":[]}";

	private LocalReelsBackend fromFile(String folder, String rest) throws IOException {
		Path dir = tmp.resolve(folder);
		Files.createDirectories(dir);
		Files.write(dir.resolve("feed.json"), ("{\"version\":1,\"posts\":[" + ONE_POST + "]" + rest + "}").getBytes(StandardCharsets.UTF_8));
		return new LocalReelsBackend(dir, UTC);
	}

	@Test
	void oldFeedWithoutFreezesAndClockDayLoads() throws IOException {
		LocalReelsBackend a = fromFile("alt", "");
		assertFalse(a.dirty());
		assertEquals(1, a.feed().size());
		StreakGuard g = a.streakGuard(NOON);
		assertEquals(1, g.streak);
		assertEquals(2, g.leftThisMonth);
		assertEquals(2, g.leftForYesterday);
		assertFalse(g.clockBehind);
		assertTrue(g.canPauseToday);
		assertFalse(fromFile("alt2", ",\"freezes\":null,\"clockDay\":null").streakGuard(NOON).clockBehind);
		assertEquals(2, fromFile("alt3", ",\"freezes\":[]").streakGuard(NOON).leftThisMonth);
	}

	@Test
	void noFreezesKeyIsWrittenWhileNoneWasUsed() throws IOException {
		LocalReelsBackend a = backend("ohne");
		post(a, "2026-11-12");
		assertSame(StreakGuard.Result.POSTED_TODAY, a.protect(false, at("2026-11-12")));
		a.flush();
		String json = read(tmp.resolve("ohne").resolve("feed.json"));
		assertFalse(json.contains("\"freezes\""), json);
		assertFalse(json.contains("\"clockDay\""), "ohne Herausholen des Handys kein Tag");
	}

	@Test
	void clockDayIsWrittenOncePerDayAndNeverMovesBackwards() throws IOException {
		LocalReelsBackend a = backend("tag");
		post(a, "2026-11-12");
		a.flush();
		Path file = tmp.resolve("tag").resolve("feed.json");
		List<ReelsPost> feed = a.feed();
		a.noteClock(NOON);
		assertTrue(a.dirty(), "der erste Blick des Tages wird gemerkt");
		assertSame(feed, a.feed(), "der Feed selbst hat sich nicht geändert");
		a.flush();
		assertTrue(read(file).contains("\"clockDay\": \"2026-11-13\""), read(file));

		a.noteClock(NOON + 3600000L);
		assertFalse(a.dirty(), "einmal am Tag");
		a.noteClock(NOON - DAY);
		a.noteClock(NOON - 400 * DAY);
		assertFalse(a.dirty(), "nie zurück");
		assertTrue(a.streakGuard(NOON - DAY).clockBehind);

		a.noteClock(NOON + DAY);
		assertTrue(a.dirty());
		a.flush();
		assertTrue(read(file).contains("\"clockDay\": \"2026-11-14\""), read(file));
		assertEquals(1, count(read(file), "clockDay"));

		LocalReelsBackend b = backend("tag");
		b.noteClock(NOON + DAY);
		assertFalse(b.dirty(), "der Tag kommt aus der Datei zurück");
		assertTrue(b.streakGuard(NOON).clockBehind);
	}

	@Test
	void clockDayIsNotWrittenForAnEmptyFeedOrInASecondWindow() throws IOException {
		LocalReelsBackend empty = backend("leer");
		empty.noteClock(NOON);
		assertFalse(empty.dirty(), "ein leerer Feed bleibt ungeschrieben");
		empty.flush();
		assertFalse(Files.exists(tmp.resolve("leer").resolve("feed.json")));

		LocalReelsBackend first = backend("zwei");
		post(first, "2026-11-12");
		first.flush();
		String before = read(tmp.resolve("zwei").resolve("feed.json"));
		LocalReelsBackend second = backend("zwei");
		secondWindow(second);
		second.noteClock(NOON);
		assertFalse(second.dirty());
		assertFalse(second.streakGuard(NOON - DAY).clockBehind, "im Zweitfenster wird auch nichts gemerkt");
		second.flush();
		assertEquals(before, read(tmp.resolve("zwei").resolve("feed.json")));
	}

	@Test
	void invalidClockDayIsIgnoredOnLoad() throws IOException {
		for (String bad : Arrays.asList("\"2099-99-99\"", "\"morgen\"", "\"\"", "\"2026-11-31\"", "20261114", "true")) {
			LocalReelsBackend a = fromFile("uhr" + Math.abs(bad.hashCode()), ",\"clockDay\":" + bad);
			assertEquals(1, a.feed().size(), bad);
			StreakGuard g = a.streakGuard(NOON);
			assertFalse(g.clockBehind, bad);
			assertTrue(g.canPauseToday, bad);
		}
		LocalReelsBackend good = fromFile("uhr-gut", ",\"clockDay\":\"2026-11-14\"");
		assertTrue(good.streakGuard(NOON).clockBehind);
		assertFalse(good.streakGuard(NOON + DAY).clockBehind);
	}

	@Test
	void moreThanTwoPerMonthInAHandEditedFileAreDropped() throws IOException {
		// Fünf Tage im November eingetragen: nur die zwei zuerst eingelösten gelten.
		LocalReelsBackend a = fromFile("viele", ",\"freezes\":["
				+ "{\"day\":\"2026-11-08\",\"usedAt\":500},"
				+ "{\"day\":\"2026-11-07\",\"usedAt\":100},"
				+ "{\"day\":\"2026-11-09\",\"usedAt\":100},"
				+ "{\"day\":\"2026-11-10\",\"usedAt\":300},"
				+ "{\"day\":\"2026-11-11\",\"usedAt\":400},"
				+ "{\"day\":\"2026-10-31\",\"usedAt\":900}]");
		assertFalse(a.dirty());
		StreakGuard g = a.streakGuard(NOON);
		assertEquals(0, g.leftThisMonth);
		assertEquals(1, g.streak);
		assertSame(StreakGuard.Result.NONE_LEFT, a.protect(false, NOON));
		a.toggleLike("20261112-120000-0001");
		a.flush();
		String json = read(tmp.resolve("viele").resolve("feed.json"));
		assertEquals(3, count(json, "\"day\""), json);
		assertTrue(json.contains("2026-11-07") && json.contains("2026-11-09") && json.contains("2026-10-31"), json);
		assertTrue(json.indexOf("2026-11-07") < json.indexOf("2026-11-09") && json.indexOf("2026-11-09") < json.indexOf("2026-10-31"),
				"in der Reihenfolge des Einlösens: " + json);
	}

	@Test
	void invalidAndDuplicateDaysAreDroppedOnLoad() throws IOException {
		LocalReelsBackend a = fromFile("kaputt", ",\"freezes\":[null,"
				+ "{\"usedAt\":5},"
				+ "{\"day\":\"2026-11-31\",\"usedAt\":5},"
				+ "{\"day\":\"gestern\",\"usedAt\":5},"
				+ "{\"day\":\"2026-11-11\",\"usedAt\":7},"
				+ "{\"day\":\"2026-11-11\",\"usedAt\":3}]");
		assertEquals(1, a.feed().size());
		StreakGuard g = a.streakGuard(NOON);
		assertEquals(1, g.leftThisMonth, "nur der eine gültige Tag zählt, und nur einmal");
		assertSame(StreakGuard.Result.OK, a.protect(false, NOON));
		a.flush();
		String json = read(tmp.resolve("kaputt").resolve("feed.json"));
		assertEquals(2, count(json, "\"day\""), json);
		assertTrue(json.contains("\"usedAt\": 7"), "der erste Eintrag eines Tages bleibt: " + json);
	}

	@Test
	void protectionKeepsItsDateAfterATimeZoneChange() throws IOException {
		Path dir = tmp.resolve("zone");
		LocalReelsBackend utc = new LocalReelsBackend(dir, UTC);
		long postAt = at("2026-11-12");
		utc.post(ReelsPost.newId(postAt, UTC, 1) + ".png", 1280, 720, false, false, "", "A", postAt);
		// Eingelöst am 13. um 23:30 UTC – in Berlin ist da schon der 14.
		long lateEvening = ReelsTime.civilDay("2026-11-13") * DAY + 23 * 3600000L + 1800000L;
		assertSame(StreakGuard.Result.OK, utc.protect(false, lateEvening));
		utc.flush();
		assertTrue(read(dir.resolve("feed.json")).contains("\"day\": \"2026-11-13\""));

		LocalReelsBackend berlin = new LocalReelsBackend(dir, TimeZone.getTimeZone("Europe/Berlin"));
		long nextMorning = at("2026-11-14");
		StreakGuard g = berlin.streakGuard(nextMorning);
		assertTrue(g.yesterdayFrozen, "geschützt bleibt der 13., wie der Spieler ihn gesehen hat");
		assertFalse(g.todayFrozen);
		assertEquals(1, g.streak);
		assertFalse(g.clockBehind);
		berlin.toggleLike(berlin.feed().get(0).id);
		berlin.flush();
		assertTrue(read(dir.resolve("feed.json")).contains("\"day\": \"2026-11-13\""), "das Datum wird nie umgerechnet");
	}
}
