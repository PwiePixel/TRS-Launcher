package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.module.TrsModules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MC-Reels ohne Minecraft: lokaler Feed auf der Platte, Streak, Blättern, Foto-Ausschnitt, Tastensperre. */
class ReelsStoreTest {
	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	private static final long DAY = 86400000L;
	/** 2026-11-13 12:00:00 UTC. */
	private static final long NOON = 1794571200000L;
	private static final Executor DIRECT = new Executor() {
		@Override
		public void execute(Runnable command) {
			command.run();
		}
	};

	@TempDir
	Path tmp;

	@AfterEach
	void reset() {
		Reels.clearForTest();
	}

	private static String photo(int n) {
		return ReelsPost.newId(NOON, UTC, n) + ".png";
	}

	// --- Feed auf der Platte ---

	@Test
	void postSurvivesFlushAndReload() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		assertTrue(a.feed().isEmpty());
		assertEquals(0, a.streak(NOON));
		a.flush();
		assertFalse(Files.exists(tmp.resolve("feed.json")), "ohne Änderung wird nichts geschrieben");

		ReelsPost p = a.post(photo(1), 608, 1080, true, true, "  Schaut mal,\nwer bei mir eingezogen ist!  ", "PixelDEV_CAM", NOON);
		assertNotNull(p);
		assertEquals("20261113-120000-0001", p.id, "die ID ist der Name des Fotos");
		assertEquals("Schaut mal, wer bei mir eingezogen ist!", p.caption, "Ränder weg, Zeilenumbruch wird Leerzeichen");
		assertTrue(a.dirty());
		a.flush();
		assertFalse(a.dirty());
		assertTrue(Files.isRegularFile(tmp.resolve("feed.json")));
		assertFalse(Files.exists(tmp.resolve("feed.json.tmp")), "atomar geschrieben, keine Reste");

