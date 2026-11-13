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
		assertEquals(288, shot.previewW, "Vorschau: höchstens 512 an der langen Seite");
		assertEquals(512, shot.previewH);
		assertEquals(288 * 512, shot.preview.length);
		assertEquals(0xFF205080, shot.preview[0]);
		assertEquals(0xFFE0C020, shot.preview[287]);

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
		assertEquals(512, wide.previewW);
		assertEquals(288, wide.previewH);
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
}