		LocalReelsBackend b = new LocalReelsBackend(tmp, UTC);
		assertEquals(1, b.feed().size());
		ReelsPost q = b.feed().get(0);
		assertEquals(p.id, q.id);
		assertEquals("PixelDEV_CAM", q.author);
		assertEquals(p.caption, q.caption);
		assertEquals(photo(1), q.photo);
		assertEquals(608, q.width);
		assertEquals(1080, q.height);
		assertTrue(q.portrait);
		assertTrue(q.selfie);
		assertEquals(NOON, q.createdAt);
		assertEquals(0, q.likes);
		assertFalse(q.likedByMe);
		assertTrue(q.comments.isEmpty());
		assertEquals(1, b.streak(NOON));
		assertEquals(tmp.resolve("photos").resolve(photo(1)), b.photoFile(q));
	}

	@Test
	void feedIsNewestFirstUnmodifiableAndChangesIdentityOnChange() {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		ReelsPost old = a.post(photo(1), 1280, 720, false, false, "alt", "A", NOON - DAY);
		List<ReelsPost> before = a.feed();
		assertSame(before, a.feed(), "ohne Änderung dieselbe Liste");
		ReelsPost fresh = a.post(photo(2), 1280, 720, false, false, "neu", "A", NOON);
		List<ReelsPost> after = a.feed();
		assertNotSame(before, after, "nach einer Änderung eine neue Liste");
		assertEquals(Arrays.asList(fresh, old), after, "neueste zuerst");
		assertEquals(1, before.size(), "die alte Liste bleibt, wie sie war");
		assertThrows(UnsupportedOperationException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				a.feed().clear();
			}
		});
		// Dasselbe Foto ein zweites Mal: eigene ID.
		ReelsPost again = a.post(photo(2), 1280, 720, false, false, "", "A", NOON);
		assertNotEquals(fresh.id, again.id);
		assertTrue(ReelsPost.validId(again.id), again.id);
	}

	@Test
	void brokenFeedFileIsKeptAsideBeforeTheFirstWrite() throws IOException {
		Path feed = tmp.resolve("feed.json");
		Files.write(feed, "{ \"posts\": [ kaputt".getBytes(StandardCharsets.UTF_8));
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		assertTrue(a.feed().isEmpty(), "kaputte Datei: leerer Feed, kein Absturz");
		a.flush();
		assertFalse(Files.exists(tmp.resolve("feed.json.broken")), "ohne Änderung bleibt alles liegen");
		assertEquals("{ \"posts\": [ kaputt", new String(Files.readAllBytes(feed), StandardCharsets.UTF_8));

		a.post(photo(1), 1280, 720, false, false, "neu", "A", NOON);
		a.flush();
		Path broken = tmp.resolve("feed.json.broken");
		assertTrue(Files.isRegularFile(broken), "die unlesbare Datei wird vor dem Überschreiben aufgehoben");
		assertEquals("{ \"posts\": [ kaputt", new String(Files.readAllBytes(broken), StandardCharsets.UTF_8));
		assertEquals(1, new LocalReelsBackend(tmp, UTC).feed().size(), "die neue Datei ist lesbar");

		// Leere Datei zählt ebenfalls als unlesbar.
		Path other = tmp.resolve("leer");
		Files.createDirectories(other);
		Files.write(other.resolve("feed.json"), new byte[0]);
		assertTrue(new LocalReelsBackend(other, UTC).feed().isEmpty());
	}

	@Test
	void postsWithForeignFileNamesAreDropped() throws IOException {
		String json = "{\"version\":1,\"posts\":["
				+ "{\"id\":\"20261113-120000-0001\",\"photo\":\"../../options.txt\",\"createdAt\":5},"
				+ "{\"id\":\"../x\",\"photo\":\"20261113-120000-0002.png\",\"createdAt\":6},"
				+ "null,"
				+ "{\"id\":\"20261113-120000-0003\",\"photo\":\"20261113-120000-0003.png\",\"createdAt\":7,\"likes\":-4,"
				+ "\"comments\":[null,{\"author\":\"B\",\"text\":\"  \"},{\"author\":\"B\",\"text\":\"hi\",\"at\":9}]},"
				+ "{\"id\":\"20261113-120000-0003\",\"photo\":\"20261113-120000-0003.png\",\"createdAt\":8}"
				+ "]}";
		Files.write(tmp.resolve("feed.json"), json.getBytes(StandardCharsets.UTF_8));
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		assertEquals(1, a.feed().size(), "nur der Post mit gültiger ID und gültigem Foto-Namen, Doppelte fallen weg");
		ReelsPost p = a.feed().get(0);
		assertEquals(7, p.createdAt);
		assertEquals(0, p.likes);
		assertEquals("", p.caption);
		assertEquals(1, p.comments.size());
		assertEquals("hi", p.comments.get(0).text);

		ReelsPost evil = new ReelsPost();
		evil.photo = "../feed.json";
		assertNull(a.photoFile(evil), "nie ein Pfad außerhalb des Foto-Ordners");
		assertNull(a.newPhotoFile("../x"));
		assertNull(a.post("../x.png", 1, 1, false, false, "", "", NOON));
		assertFalse(ReelsPost.validPhotoName("20261113-120000-0003.jpg"));
		assertFalse(ReelsPost.validPhotoName(null));
	}

	@Test
	void likeTogglesBetweenZeroAndOne() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "", "A", NOON);
		assertTrue(a.toggleLike(p.id));
		assertEquals(1, a.feed().get(0).likes);
		assertTrue(a.feed().get(0).likedByMe);
		a.flush();
		assertEquals(1, new LocalReelsBackend(tmp, UTC).feed().get(0).likes, "Like bleibt nach dem Neuladen");
		assertFalse(a.toggleLike(p.id));
		assertEquals(0, a.feed().get(0).likes);
		assertFalse(a.feed().get(0).likedByMe);
		assertFalse(a.toggleLike("20000101-000000-0000"), "unbekannter Post: nichts passiert");
		assertFalse(a.toggleLike(null));
	}

	@Test
	void commentsAndCaptionsAreStoredAndTrimmed() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		StringBuilder longText = new StringBuilder();
		for (int i = 0; i < 300; i++) longText.append((char) ('a' + i % 26));
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, longText.toString(), "A", NOON);
		assertEquals(80, p.caption.length(), "Text zum Foto: höchstens 80 Zeichen");
		assertEquals(longText.substring(0, 80), p.caption);

		ReelsComment c = a.comment(p.id, "Bea", "  " + longText + "  ", NOON + 1000);
		assertNotNull(c);
		assertEquals(120, c.text.length(), "Kommentar: höchstens 120 Zeichen");
		assertEquals("Bea", c.author);
		assertEquals(NOON + 1000, c.at);
		assertNull(a.comment(p.id, "Bea", "   \n ", NOON), "leerer Kommentar wird nicht gespeichert");
		assertNull(a.comment("20000101-000000-0000", "Bea", "hallo", NOON));
		assertNotNull(a.comment(p.id, "Bea", "zweiter §cbunt", NOON + 2000));
		a.flush();

		List<ReelsComment> read = new LocalReelsBackend(tmp, UTC).feed().get(0).comments;
		assertEquals(2, read.size());
		assertEquals(c.text, read.get(0).text);
		assertEquals("zweiter  cbunt", read.get(1).text, "Formatierungszeichen des Spiels werden entschärft");

		// Ein Zeichenpaar (Emoji) wird beim Kürzen nicht zerschnitten.
		String pair = new String(Character.toChars(0x1F600));
		String cut = ReelsPost.clean("abc" + pair, 4);
		assertEquals("abc", cut);
		assertEquals("", ReelsPost.clean(null, 10));
	}

	@Test
	void halfACharacterPairIsDroppedAndNeverBlocksSaving() throws IOException {
		// Ein Emoji sind zwei Zeichen (ein Paar). Bleibt nur eine Hälfte (Rücktaste, Längengrenze), ist das kein Zeichen.
		String pair = new String(Character.toChars(0x1F642));
		char high = pair.charAt(0), low = pair.charAt(1);
		assertEquals("nice", ReelsPost.clean("nice " + high, 80));
		assertEquals("ab", ReelsPost.clean("a" + low + "b", 80));
		assertEquals("a" + pair + "b", ReelsPost.clean("a" + pair + "b", 80), "ein ganzes Paar bleibt");
		assertEquals(pair, ReelsPost.clean("" + high + pair + low, 80));
		assertEquals("", ReelsPost.clean("" + low + high, 80));

		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "nice " + high, "A" + high, NOON);
		assertEquals("nice", p.caption);
		assertEquals("A", p.author);
		ReelsComment c = a.comment(p.id, "B" + low, low + "ok " + pair, NOON + 1000);
		assertNotNull(c);
		assertEquals("ok " + pair, c.text);
		assertEquals("B", c.author);
		a.flush();
		assertFalse(a.dirty());
		assertFalse(Files.exists(tmp.resolve("feed.json.tmp")));
		ReelsPost read = new LocalReelsBackend(tmp, UTC).feed().get(0);
		assertEquals("nice", read.caption);
		assertEquals("ok " + pair, read.comments.get(0).text, "ein ganzes Paar übersteht Sichern und Laden");

		// Sollte doch einmal ein halbes Paar im Feed stehen: gesichert wird trotzdem – der Rest des Feeds hängt nicht daran.
		a.feed().get(0).caption = "kaputt" + high;
		a.toggleLike(p.id);
		a.post(photo(2), 1280, 720, false, false, "danach", "A", NOON + 2000);
		a.flush();
		assertFalse(a.dirty(), "ein Text, der sich nicht schreiben lässt, hält das Sichern nicht auf");
		assertFalse(Files.exists(tmp.resolve("feed.json.tmp")));
		LocalReelsBackend b = new LocalReelsBackend(tmp, UTC);
		assertEquals(2, b.feed().size());
		assertEquals("danach", b.feed().get(0).caption);
		assertTrue(b.feed().get(1).caption.startsWith("kaputt"), b.feed().get(1).caption);
		assertTrue(b.feed().get(1).likedByMe);
	}

	@Test
	void halfACharacterPairInAHandEditedFeedIsDroppedOnLoad() throws IOException {
		String id = ReelsPost.newId(NOON, UTC, 7);
		String json = "{\"version\":1,\"posts\":[{\"id\":\"" + id + "\",\"author\":\"A\\ud83d\",\"caption\":\"x\\ud83dy\",\"photo\":\"" + id
				+ ".png\",\"width\":1280,\"height\":720,\"createdAt\":" + NOON + ",\"comments\":[{\"author\":\"B\",\"text\":\"\\ude42hm\",\"at\":"
				+ NOON + "}]}]}";
		Files.write(tmp.resolve("feed.json"), json.getBytes(StandardCharsets.UTF_8));
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		assertEquals(1, a.feed().size());
		ReelsPost p = a.feed().get(0);
		assertEquals("A", p.author);
		assertEquals("xy", p.caption);
		assertEquals("hm", p.comments.get(0).text);
		a.toggleLike(p.id);
		a.flush();
		assertFalse(a.dirty());
		assertTrue(new LocalReelsBackend(tmp, UTC).feed().get(0).likedByMe);
	}

	@Test
	void readOnlySecondWindowWritesNothing() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		a.readOnly = new java.util.function.BooleanSupplier() {
			@Override
			public boolean getAsBoolean() {
				return true;
			}
		};
		a.post(photo(1), 1280, 720, false, false, "", "A", NOON);
		a.flush();
		assertFalse(Files.exists(tmp.resolve("feed.json")), "Zweitfenster: feed.json gehört dem ersten Spiel");
		assertEquals(1, a.feed().size(), "im Speicher geht es trotzdem weiter");

		// Ohne Ordner: nur im Speicher, nichts wirft.
		LocalReelsBackend memory = new LocalReelsBackend(null, UTC);
		assertNotNull(memory.post(photo(2), 1, 1, false, false, "", "", NOON));
		memory.flush();
		assertNull(memory.photoFile(memory.feed().get(0)));
		assertNull(memory.newPhotoFile("20261113-120000-0002"));
	}

	// --- Streak und Zeit ---

	@Test
	void streakCountsDaysInARow() {
		long today = 20000;
		assertEquals(0, ReelsTime.streak(Collections.<Long>emptyList(), today));
		assertEquals(0, ReelsTime.streak(null, today));
		assertEquals(1, ReelsTime.streak(Arrays.asList(today, today), today), "zwei Posts an einem Tag zählen einmal");
		assertEquals(3, ReelsTime.streak(Arrays.asList(today - 2, today - 1, today), today), "drei Tage in Folge");
		assertEquals(1, ReelsTime.streak(Arrays.asList(today - 3, today - 2, today), today), "eine Lücke setzt zurück");
		assertEquals(2, ReelsTime.streak(Arrays.asList(today - 2, today - 1), today),
				"heute noch kein Post: die Streak von gestern bleibt bis Mitternacht");
		assertEquals(0, ReelsTime.streak(Arrays.asList(today - 3, today - 2), today), "gestern verpasst: vorbei");
		assertEquals(1, ReelsTime.streak(Arrays.asList(today + 1, today), today), "ein Tag in der Zukunft stört nicht");
	}

	@Test
	void daysFollowTheTimeZoneOfThePlayer() {
		int plus2 = 2 * 3600 * 1000;
		long midnightUtc = 20000L * DAY;
		assertEquals(20000, ReelsTime.epochDay(midnightUtc, 0));
		assertEquals(19999, ReelsTime.epochDay(midnightUtc - 1, 0));
		// 23:30 UTC ist in UTC+2 schon der nächste Tag.
		assertEquals(20000, ReelsTime.epochDay(midnightUtc - 30 * 60 * 1000, plus2));
		assertEquals(19999, ReelsTime.epochDay(midnightUtc + 30 * 60 * 1000, -plus2));
		assertEquals(-1, ReelsTime.epochDay(-1, 0), "vor 1970 wird abgerundet, nicht zur Null hin");

		// Zwei Posts um Mitternacht (Berlin, Winterzeit UTC+1): 23:50 und 00:10 Ortszeit sind zwei Tage in Folge.
		TimeZone berlin = TimeZone.getTimeZone("Europe/Berlin");
		LocalReelsBackend a = new LocalReelsBackend(null, berlin);
		long localMidnight = NOON + 11 * 3600 * 1000; // 2026-11-14 00:00 in Berlin
		a.post(photo(1), 1, 1, false, false, "", "", localMidnight - 10 * 60 * 1000);
		assertEquals(1, a.streak(localMidnight - 5 * 60 * 1000));
		assertEquals(1, a.streak(localMidnight + 5 * 60 * 1000), "nach Mitternacht zählt der Post von gestern weiter");
		a.post(photo(2), 1, 1, false, false, "", "", localMidnight + 10 * 60 * 1000);
		assertEquals(2, a.streak(localMidnight + 15 * 60 * 1000));
		assertEquals(2, a.streak(localMidnight + DAY + 15 * 60 * 1000), "am nächsten Tag noch offen");
		assertEquals(0, a.streak(localMidnight + 2 * DAY + 15 * 60 * 1000), "ein ganzer Tag ohne Post: vorbei");
		// In UTC wären beide Posts am selben Tag gewesen.
		LocalReelsBackend utc = new LocalReelsBackend(null, UTC);
		utc.post(photo(1), 1, 1, false, false, "", "", localMidnight - 10 * 60 * 1000);
		utc.post(photo(2), 1, 1, false, false, "", "", localMidnight + 10 * 60 * 1000);
		assertEquals(1, utc.streak(localMidnight + 15 * 60 * 1000));
	}

	@Test
	void streakSurvivesReload() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		a.post(photo(1), 1, 1, false, false, "", "", NOON - 2 * DAY);
		a.post(photo(2), 1, 1, false, false, "", "", NOON - DAY);
		a.post(photo(3), 1, 1, false, false, "", "", NOON);
		a.flush();
		assertEquals(3, new LocalReelsBackend(tmp, UTC).streak(NOON));
	}

	@Test
	void ageOfAPost() {
		assertArrayEquals(new String[]{"reels.ago.now", "0"}, ReelsTime.ago(NOON, NOON + 59999));
		assertArrayEquals(new String[]{"reels.ago.min", "1"}, ReelsTime.ago(NOON, NOON + 60000));
		assertArrayEquals(new String[]{"reels.ago.min", "59"}, ReelsTime.ago(NOON, NOON + 3599999));
		assertArrayEquals(new String[]{"reels.ago.hours", "2"}, ReelsTime.ago(NOON, NOON + 2 * 3600000 + 5));
		assertArrayEquals(new String[]{"reels.ago.days", "3"}, ReelsTime.ago(NOON, NOON + 3 * DAY + 5));
		assertArrayEquals(new String[]{"reels.ago.now", "0"}, ReelsTime.ago(NOON + 5000, NOON), "Uhr verstellt: gerade eben");
	}

	// --- Blättern ---

	@Test
	void pagerClampsAndCollectsHalfNotches() {
		FeedPager p = new FeedPager();
		assertFalse(p.step(1), "leerer Feed: nichts zu blättern");
		assertFalse(p.wheel(-3));
		p.size(3);
		assertEquals(0, p.index());
		assertFalse(p.wheel(-0.5), "eine halbe Raste blättert noch nicht");
		assertTrue(p.wheel(-0.5), "zwei halbe Rasten = nächster Post");
		assertEquals(1, p.index());
		assertTrue(p.wheel(-1));
		assertEquals(2, p.index());
		assertFalse(p.wheel(-1), "am Ende ist Schluss");
		assertFalse(p.wheel(-5));
		assertEquals(2, p.index());
		assertTrue(p.wheel(1), "am Ende wird nichts angespart: eine Raste zurück genügt");
		assertEquals(1, p.index());
		assertTrue(p.wheel(2.5), "mehrere Rasten auf einmal, am Anfang geklemmt");
		assertEquals(0, p.index());
		assertFalse(p.step(-1));
		assertTrue(p.step(5));
		assertEquals(2, p.index());
		p.size(2);
		assertEquals(1, p.index(), "weniger Posts: die Seite rutscht mit");
		p.size(0);
		assertEquals(0, p.index());
		p.size(4);
		p.step(3);
		p.first();
		assertEquals(0, p.index());
		assertFalse(p.wheel(Double.NaN));
		// Direkt zu einer Seite (nach dem Posten zum neuen Post), außerhalb des Feeds zur nächstgelegenen.
		p.to(2);
		assertEquals(2, p.index());
		p.to(9);
		assertEquals(3, p.index());
		p.to(-4);
		assertEquals(0, p.index());
		p.wheel(-0.5);
		p.to(1);
		assertFalse(p.wheel(-0.5), "angesparte halbe Rasten verfallen dabei");
		assertEquals(1, p.index());
	}

	// --- Foto ---

	@Test
	void cropsToTheAspectOfThePhone() {
		assertArrayEquals(new int[]{16, 9}, PhotoFrame.aspect(PhoneHold.LANDSCAPE));
		assertArrayEquals(new int[]{9, 16}, PhotoFrame.aspect(PhoneHold.PORTRAIT));
		assertArrayEquals(new int[]{0, 0, 1920, 1080}, PhotoFrame.cropRect(1920, 1080, 16, 9), "16:9 aus 16:9: das ganze Bild");
		assertArrayEquals(new int[]{656, 0, 608, 1080}, PhotoFrame.cropRect(1920, 1080, 9, 16), "hochkant: mittiger Streifen");
		assertArrayEquals(new int[]{0, 60, 1920, 1080}, PhotoFrame.cropRect(1920, 1200, 16, 9), "16:10: oben und unten weg");
		assertArrayEquals(new int[]{440, 0, 2560, 1440}, PhotoFrame.cropRect(3440, 1440, 16, 9), "21:9: Seiten weg");
		assertArrayEquals(new int[]{0, 0, 1, 1}, PhotoFrame.cropRect(1, 1, 9, 16));
	}

	@Test
	void outputIsScaledDownButNeverUp() {
		assertArrayEquals(new int[]{1280, 720}, PhotoFrame.outSize(1920, 1080, 1280));
		assertArrayEquals(new int[]{608, 1080}, PhotoFrame.outSize(608, 1080, 1280), "kleiner als die Grenze: bleibt");
		assertArrayEquals(new int[]{720, 1280}, PhotoFrame.outSize(1215, 2160, 1280));
		assertArrayEquals(new int[]{854, 480}, PhotoFrame.outSize(854, 480, 1280), "nie vergrößern");
		assertArrayEquals(new int[]{1280, 1280}, PhotoFrame.outSize(1280, 1280, 1280));
		assertArrayEquals(new int[]{512, 1}, PhotoFrame.outSize(5000, 3, 512));
	}

	@Test
	void renderAveragesAreasAndForcesOpaque() {
		// 4×2: links rot/blau gemischt, rechts grün – auf 2×1 gemittelt.
		int[] src = {
				0x00FF0000, 0x000000FF, 0x0000FF00, 0x0000FF00,
				0x000000FF, 0x00FF0000, 0x0000FF00, 0x0000FF00};
		int[] out = PhotoFrame.render(src, 4, 2, new int[]{0, 0, 4, 2}, 2, 1);
		assertArrayEquals(new int[]{0xFF7F007F, 0xFF00FF00}, out);
		// Ausschnitt ohne Skalierung: nur die rechte Hälfte.
		assertArrayEquals(new int[]{0xFF00FF00, 0xFF00FF00, 0xFF00FF00, 0xFF00FF00},
				PhotoFrame.render(src, 4, 2, new int[]{2, 0, 2, 2}, 2, 2));
		// Ausschnitt mit Versatz und Skalierung.
		assertArrayEquals(new int[]{0xFF007F7F}, PhotoFrame.render(src, 4, 2, new int[]{1, 0, 2, 1}, 1, 1));
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				PhotoFrame.render(new int[3], 2, 2, new int[]{0, 0, 2, 2}, 1, 1);
			}
		});
	}

	@Test
	void viewfinderReadbackIsNeverSmallerThanItsPlaceOnScreen() {
		// 1080p hochkant: der Sucher ist 384×806 Fensterpixel groß – nur das volle Bild reicht (Ausschnitt 517×1080).
		assertEquals(1, PhotoFrame.readbackFactor(1920, 1080, 404, 848, 384, 806));
		// 1080p quer: Sucher 1048×509 – auch hier das volle Bild (halbiert wären es nur 960 Pixel Breite).
		assertEquals(1, PhotoFrame.readbackFactor(1920, 1080, 832, 404, 1048, 509));
		// 4K mit einem Sucher von 1048×509: ein Drittel (1280×720) reicht, ein Viertel (960 breit) nicht.
		assertEquals(3, PhotoFrame.readbackFactor(3840, 2160, 832, 404, 1048, 509));
		// 4K im Vollbild (Sucher 2097×1018): nur das volle Bild.
		assertEquals(1, PhotoFrame.readbackFactor(3840, 2160, 832, 404, 2097, 1018));
		// Kleiner Sucher: vierteln.
		assertEquals(4, PhotoFrame.readbackFactor(1920, 1080, 832, 404, 400, 194));
		// Seiten nicht teilbar: der nächste passende Faktor.
		assertEquals(3, PhotoFrame.readbackFactor(1917, 1080, 16, 9, 100, 50));
		assertEquals(1, PhotoFrame.readbackFactor(1919, 1079, 16, 9, 10, 10));
		assertEquals(1, PhotoFrame.readbackFactor(0, 0, 16, 9, 10, 10));
	}

	@Test
	void viewfinderTextureIsAsLargeAsTheViewfinderButNeverLargerThanItsSource() {
		// Ausschnitt größer als der Sucher: genau die Größe des Suchers.
		assertArrayEquals(new int[]{384, 806}, PhotoFrame.viewSize(515, 1080, 384, 806));
		assertArrayEquals(new int[]{1048, 509}, PhotoFrame.viewSize(1920, 932, 1048, 509));
		// Ausschnitt kleiner (kleines Fenster, verkleinert ausgelesen): so groß wie der Ausschnitt – nie vergrößern.
		assertArrayEquals(new int[]{480, 233}, PhotoFrame.viewSize(480, 233, 1048, 509));
		assertArrayEquals(new int[]{129, 270}, PhotoFrame.viewSize(129, 270, 384, 806));
		// Riesiger Sucher: höchstens MAX_VIEW_SIDE an der langen Seite, Seitenverhältnis bleibt.
		int[] big = PhotoFrame.viewSize(4192, 2036, 4192, 2036);
		assertEquals(PhotoFrame.MAX_VIEW_SIDE, big[0]);
		assertEquals(Math.round(2036 * (PhotoFrame.MAX_VIEW_SIDE / 4192.0)), big[1]);
		// Unsinnige Angaben werfen nicht.
		assertArrayEquals(new int[]{1, 1}, PhotoFrame.viewSize(0, 0, 0, 0));
		assertArrayEquals(new int[]{100, 50}, PhotoFrame.viewSize(100, 50, 0, 0));
	}

	@Test
	void streakAfterAPostCountsTodayOnceAndChangesNothing() {
		LocalReelsBackend b = new LocalReelsBackend(tmp, UTC);
		assertEquals(0, b.streak(NOON));
		assertEquals(1, b.streakAfterPost(NOON), "der erste Post überhaupt startet die Streak");
		assertEquals(0, b.streak(NOON), "die Frage allein ändert nichts");
		assertNotNull(b.post(ReelsPost.newId(NOON - DAY, UTC, 1) + ".png", 16, 9, false, false, "gestern", "A", NOON - DAY));
		assertEquals(1, b.streak(NOON), "gestern gepostet: die Streak läuft noch");
		assertEquals(2, b.streakAfterPost(NOON), "ein Post heute verlängert sie");
		assertNotNull(b.post(ReelsPost.newId(NOON, UTC, 2) + ".png", 16, 9, false, false, "heute", "A", NOON));
		assertEquals(2, b.streak(NOON));
		assertEquals(2, b.streakAfterPost(NOON), "ein zweiter Post am selben Tag ändert nichts");
		assertEquals(1, b.streakAfterPost(NOON + 3 * DAY), "nach einer Lücke beginnt sie wieder bei eins");
		assertEquals(2, b.feed().size());
	}

	// --- Dienst: Foto speichern, posten, verwerfen ---

	private static int[] testFrame(int w, int h) {
		int[] argb = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) argb[y * w + x] = x < w / 2 ? 0x00205080 : 0x00E0C020;
		}
		return argb;
	}

	@Test
	void savePhotoWritesACroppedPngAndAPreview() throws IOException {
		LocalReelsBackend backend = new LocalReelsBackend(tmp, UTC);
		Reels reels = Reels.forTest(new TrsModules(), backend, DIRECT, UTC);
		assertSame(reels, Reels.get());
		assertNull(reels.pollShot());

		final List<Reels.Shot> seen = new ArrayList<Reels.Shot>();
		reels.savePhoto(testFrame(1920, 1080), 1920, 1080, PhoneHold.PORTRAIT, true, new java.util.function.Consumer<Reels.Shot>() {
			@Override
			public void accept(Reels.Shot shot) {
				seen.add(shot);
			}
		});
		Reels.Shot shot = reels.pollShot();
		assertNotNull(shot);
		assertSame(shot, seen.get(0));
		assertNull(reels.pollShot(), "jedes Foto kommt genau einmal");
		assertFalse(shot.failed);
		assertEquals(608, shot.width, "hochkant aus 1920×1080: 608×1080, nicht vergrößert");
		assertEquals(1080, shot.height);
		assertTrue(shot.portrait);
		assertTrue(shot.selfie);
		assertTrue(ReelsPost.validPhotoName(shot.fileName), shot.fileName);
		assertEquals(tmp.resolve("photos").resolve(shot.fileName), shot.file);
		assertEquals(576, shot.previewW, "Vorschau: höchstens 1024 an der langen Seite");
		assertEquals(1024, shot.previewH);
		assertEquals(576 * 1024, shot.preview.length);
		assertEquals(0xFF205080, shot.preview[0]);
		assertEquals(0xFFE0C020, shot.preview[575]);

		BufferedImage png = javax.imageio.ImageIO.read(shot.file.toFile());
		assertEquals(608, png.getWidth());
		assertEquals(1080, png.getHeight());
		assertEquals(0xFF205080, png.getRGB(0, 0), "linke Hälfte des Ausschnitts, deckend");
		assertEquals(0xFFE0C020, png.getRGB(607, 1079));
		assertFalse(Files.exists(shot.file.resolveSibling(shot.fileName + ".tmp")));

		// Quer: 1280×720.
		reels.savePhoto(testFrame(1920, 1080), 1920, 1080, PhoneHold.LANDSCAPE, false, null);
		Reels.Shot wide = reels.pollShot();
		assertEquals(1280, wide.width);
		assertEquals(720, wide.height);
		assertFalse(wide.portrait);
		assertNotEquals(shot.fileName, wide.fileName, "zwei Fotos in derselben Sekunde bekommen verschiedene Namen");
		assertEquals(1024, wide.previewW);
		assertEquals(576, wide.previewH);
	}

	@Test
	void postingKeepsThePhotoAndDiscardingDeletesIt() throws IOException {
		LocalReelsBackend backend = new LocalReelsBackend(tmp, UTC);
		Reels reels = Reels.forTest(new TrsModules(), backend, DIRECT, UTC);
		reels.savePhoto(testFrame(320, 180), 320, 180, PhoneHold.LANDSCAPE, false, null);
		Reels.Shot first = reels.pollShot();
		reels.savePhoto(testFrame(320, 180), 320, 180, PhoneHold.LANDSCAPE, false, null);
		Reels.Shot second = reels.pollShot();
		assertTrue(Files.isRegularFile(first.file));
		assertTrue(Files.isRegularFile(second.file));

		// „Nochmal“: das erste Foto wird verworfen.
		reels.discard(first);
		assertFalse(Files.exists(first.file));

		ReelsPost post = reels.post(second, "Aussicht hinter meiner Basis", "PixelDEV_CAM", NOON);
		assertNotNull(post);
		assertEquals(320, post.width);
		assertEquals(180, post.height);
		assertEquals(second.file, backend.photoFile(post));
		assertFalse(backend.dirty(), "nach dem Posten wird gleich gesichert");
		assertTrue(Files.isRegularFile(tmp.resolve("feed.json")));
		reels.discard(second);
		assertTrue(Files.isRegularFile(second.file), "ein gepostetes Foto wird nie verworfen");

		assertTrue(reels.toggleLike(post.id));
		assertNotNull(reels.comment(post.id, "Bea", "schön!", NOON + 5));
		assertFalse(backend.dirty());
		LocalReelsBackend again = new LocalReelsBackend(tmp, UTC);
		assertEquals(1, again.feed().get(0).likes);
		assertEquals("schön!", again.feed().get(0).comments.get(0).text);
		assertEquals(1, again.streak(NOON));

		reels.discard(null);
		assertNull(reels.post(null, "", "", NOON));
	}

	@Test
	void aPhotoThatCannotBeSavedComesBackAsFailed() throws IOException {
		// Der Foto-Ordner ist eine Datei: Schreiben muss scheitern, ohne dass etwas wirft.
		Files.write(tmp.resolve("photos"), new byte[]{1});
		final List<String> log = new ArrayList<String>();
		Reels reels = new Reels(new TrsModules(), new LocalReelsBackend(tmp, UTC), new java.util.function.Consumer<String>() {
			@Override
			public void accept(String s) {
				log.add(s);
			}
		}, DIRECT, UTC);
		reels.savePhoto(testFrame(32, 18), 32, 18, PhoneHold.LANDSCAPE, false, null);
		Reels.Shot shot = reels.pollShot();
		assertNotNull(shot);
		assertTrue(shot.failed);
		assertNull(shot.file);
		assertEquals(1, log.size(), "der Fehler wird gemeldet");
		assertNull(reels.post(shot, "", "", NOON), "ein fehlgeschlagenes Foto lässt sich nicht posten");
		reels.discard(shot);

		// Kaputte Bilddaten ebenso.
		reels.savePhoto(new int[4], 1920, 1080, PhoneHold.LANDSCAPE, false, null);
		assertTrue(reels.pollShot().failed);
	}

	@Test
	void realWorkerThreadDeliversTheShot() throws Exception {
		Reels reels = Reels.forTest(new TrsModules(), new LocalReelsBackend(tmp, UTC), null, UTC);
		final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
		final String[] thread = new String[1];
		reels.savePhoto(testFrame(64, 36), 64, 36, PhoneHold.LANDSCAPE, false, new java.util.function.Consumer<Reels.Shot>() {
			@Override
			public void accept(Reels.Shot shot) {
				thread[0] = Thread.currentThread().getName();
				done.countDown();
			}
		});
		assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
		assertEquals("TRS-Reels", thread[0], "Bilder werden nicht im Spiel-Thread gerechnet");
		Reels.Shot shot = reels.pollShot();
		assertNotNull(shot);
		assertFalse(shot.failed);
		assertSame(reels.worker(), reels.worker());
	}

	// --- Fotos, auf die niemand mehr wartet ---

	/** Hintergrund-Thread von Hand: Aufträge sammeln und einzeln ausführen. */
	private static final class Manual implements Executor {
		final java.util.ArrayDeque<Runnable> jobs = new java.util.ArrayDeque<Runnable>();

		@Override
		public void execute(Runnable r) {
			jobs.add(r);
		}
	}

	private List<String> photoFiles() {
		List<String> names = new ArrayList<String>();
		java.io.File[] files = tmp.resolve("photos").toFile().listFiles();
		if (files != null) for (java.io.File f : files) names.add(f.getName());
		return names;
	}

	@Test
	void abandonedPhotoIsDeletedByTheWorkerInsteadOfBeingLeftOnDisk() {
		Manual worker = new Manual();
		Reels reels = Reels.forTest(new TrsModules(), new LocalReelsBackend(tmp, UTC), worker, UTC);
		long first = reels.savePhoto(testFrame(64, 36), 64, 36, PhoneHold.LANDSCAPE, false, null);
		assertTrue(first > 0);
		reels.abandon(first);
		worker.jobs.poll().run();
		assertNull(reels.pollShot(), "auf das Foto wartet niemand: es kommt nicht mehr an");
		assertTrue(photoFiles().isEmpty(), "und die Datei ist wieder weg: " + photoFiles());

		// Ein neueres Foto verdrängt ein älteres, das noch gespeichert wird; „abandon“ des alten lässt das neue in Ruhe.
		long old = reels.savePhoto(testFrame(64, 36), 64, 36, PhoneHold.LANDSCAPE, false, null);
		long fresh = reels.savePhoto(testFrame(32, 18), 32, 18, PhoneHold.LANDSCAPE, true, null);
		assertNotEquals(old, fresh, "jedes Auslösen hat seine eigene Nummer");
		reels.abandon(old);
		worker.jobs.poll().run();
		assertNull(reels.pollShot());
		assertTrue(photoFiles().isEmpty());
		worker.jobs.poll().run();
		Reels.Shot shot = reels.pollShot();
		assertNotNull(shot);
		assertEquals(fresh, shot.ticket);
		assertEquals(32, shot.width);
		assertEquals(1, photoFiles().size());

		// Ein fehlgeschlagenes Foto trägt die Nummer seines Auslösens (damit die Kamera die Meldung zuordnen kann).
		long broken = reels.savePhoto(new int[4], 1920, 1080, PhoneHold.LANDSCAPE, false, null);
		worker.jobs.poll().run();
		Reels.Shot failed = reels.pollShot();
		assertTrue(failed.failed);
		assertEquals(broken, failed.ticket);
	}

	@Test
	void stoppingTheGameLeavesNoPhotoWithoutAPostAndSavesTheFeed() throws IOException {
		LocalReelsBackend backend = new LocalReelsBackend(tmp, UTC);
		Manual worker = new Manual();
		Reels reels = Reels.forTest(new TrsModules(), backend, worker, UTC);
		// Ein gepostetes Foto, eines fertig in der Warteschlange, eines noch beim Speichern.
		reels.savePhoto(testFrame(64, 36), 64, 36, PhoneHold.LANDSCAPE, false, null);
		worker.jobs.poll().run();
		Reels.Shot posted = reels.pollShot();
		assertNotNull(reels.post(posted, "bleibt", "A", NOON));
		reels.savePhoto(testFrame(64, 36), 64, 36, PhoneHold.LANDSCAPE, false, null);
		worker.jobs.pollLast().run();
		reels.savePhoto(testFrame(64, 36), 64, 36, PhoneHold.LANDSCAPE, false, null);
		Runnable writing = worker.jobs.pollLast();
		assertEquals(2, photoFiles().size(), "das gepostete und das fertige, noch nicht abgeholte: " + photoFiles());

		reels.stop(0);
		assertNull(reels.pollShot());
		assertEquals(Collections.singletonList(posted.fileName), photoFiles(), "nur das gepostete Foto bleibt");
		assertEquals(1, new LocalReelsBackend(tmp, UTC).feed().size(), "der Feed ist gesichert");
		// Was danach noch fertig wird, räumt der Hintergrund-Thread selbst weg.
		writing.run();
		assertNull(reels.pollShot());
		assertEquals(Collections.singletonList(posted.fileName), photoFiles());
	}

	@Test
	void stopWaitsForTheRealWorkerSoAHalfWrittenPhotoIsGone() {
		Reels reels = Reels.forTest(new TrsModules(), new LocalReelsBackend(tmp, UTC), null, UTC);
		reels.savePhoto(testFrame(640, 360), 640, 360, PhoneHold.LANDSCAPE, false, null);
		reels.stop(10000);
		assertNull(reels.pollShot());
		assertTrue(photoFiles().isEmpty(), "beim Beenden bleibt kein Foto ohne Post: " + photoFiles());
	}

	// --- Sichern ---

	@Test
	void postCountsForOneDayOnlyAfterTheTimeZoneChanged() throws IOException {
		// 00:30 in Berlin am 14.11. ist 23:30 UTC am 13.11.
		TimeZone berlin = TimeZone.getTimeZone("Europe/Berlin");
		long halfPastMidnight = NOON + 11 * 3600000L + 30 * 60000L;
		LocalReelsBackend a = new LocalReelsBackend(tmp, berlin);
		assertNotNull(a.post(photo(1), 1280, 720, false, false, "", "A", halfPastMidnight));
		assertEquals(1, a.streak(halfPastMidnight + 60000L));
		a.flush();
		assertFalse(new String(Files.readAllBytes(tmp.resolve("feed.json")), StandardCharsets.UTF_8).contains("postDays"),
				"die Tage stehen nicht in der Datei – sie ergeben sich aus den Posts");

		// Derselbe Feed, der PC steht jetzt auf UTC: ein Post, ein Tag – nicht zwei.
		LocalReelsBackend b = new LocalReelsBackend(tmp, UTC);
		assertEquals(1, b.streak(halfPastMidnight + 60000L));
		assertEquals(1, b.streak(NOON + DAY), "am Tag danach (UTC) läuft sie noch");
		assertEquals(0, b.streak(NOON + 2 * DAY));

		// Eine ältere Datei mit gespeicherten Tagen aus einer anderen Zeitzone: sie zählen nicht mehr mit.
		String json = new String(Files.readAllBytes(tmp.resolve("feed.json")), StandardCharsets.UTF_8);
		long day = halfPastMidnight / DAY;
		json = json.substring(0, json.lastIndexOf('}')) + ",\n  \"postDays\": [" + day + ", " + (day + 1) + "]\n}";
		Files.write(tmp.resolve("feed.json"), json.getBytes(StandardCharsets.UTF_8));
		LocalReelsBackend c = new LocalReelsBackend(tmp, UTC);
		assertEquals(1, c.feed().size());
		assertEquals(1, c.streak(halfPastMidnight + 60000L), "ein Post zählt für genau einen Tag");
	}

	@Test
	void failedWriteKeepsTheChangeForTheNextTry() throws IOException {
		// „feed.json“ ist ein Ordner: Schreiben schlägt fehl.
		Path dir = tmp.resolve("kaputt");
		Files.createDirectories(dir.resolve("feed.json").resolve("x"));
		LocalReelsBackend a = new LocalReelsBackend(dir, UTC);
		a.post(photo(1), 1280, 720, false, false, "bleibt", "A", NOON);
		assertThrows(IOException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws Throwable {
				a.flush();
			}
		});
		assertTrue(a.dirty(), "nicht geschrieben: die Änderung gilt weiter als ungesichert");
		Files.delete(dir.resolve("feed.json").resolve("x"));
		Files.delete(dir.resolve("feed.json"));
		a.flush();
		assertFalse(a.dirty());
		assertEquals("bleibt", new LocalReelsBackend(dir, UTC).feed().get(0).caption);
	}

	@Test
	void feedNeverWaitsForTheDiskWhileTheFeedIsBeingWritten() throws Exception {
		final LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		for (int i = 0; i < 300; i++) {
			ReelsPost p = a.post(photo(i + 1), 1280, 720, false, false, "Post " + i, "A", NOON - i * 1000L);
			for (int k = 0; k < 20; k++) a.comment(p.id, "B", "Kommentar " + k, NOON);
		}
		// Ein fremder Thread hält die Sperre des Feeds nicht, während geschrieben wird: feed() und Likes laufen durch.
		final java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
		final java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		Thread writer = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!stop.get()) {
						a.flush();
					}
				} catch (Throwable t) {
					error.set(t);
				}
			}
		}, "test-flush");
		writer.start();
		String id = a.feed().get(0).id;
		for (int i = 0; i < 200; i++) {
			assertEquals(300, a.feed().size());
			a.toggleLike(id);
		}
		stop.set(true);
		writer.join(20000);
		assertNull(error.get(), "Sichern neben Änderungen: " + error.get());
		a.flush();
		assertFalse(a.dirty());
		LocalReelsBackend b = new LocalReelsBackend(tmp, UTC);
		assertEquals(300, b.feed().size());
		assertEquals(a.feed().get(0).likes, b.feed().get(0).likes, "der letzte Stand steht auf der Platte");
		assertEquals(20, b.feed().get(0).comments.size());
	}

	// --- Tastensperre ---

	@Test
	void keyGateBlocksUntilTheKeyWasUpOnce() {
		PhoneKeyGate gate = new PhoneKeyGate();
		assertFalse(gate.shouldOpen(false, false));
		assertTrue(gate.shouldOpen(true, true), "ohne Sperre öffnet der Tastendruck");
		// Handy mit derselben Taste weggesteckt, Taste noch unten.
		gate.block();
		assertTrue(gate.blocked());
		assertFalse(gate.shouldOpen(true, true), "noch gehaltene Taste öffnet nicht sofort wieder");
		assertFalse(gate.shouldOpen(false, true));
		assertFalse(gate.shouldOpen(false, false), "losgelassen: Sperre weg, aber noch kein Druck");
		assertFalse(gate.blocked());
		assertTrue(gate.shouldOpen(true, true), "neuer Druck öffnet");
	}

	@Test
	void idsHaveAFixedForm() {
		assertEquals("20261113-120000-00ff", ReelsPost.newId(NOON, UTC, 255));
		assertEquals("20261113-130000-ffff", ReelsPost.newId(NOON, TimeZone.getTimeZone("Europe/Berlin"), -1));
		assertTrue(ReelsPost.validId("20261113-120000-00ff"));
		assertFalse(ReelsPost.validId("20261113-120000-00FF"));
		assertFalse(ReelsPost.validId("20261113_120000-00ff"));
		assertFalse(ReelsPost.validId("2026111-3120000-00ff"));
		assertFalse(ReelsPost.validId(""));
		assertFalse(ReelsPost.validId(null));
		LocalReelsBackend a = new LocalReelsBackend(null, UTC);
		assertNotEquals(a.newId(NOON), a.newId(NOON));
	}

	// --- Kommentare: IDs, Antworten, Bearbeiten, gesehen ---

	/** Eine feed.json, wie sie die erste Fassung von MC-Reels geschrieben hat: Kommentare ohne ID, nichts Neueres. */
	private static String oldFeed() {
		return "{\"version\":1,\"posts\":[{\"id\":\"20261113-120000-0001\",\"author\":\"PixelCST\",\"caption\":\"alt\","
				+ "\"photo\":\"20261113-120000-0001.png\",\"width\":1280,\"height\":720,\"selfie\":false,\"portrait\":false,"
				+ "\"likedByMe\":false,\"likes\":0,\"createdAt\":" + NOON + ",\"comments\":["
				+ "{\"author\":\"Steve_Baut\",\"text\":\"Stark!\",\"at\":" + (NOON + 1000) + "},"
				+ "{\"author\":\"PixelCST\",\"text\":\"Danke\",\"at\":" + (NOON + 1000) + "},"
				+ "{\"author\":\"Bea\",\"text\":\"wo ist das?\",\"at\":" + (NOON + 2000) + "}]}]}";
	}

	private String feedJson() throws IOException {
		return new String(Files.readAllBytes(tmp.resolve("feed.json")), StandardCharsets.UTF_8);
	}

	private static List<String> commentIds(LocalReelsBackend b) {
		List<String> ids = new ArrayList<String>();
		for (ReelsComment c : b.feed().get(0).comments) ids.add(c.id);
		return ids;
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) n++;
		return n;
	}

	@Test
	void oldFeedLoadsCleanAndGetsOnlyCommentIdsOnTheNextWrite() throws IOException {
		Files.write(tmp.resolve("feed.json"), oldFeed().getBytes(StandardCharsets.UTF_8));
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		assertFalse(a.dirty(), "Laden allein ändert nichts");
		a.flush();
		assertEquals(oldFeed(), feedJson(), "ohne Änderung bleibt die alte Datei Byte für Byte liegen");
		ReelsPost p = a.feed().get(0);
		assertNull(p.meta);
		assertEquals(3, p.comments.size());
		List<String> first = commentIds(a);
		for (ReelsComment c : p.comments) {
			assertTrue(ReelsPost.validId(c.id), "jeder alte Kommentar hat nach dem Laden eine ID: " + c.id);
			assertNull(c.replyTo);
			assertNull(c.editedAt);
			assertNull(c.seen);
		}
		assertEquals(3, new java.util.HashSet<String>(first).size(), "eindeutig innerhalb des Posts");

		a.toggleLike(p.id);
		a.flush();
		String json = feedJson();
		for (String key : Arrays.asList("meta", "freezes", "clockDay", "replyTo", "editedAt", "seen")) {
			assertFalse(json.contains("\"" + key + "\""), "kein neues Feld ohne Anlass: " + key);
		}
		assertEquals(4, count(json, "\"id\""), "der Post und jeder der drei Kommentare hat eine ID");
		for (String id : first) assertTrue(json.contains("\"" + id + "\""), id);

		LocalReelsBackend b = new LocalReelsBackend(tmp, UTC);
		assertEquals(first, commentIds(b), "die geschriebenen IDs sind die vom ersten Laden");
		assertEquals("Stark!", b.feed().get(0).comments.get(0).text);
		assertTrue(b.feed().get(0).likedByMe);
	}

	@Test
	void sameOldFileGivesTheSameIdsOnEveryLoad() throws IOException {
		Files.write(tmp.resolve("feed.json"), oldFeed().getBytes(StandardCharsets.UTF_8));
		List<String> first = commentIds(new LocalReelsBackend(tmp, UTC));
		// Aus Zeit (UTC) und Stelle – nicht aus Zufall und nicht aus der Zeitzone des Spielers.
		assertEquals(Arrays.asList("20261113-120001-0000", "20261113-120001-0001", "20261113-120002-0002"), first);
		assertEquals(first, commentIds(new LocalReelsBackend(tmp, UTC)));
		assertEquals(first, commentIds(new LocalReelsBackend(tmp, TimeZone.getTimeZone("Asia/Tokyo"))));
		assertEquals(oldFeed(), feedJson(), "nur gelesen");

		// Unbrauchbare Zeiten, eine doppelte und eine ungültige ID: trotzdem lauter gültige, verschiedene IDs.
		Path other = tmp.resolve("schräg");
		Files.createDirectories(other);
		String json = "{\"version\":1,\"posts\":[{\"id\":\"20261113-120000-0001\",\"photo\":\"20261113-120000-0001.png\","
				+ "\"createdAt\":" + NOON + ",\"comments\":["
				+ "{\"id\":\"19700101-000000-0001\",\"author\":\"A\",\"text\":\"a\",\"at\":0},"
				+ "{\"id\":\"19700101-000000-0001\",\"author\":\"B\",\"text\":\"b\",\"at\":0},"
				+ "{\"id\":\"../x\",\"author\":\"C\",\"text\":\"c\",\"at\":" + Long.MAX_VALUE + "},"
				+ "{\"author\":\"D\",\"text\":\"d\",\"at\":" + Long.MIN_VALUE + "},"
				+ "{\"author\":\"E\",\"text\":\"e\",\"at\":0}]}]}";
		Files.write(other.resolve("feed.json"), json.getBytes(StandardCharsets.UTF_8));
		List<String> odd = commentIds(new LocalReelsBackend(other, UTC));
		assertEquals(5, odd.size());
		assertEquals(5, new java.util.HashSet<String>(odd).size(), odd.toString());
		for (String id : odd) assertTrue(ReelsPost.validId(id), id);
		assertEquals("19700101-000000-0001", odd.get(0), "die erste gültige ID bleibt");
		assertEquals(odd, commentIds(new LocalReelsBackend(other, UTC)));
	}

	@Test
	void idsRepliesEditsAndSeenSurviveReload() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "", "PixelCST", NOON);
		ReelsComment own = a.comment(p.id, "PixelCST", "erster", NOON + 1000);
		ReelsComment other = a.comment(p.id, "Steve_Baut", "Stark! @PixelCST wo ist das?", NOON + 2000);
		ReelsComment reply = a.reply(p.id, own.id, "Steve_Baut", "Antwort", NOON + 3000);
		assertTrue(ReelsPost.validId(own.id), own.id);
		assertNotEquals(own.id, other.id);
		assertNotEquals(other.id, reply.id);
		assertEquals(own.id, reply.replyTo);
		assertNull(own.replyTo);
		assertNotNull(a.editComment(p.id, own.id, "PixelCST", "erster, verbessert", NOON + 4000));
		assertEquals(2, a.markPingsSeen(p.id, "PixelCST"), "die Erwähnung und die Antwort auf den eigenen Kommentar");
		a.flush();
		String json = feedJson();
		assertTrue(json.contains("\"replyTo\"") && json.contains("\"editedAt\"") && json.contains("\"seen\""), json);

		List<ReelsComment> read = new LocalReelsBackend(tmp, UTC).feed().get(0).comments;
		assertEquals(3, read.size());
		assertEquals(own.id, read.get(0).id);
		assertEquals("erster, verbessert", read.get(0).text);
		assertEquals(Long.valueOf(NOON + 4000), read.get(0).editedAt);
		assertNull(read.get(0).seen, "ein eigener Kommentar ist nie ein Ping");
		assertEquals(other.id, read.get(1).id);
		assertEquals(Boolean.TRUE, read.get(1).seen);
		assertNull(read.get(1).editedAt);
		assertEquals(reply.id, read.get(2).id);
		assertEquals(own.id, read.get(2).replyTo);
		assertEquals(Boolean.TRUE, read.get(2).seen);
	}

	@Test
	void editOnlyByTheAuthorAndMarksEdited() {
		LocalReelsBackend a = new LocalReelsBackend(null, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "", "PixelCST", NOON);
		ReelsComment c = a.comment(p.id, "PixelCST", "Tipfehler", NOON + 1000);
		assertNull(c.editedAt);
		List<ReelsPost> before = a.feed();
		assertNull(a.editComment(p.id, c.id, "Steve_Baut", "gekapert", NOON + 2000), "nur der Verfasser");
		assertNull(a.editComment(p.id, c.id, "", "gekapert", NOON + 2000));
		assertNull(a.editComment(p.id, c.id, null, "gekapert", NOON + 2000));
		assertNull(a.editComment(p.id, "20000101-000000-0000", "PixelCST", "x", NOON + 2000), "unbekannter Kommentar");
		assertNull(a.editComment("20000101-000000-0000", c.id, "PixelCST", "x", NOON + 2000), "unbekannter Post");
		assertNull(a.editComment(p.id, null, "PixelCST", "x", NOON + 2000));
		assertSame(before, a.feed(), "nichts davon ändert den Feed");
		assertEquals("Tipfehler", c.text);

		ReelsComment edited = a.editComment(p.id, c.id, "pixelcst", "  Tippfehler\n ", NOON + 2000);
		assertSame(c, edited, "derselbe Kommentar, der Name zählt ohne Groß-/Kleinschreibung");
		assertEquals("Tippfehler", c.text, "gesäubert wie ein neuer Kommentar");
		assertEquals(Long.valueOf(NOON + 2000), c.editedAt);
		assertEquals(NOON + 1000, c.at, "der Zeitpunkt des Kommentars bleibt");
		assertNotSame(before, a.feed());
		assertTrue(a.dirty());
		assertEquals(1, p.comments.size());

		StringBuilder longText = new StringBuilder();
		for (int i = 0; i < 200; i++) longText.append('x');
		assertEquals(ReelsComment.MAX_TEXT, a.editComment(p.id, c.id, "PixelCST", longText.toString(), NOON + 3000).text.length());
	}

	@Test
	void unchangedEditIsNotMarked() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "", "PixelCST", NOON);
		ReelsComment c = a.comment(p.id, "PixelCST", "bleibt so", NOON + 1000);
		a.flush();
		List<ReelsPost> before = a.feed();
		assertSame(c, a.editComment(p.id, c.id, "PixelCST", "  bleibt so ", NOON + 2000), "der Kommentar kommt zurück");
		assertNull(c.editedAt, "unveränderter Text gilt nicht als bearbeitet");
		assertSame(before, a.feed());
		assertFalse(a.dirty());
	}

	@Test
	void emptyEditIsRejected() {
		LocalReelsBackend a = new LocalReelsBackend(null, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "", "PixelCST", NOON);
		ReelsComment c = a.comment(p.id, "PixelCST", "bleibt", NOON + 1000);
		assertNull(a.editComment(p.id, c.id, "PixelCST", "  \n ", NOON + 2000));
		assertNull(a.editComment(p.id, c.id, "PixelCST", null, NOON + 2000));
		assertEquals("bleibt", c.text);
		assertNull(c.editedAt);
	}

	@Test
	void replyToAReplyAttachesToItsParent() {
		LocalReelsBackend a = new LocalReelsBackend(null, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "", "PixelCST", NOON);
		ReelsComment top = a.comment(p.id, "PixelCST", "oben", NOON + 1000);
		ReelsComment reply = a.reply(p.id, top.id, "Steve_Baut", "Antwort", NOON + 2000);
		ReelsComment second = a.reply(p.id, reply.id, "Bea", "Antwort auf die Antwort", NOON + 3000);
		assertEquals(top.id, reply.replyTo);
		assertEquals(top.id, second.replyTo, "nur eine Ebene");
		assertEquals(3, p.comments.size());
		assertNull(a.reply(p.id, top.id, "Bea", "   ", NOON), "leere Antwort wird nicht gespeichert");
		assertNull(a.reply("20000101-000000-0000", top.id, "Bea", "hallo", NOON));
	}

	@Test
	void replyToAnUnknownParentBecomesTopLevel() {
		LocalReelsBackend a = new LocalReelsBackend(null, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "", "PixelCST", NOON);
		ReelsComment c = a.reply(p.id, "20000101-000000-0000", "Bea", "wohin?", NOON + 1000);
		assertNotNull(c);
		assertNull(c.replyTo);
		assertNull(a.reply(p.id, null, "Bea", "auch oben", NOON + 2000).replyTo);
		assertNull(a.reply(p.id, "../x", "Bea", "auch oben", NOON + 3000).replyTo);
		assertEquals(3, p.comments.size());
	}

	@Test
	void handEditedReplyChainsAreFlattenedOnLoad() throws IOException {
		String a = "20261113-120001-000a";
		String b = "20261113-120002-000b";
		String c = "20261113-120003-000c";
		String d = "20261113-120004-000d";
		String e = "20261113-120005-000e";
		String f = "20261113-120006-000f";
		String g = "20261113-120007-0010";
		String json = "{\"version\":1,\"posts\":[{\"id\":\"20261113-120000-0001\",\"photo\":\"20261113-120000-0001.png\","
				+ "\"createdAt\":" + NOON + ",\"comments\":["
				+ "{\"id\":\"" + a + "\",\"author\":\"A\",\"text\":\"oben\",\"at\":1},"
				+ "{\"id\":\"" + b + "\",\"author\":\"B\",\"text\":\"Antwort\",\"at\":2,\"replyTo\":\"" + a + "\"},"
				+ "{\"id\":\"" + c + "\",\"author\":\"C\",\"text\":\"Antwort auf Antwort\",\"at\":3,\"replyTo\":\"" + b + "\"},"
				+ "{\"id\":\"" + d + "\",\"author\":\"D\",\"text\":\"auf sich selbst\",\"at\":4,\"replyTo\":\"" + d + "\"},"
				+ "{\"id\":\"" + e + "\",\"author\":\"E\",\"text\":\"ins Leere\",\"at\":5,\"replyTo\":\"20000101-000000-0000\","
				+ "\"editedAt\":0,\"seen\":false},"
				+ "{\"id\":\"" + f + "\",\"author\":\"F\",\"text\":\"im Kreis 1\",\"at\":6,\"replyTo\":\"" + g + "\"},"
				+ "{\"id\":\"" + g + "\",\"author\":\"G\",\"text\":\"im Kreis 2\",\"at\":7,\"replyTo\":\"" + f + "\",\"editedAt\":-5,"
				+ "\"seen\":true}]}]}";
		Files.write(tmp.resolve("feed.json"), json.getBytes(StandardCharsets.UTF_8));
		LocalReelsBackend loaded = new LocalReelsBackend(tmp, UTC);
		assertFalse(loaded.dirty());
		List<ReelsComment> read = loaded.feed().get(0).comments;
		assertEquals(7, read.size());
		assertNull(read.get(0).replyTo);
		assertEquals(a, read.get(1).replyTo);
		assertEquals(a, read.get(2).replyTo, "die Antwort auf eine Antwort hängt am Kommentar der obersten Ebene");
		assertNull(read.get(3).replyTo, "auf sich selbst");
		assertNull(read.get(4).replyTo, "unbekannter Kommentar");
		assertNull(read.get(4).editedAt, "0 heißt: nie bearbeitet");
		assertNull(read.get(4).seen, "nur true wird gemerkt");
		assertNull(read.get(5).replyTo, "im Kreis");
		assertNull(read.get(6).replyTo, "im Kreis");
		assertNull(read.get(6).editedAt);
		assertEquals(Boolean.TRUE, read.get(6).seen);
		for (CommentThread.Row row : CommentThread.rows(read)) {
			assertEquals(row.comment == read.get(1) || row.comment == read.get(2), row.reply, row.comment.text);
		}
	}

	@Test
	void markPingsSeenIsStoredAndChangesNothingTheSecondTime() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "", "PixelCST", NOON);
		ReelsPost q = a.post(photo(2), 1280, 720, false, false, "", "PixelCST", NOON + 10);
		ReelsComment ping = a.comment(p.id, "Steve_Baut", "Stark! @pixelcst wo ist das?", NOON + 1000);
		ReelsComment plain = a.comment(p.id, "Steve_Baut", "ohne Namen", NOON + 2000);
		ReelsComment elsewhere = a.comment(q.id, "Bea", "@PixelCST schau", NOON + 3000);
		assertEquals(2, Pings.unseen(a.feed(), "PixelCST"));
		a.flush();
		List<ReelsPost> before = a.feed();
		assertEquals(0, a.markPingsSeen("20000101-000000-0000", "PixelCST"));
		assertEquals(0, a.markPingsSeen(p.id, "Niemand"));
		assertEquals(0, a.markPingsSeen(p.id, null));
		assertSame(before, a.feed());
		assertFalse(a.dirty());

		assertEquals(1, a.markPingsSeen(p.id, "PixelCST"));
		assertEquals(Boolean.TRUE, ping.seen);
		assertNull(plain.seen, "nur was den Spieler betrifft, wird gemerkt");
		assertNull(elsewhere.seen, "nur der Post, dessen Kommentare offen sind");
		assertNotSame(before, a.feed());
		assertTrue(a.dirty());
		a.flush();
		assertEquals(1, count(feedJson(), "\"seen\": true"));

		before = a.feed();
		assertEquals(0, a.markPingsSeen(p.id, "PixelCST"), "beim zweiten Mal ist nichts mehr zu merken");
		assertSame(before, a.feed());
		assertFalse(a.dirty());
		LocalReelsBackend b = new LocalReelsBackend(tmp, UTC);
		assertEquals(1, Pings.unseen(b.feed(), "PixelCST"), "der andere Post ist noch ungesehen");
	}

	@Test
	void commentCapStillHoldsForReplies() {
		LocalReelsBackend a = new LocalReelsBackend(null, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "", "PixelCST", NOON);
		ReelsComment top = a.comment(p.id, "PixelCST", "oben", NOON);
		for (int i = 1; i < LocalReelsBackend.MAX_COMMENTS; i++) assertNotNull(a.reply(p.id, top.id, "Bea", "Antwort " + i, NOON));
		assertEquals(LocalReelsBackend.MAX_COMMENTS, p.comments.size());
		assertNull(a.reply(p.id, top.id, "Bea", "eine zu viel", NOON));
		assertNull(a.comment(p.id, "Bea", "eine zu viel", NOON));
		java.util.Set<String> ids = new java.util.HashSet<String>();
		for (ReelsComment c : p.comments) assertTrue(ids.add(c.id), "auch in derselben Sekunde ist jede ID eigen: " + c.id);
		// Bearbeiten geht auch am vollen Post.
		assertNotNull(a.editComment(p.id, top.id, "PixelCST", "oben, geändert", NOON + 1));
	}

	// --- Angaben zum Foto (Shader, Instanz, Version, Loader) ---

	@Test
	void metaSurvivesFlushAndReload() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		ReelsMeta meta = new ReelsMeta("26.2", "fabric", "BSL_v8.2.09", "All the Mods 10");
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "mit", "PixelCST", NOON, meta);
		assertNotNull(p.meta);
		assertNotSame(meta, p.meta, "der Post hält seine eigene Kopie");
		a.flush();
		assertTrue(feedJson().contains("\"meta\""));
		ReelsMeta read = new LocalReelsBackend(tmp, UTC).feed().get(0).meta;
		assertEquals("26.2", read.mc);
		assertEquals("fabric", read.loader);
		assertEquals("BSL_v8.2.09", read.shader);
		assertEquals("All the Mods 10", read.pack);
		assertEquals("26.2 · Fabric", read.versionLabel());
	}

	@Test
	void oldPostsLoadWithoutMetaAndNoMetaKeyIsWritten() throws IOException {
		Files.write(tmp.resolve("feed.json"), oldFeed().getBytes(StandardCharsets.UTF_8));
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		assertNull(a.feed().get(0).meta);
		a.post(photo(2), 1280, 720, false, false, "ohne", "PixelCST", NOON + 5, null);
		a.post(photo(3), 1280, 720, false, false, "leer", "PixelCST", NOON + 6, new ReelsMeta(null, " ", "", "\n"));
		a.flush();
		assertFalse(feedJson().contains("\"meta\""), "ohne Angaben steht kein meta in der Datei");
		for (ReelsPost p : new LocalReelsBackend(tmp, UTC).feed()) assertNull(p.meta);
	}

	@Test
	void metaIsCleanedCappedAndDroppedWhenEmpty() {
		LocalReelsBackend a = new LocalReelsBackend(null, UTC);
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 100; i++) longName.append((char) ('a' + i % 26));
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "", "A", NOON,
				new ReelsMeta(" 26.2 ", "Fabric", "  Complementary\n§cReimagined  " + longName, longName.toString()));
		assertEquals("26.2", p.meta.mc);
		assertEquals("fabric", p.meta.loader, "klein geschrieben");
		assertEquals(ReelsMeta.MAX_SHADER, p.meta.shader.length());
		assertTrue(p.meta.shader.startsWith("Complementary  cReimagined"), p.meta.shader);
		assertEquals(ReelsMeta.MAX_PACK, p.meta.pack.length());

		ReelsPost only = a.post(photo(2), 1280, 720, false, false, "", "A", NOON + 1, new ReelsMeta(null, null, "BSL", null));
		assertNull(only.meta.mc);
		assertNull(only.meta.loader);
		assertEquals("BSL", only.meta.shader);
		assertNull(only.meta.pack);

		assertNull(a.post(photo(3), 1280, 720, false, false, "", "A", NOON + 2, new ReelsMeta("", "", " ", "\t")).meta);
		assertNull(a.post(photo(4), 1280, 720, false, false, "", "A", NOON + 3, null).meta);
	}

	@Test
	void badVersionAndUnknownLoaderFormAreDropped() throws IOException {
		String json = "{\"version\":1,\"posts\":["
				+ "{\"id\":\"20261113-120000-0001\",\"photo\":\"20261113-120000-0001.png\",\"createdAt\":1,"
				+ "\"meta\":{\"mc\":\"26.2<script>\",\"loader\":\"fabric!\",\"shader\":\"BSL\"}},"
				+ "{\"id\":\"20261113-120000-0002\",\"photo\":\"20261113-120000-0002.png\",\"createdAt\":2,"
				+ "\"meta\":{\"mc\":\"1.21.4-pre1 +b_2\",\"loader\":\"NeoForge\",\"pack\":\"P\"}},"
				+ "{\"id\":\"20261113-120000-0003\",\"photo\":\"20261113-120000-0003.png\",\"createdAt\":3,"
				+ "\"meta\":{\"mc\":\"12345678901234567\",\"loader\":\"abcdefghijklmnopq\"}},"
				+ "{\"id\":\"20261113-120000-0004\",\"photo\":\"20261113-120000-0004.png\",\"createdAt\":4,"
				+ "\"meta\":{\"mc\":\"ä\",\"loader\":\"fabric 2\"}},"
				+ "{\"id\":\"20261113-120000-0005\",\"photo\":\"20261113-120000-0005.png\",\"createdAt\":5,\"meta\":{}}]}";
		Files.write(tmp.resolve("feed.json"), json.getBytes(StandardCharsets.UTF_8));
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		List<ReelsPost> feed = a.feed();
		assertEquals(5, feed.size());
		assertFalse(a.dirty());
		ReelsMeta first = feed.get(4).meta;
		assertNull(first.mc);
		assertNull(first.loader);
		assertEquals("BSL", first.shader);
		ReelsMeta second = feed.get(3).meta;
		assertEquals("1.21.4-pre1 +b_2", second.mc);
		assertEquals("neoforge", second.loader);
		assertEquals("1.21.4-pre1 +b_2 · NeoForge", second.versionLabel());
		assertNull(feed.get(2).meta, "17 Zeichen sind zu lang – nichts bleibt übrig");
		assertNull(feed.get(1).meta);
		assertNull(feed.get(0).meta, "ein leeres meta ist keines");
	}

	@Test
	void eightArgumentPostStillWorksAndHasNoMeta() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		ReelsPost p = a.post(photo(1), 1280, 720, false, false, "wie bisher", "PixelCST", NOON);
		assertNotNull(p);
		assertNull(p.meta);
		assertEquals(1, a.feed().size());
		a.flush();
		assertFalse(feedJson().contains("\"meta\""));

		// Ein Backend, das nur die acht Angaben kennt, bekommt den Post über die neue Form trotzdem.
		Reels reels = Reels.forTest(new TrsModules(), a, DIRECT, UTC);
		Reels.Shot shot = new Reels.Shot(1L, photo(2), null, 1280, 720, null, 0, 0, false, false, false);
		assertNull(reels.post(shot, "ohne", "PixelCST", NOON + 1).meta);
		assertEquals("BSL", reels.post(shot, "mit", "PixelCST", NOON + 2, new ReelsMeta(null, null, "BSL", null)).meta.shader);
	}

	@Test
	void copyKeepsMeta() {
		ReelsPost p = new ReelsPost();
		p.id = "20261113-120000-0001";
		p.meta = new ReelsMeta("26.2", "quilt", "BSL", "Pack");
		ReelsComment c = new ReelsComment("A", "text", 5);
		c.id = "20261113-120000-0002";
		c.replyTo = "20261113-120000-0003";
		c.editedAt = Long.valueOf(9);
		c.seen = Boolean.TRUE;
		p.comments.add(c);
		ReelsPost q = p.copy();
		assertNotSame(p.meta, q.meta);
		assertEquals("26.2", q.meta.mc);
		assertEquals("quilt", q.meta.loader);
		assertEquals("BSL", q.meta.shader);
		assertEquals("Pack", q.meta.pack);
		ReelsComment d = q.comments.get(0);
		assertNotSame(c, d);
		assertEquals(c.id, d.id);
		assertEquals(c.replyTo, d.replyTo);
		assertEquals(c.editedAt, d.editedAt);
		assertEquals(c.seen, d.seen);
		assertEquals("text", d.text);
		assertNull(new ReelsPost().copy().meta);
	}

	// --- Der Dienst: sichern nur, wenn sich etwas geändert hat; Gedächtnis der Sitzung ---

	@Test
	void repliesEditsSeenAndProtectionAreSavedRightAway() throws IOException {
		LocalReelsBackend a = new LocalReelsBackend(tmp, UTC);
		final int[] jobs = {0};
		Reels reels = Reels.forTest(new TrsModules(), a, new Executor() {
			@Override
			public void execute(Runnable command) {
				jobs[0]++;
				command.run();
			}
		}, UTC);
		Reels.Shot shot = new Reels.Shot(1L, photo(1), null, 1280, 720, null, 0, 0, false, false, false);
		ReelsPost p = reels.post(shot, "", "PixelCST", NOON - DAY, new ReelsMeta("26.2", "fabric", null, null));
		assertEquals(1, jobs[0]);
		ReelsComment top = reels.comment(p.id, "PixelCST", "oben", NOON - DAY + 1);
		ReelsComment reply = reels.reply(p.id, top.id, "Steve_Baut", "Antwort", NOON - DAY + 2);
		assertEquals(top.id, reply.replyTo);
		assertEquals(3, jobs[0]);
		assertFalse(a.dirty(), "die Antwort ist schon gesichert");
		assertNull(reels.reply(p.id, top.id, "Steve_Baut", " ", NOON));
		assertEquals(3, jobs[0], "ohne Änderung wird nichts gesichert");

		assertNotNull(reels.editComment(p.id, top.id, "PixelCST", "oben!", NOON - DAY + 3));
		assertEquals(4, jobs[0]);
		assertNotNull(reels.editComment(p.id, top.id, "PixelCST", "oben!", NOON - DAY + 4));
		assertNull(reels.editComment(p.id, top.id, "Steve_Baut", "fremd", NOON - DAY + 4));
		assertEquals(4, jobs[0], "unverändert oder abgelehnt: nichts zu sichern");

		assertEquals(1, reels.markPingsSeen(p.id, "PixelCST"));
		assertEquals(5, jobs[0]);
		assertEquals(0, reels.markPingsSeen(p.id, "PixelCST"));
		assertEquals(5, jobs[0]);

		reels.noteClock(NOON);
		assertEquals(6, jobs[0], "der erste Blick des Tages wird gleich gesichert");
		assertTrue(feedJson().contains("\"clockDay\": \"2026-11-13\""), feedJson());
		reels.noteClock(NOON + 1000);
		assertEquals(6, jobs[0]);

		assertSame(StreakGuard.Result.ALREADY, pauseTwice(reels));
		assertEquals(7, jobs[0], "nur das gelungene Einlösen wird gesichert");
		assertTrue(feedJson().contains("\"freezes\""));
		assertFalse(a.dirty());
		assertEquals(ReelsTime.civilDay("2026-11-13"), reels.epochDay(NOON));
	}

	private static StreakGuard.Result pauseTwice(Reels reels) {
		assertSame(StreakGuard.Result.OK, reels.protect(false, NOON));
		return reels.protect(false, NOON);
	}

	@Test
	void pingAnnouncementAndRescueHintAreRememberedPerSession() {
		Reels reels = Reels.forTest(new TrsModules(), new LocalReelsBackend(null, UTC), DIRECT, UTC);
		assertFalse(reels.announcePings(0), "nichts zu melden");
		assertTrue(reels.announcePings(2), "einmal nach dem Spielstart");
		assertFalse(reels.announcePings(2), "nicht bei jedem Herausholen");
		assertTrue(reels.announcePings(3), "wieder, wenn eine dazukommt");
		assertFalse(reels.announcePings(1), "gesehen: weniger");
		assertTrue(reels.announcePings(2), "und wieder eine neue");

		assertTrue(reels.hintRescue(20770L));
		assertFalse(reels.hintRescue(20770L), "einmal am Tag");
		assertTrue(reels.hintRescue(20771L));

		Reels fresh = Reels.forTest(new TrsModules(), new LocalReelsBackend(null, UTC), DIRECT, UTC);
		assertTrue(fresh.announcePings(2), "ein neuer Dienst fängt neu an");
		assertTrue(fresh.hintRescue(20771L));
	}

	@Test
	void aBackendWithoutTheNewAbilitiesStillWorks() throws IOException {
		final LocalReelsBackend real = new LocalReelsBackend(null, UTC);
		// So sieht ein Backend aus, das nur die erste Fassung der Schnittstelle kennt.
		ReelsBackend plain = new ReelsBackend() {
			@Override
			public List<ReelsPost> feed() {
				return real.feed();
			}

			@Override
			public ReelsPost post(String photoFileName, int width, int height, boolean portrait, boolean selfie, String caption,
					String author, long now) {
				return real.post(photoFileName, width, height, portrait, selfie, caption, author, now);
			}

			@Override
			public boolean toggleLike(String postId) {
				return real.toggleLike(postId);
			}

			@Override
			public ReelsComment comment(String postId, String author, String text, long now) {
				return real.comment(postId, author, text, now);
			}

			@Override
			public int streak(long now) {
				return real.streak(now);
			}

			@Override
			public int streakAfterPost(long now) {
				return real.streakAfterPost(now);
			}

			@Override
			public Path photoFile(ReelsPost post) {
				return null;
			}

			@Override
			public Path newPhotoFile(String id) {
				return null;
			}

			@Override
			public void flush() {
			}
		};
		ReelsPost p = plain.post(photo(1), 1280, 720, false, false, "", "A", NOON, new ReelsMeta("26.2", "fabric", "BSL", null));
		assertNotNull(p);
		assertNull(p.meta, "die Angaben fallen weg, der Post kommt trotzdem an");
		ReelsComment top = plain.comment(p.id, "A", "oben", NOON);
		ReelsComment reply = plain.reply(p.id, top.id, "B", "Antwort", NOON);
		assertNotNull(reply);
		assertNull(reply.replyTo, "ohne Antworten: ein Kommentar der obersten Ebene");
		assertNull(plain.editComment(p.id, top.id, "A", "neu", NOON));
		assertEquals(0, plain.markPingsSeen(p.id, "A"));
		assertSame(StreakGuard.NONE, plain.streakGuard(NOON));
		assertSame(StreakGuard.Result.UNAVAILABLE, plain.protect(false, NOON));
		assertSame(StreakGuard.Result.UNAVAILABLE, plain.protect(true, NOON));
		plain.noteClock(NOON);
		assertFalse(StreakGuard.NONE.canPauseToday || StreakGuard.NONE.canRescueYesterday || StreakGuard.NONE.rescueOffered);
		Reels reels = Reels.forTest(new TrsModules(), plain, DIRECT, UTC);
		reels.noteClock(NOON);
		assertSame(StreakGuard.Result.UNAVAILABLE, reels.protect(false, NOON));
	}
}
