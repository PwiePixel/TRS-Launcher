package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.clips.ScreenshotShare;
import dev.theredstonee.trsclient.core.screenshot.ScreenshotNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MC-Reels: „Bild speichern“ – nur Dateien in einem Testordner, kein Dateimanager, keine Zwischenablage. */
class ReelsExportTest {
	private static final String ID = "20261113-140322-7e51";
	private static final String NAME = "mc-reels_2026-11-13_14.03.22.png";

	@TempDir
	Path tmp;

	private Path photo(String name, String content) throws IOException {
		Path dir = tmp.resolve("photos");
		Files.createDirectories(dir);
		Path file = dir.resolve(name);
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private static List<String> names(Path dir) throws IOException {
		List<String> out = new ArrayList<String>();
		try (Stream<Path> list = Files.list(dir)) {
			list.forEach(p -> out.add(p.getFileName().toString()));
		}
		Collections.sort(out);
		return out;
	}

	@Test
	void savedCopyGetsAReelsNameFromThePostIdAndTheSameBytes() throws IOException {
		Path photo = photo(ID + ".png", "PNG-Bytes des Fotos");
		Path shots = tmp.resolve("screenshots");
		Files.createDirectories(shots);
		ReelsExport.Result r = ReelsExport.save(photo, shots, ID);
		assertSame(ReelsExport.Status.SAVED, r.status);
		assertEquals(NAME, r.fileName);
		assertEquals(Collections.singletonList(NAME), names(shots));
		assertArrayEquals(Files.readAllBytes(photo), Files.readAllBytes(shots.resolve(NAME)));
		assertTrue(Files.isRegularFile(photo), "das Foto des Posts bleibt, wo es ist");
		assertEquals(NAME, ReelsExport.fileName(ID, 1));
		assertEquals(NAME, ReelsExport.fileName(ID, 0));
		assertEquals("mc-reels_2026-11-13_14.03.22_2.png", ReelsExport.fileName(ID, 2));
		assertEquals("mc-reels_2026-11-13_14.03.22_99.png", ReelsExport.fileName(ID, 99));
	}

	@Test
	void savingNeverOverwritesAndCountsUp() throws IOException {
		Path photo = photo(ID + ".png", "das neue Foto");
		Path shots = tmp.resolve("screenshots");
		Files.createDirectories(shots);
		Files.write(shots.resolve(NAME), "etwas ganz anderes".getBytes(StandardCharsets.UTF_8));
		Files.write(shots.resolve("mc-reels_2026-11-13_14.03.22_2.png"), "gleich lang!!".getBytes(StandardCharsets.UTF_8));
		assertEquals(Files.size(photo), Files.size(shots.resolve("mc-reels_2026-11-13_14.03.22_2.png")), "gleiche Größe, anderer Inhalt");

		ReelsExport.Result r = ReelsExport.save(photo, shots, ID);
		assertSame(ReelsExport.Status.SAVED, r.status);
		assertEquals("mc-reels_2026-11-13_14.03.22_3.png", r.fileName);
		assertEquals("etwas ganz anderes", new String(Files.readAllBytes(shots.resolve(NAME)), StandardCharsets.UTF_8));
		assertEquals("gleich lang!!", new String(Files.readAllBytes(shots.resolve("mc-reels_2026-11-13_14.03.22_2.png")), StandardCharsets.UTF_8));
		assertEquals("das neue Foto", new String(Files.readAllBytes(shots.resolve(r.fileName)), StandardCharsets.UTF_8));
		assertEquals(3, names(shots).size());

		// Alle 99 Namen belegt: nichts wird überschrieben, es schlägt fehl.
		Path full = tmp.resolve("voll");
		Files.createDirectories(full);
		for (int n = 1; n <= 99; n++) Files.write(full.resolve(ReelsExport.fileName(ID, n)), ("belegt " + n).getBytes(StandardCharsets.UTF_8));
		ReelsExport.Result none = ReelsExport.save(photo, full, ID);
		assertSame(ReelsExport.Status.FAILED, none.status);
		assertNull(none.fileName);
		assertEquals(99, names(full).size());
		assertEquals("belegt 1", new String(Files.readAllBytes(full.resolve(NAME)), StandardCharsets.UTF_8));
	}

	@Test
	void savingTheSamePhotoTwiceReportsAlreadyThere() throws IOException {
		Path photo = photo(ID + ".png", "PNG-Bytes des Fotos");
		Path shots = tmp.resolve("screenshots");
		assertSame(ReelsExport.Status.SAVED, ReelsExport.save(photo, shots, ID).status);
		ReelsExport.Result again = ReelsExport.save(photo, shots, ID);
		assertSame(ReelsExport.Status.ALREADY, again.status);
		assertEquals(NAME, again.fileName);
		assertEquals(Collections.singletonList(NAME), names(shots), "keine zweite Datei");

		// Liegt dasselbe Bild erst unter dem zweiten Namen, zählt das auch.
		Path other = tmp.resolve("zwei");
		Files.createDirectories(other);
		Files.write(other.resolve(NAME), "fremd".getBytes(StandardCharsets.UTF_8));
		assertEquals("mc-reels_2026-11-13_14.03.22_2.png", ReelsExport.save(photo, other, ID).fileName);
		ReelsExport.Result third = ReelsExport.save(photo, other, ID);
		assertSame(ReelsExport.Status.ALREADY, third.status);
		assertEquals("mc-reels_2026-11-13_14.03.22_2.png", third.fileName);
		assertEquals(2, names(other).size());
	}

	@Test
	void missingPhotoFileIsReportedAndNothingIsCreated() throws IOException {
		Path shots = tmp.resolve("screenshots");
		ReelsExport.Result r = ReelsExport.save(tmp.resolve("photos").resolve(ID + ".png"), shots, ID);
		assertSame(ReelsExport.Status.MISSING, r.status);
		assertNull(r.fileName);
		assertFalse(Files.exists(shots), "nicht einmal der Ordner wird angelegt");
		assertSame(ReelsExport.Status.MISSING, ReelsExport.save(null, shots, ID).status);
		Files.createDirectories(tmp.resolve("ordner.png"));
		assertSame(ReelsExport.Status.MISSING, ReelsExport.save(tmp.resolve("ordner.png"), shots, ID).status, "ein Ordner ist kein Foto");
		assertFalse(Files.exists(shots));
	}

	@Test
	void screenshotsFolderIsCreatedWhenMissing() throws IOException {
		Path photo = photo(ID + ".png", "PNG");
		Path shots = tmp.resolve("spiel").resolve("tief").resolve("screenshots");
		assertFalse(Files.exists(shots));
		ReelsExport.Result r = ReelsExport.save(photo, shots, ID);
		assertSame(ReelsExport.Status.SAVED, r.status);
		assertTrue(Files.isRegularFile(shots.resolve(NAME)));
	}

	@Test
	void aFileInPlaceOfTheFolderFailsWithoutLeavingAPartialFile() throws IOException {
		Path photo = photo(ID + ".png", "PNG");
		Path blocked = tmp.resolve("screenshots");
		Files.write(blocked, "ich bin eine Datei".getBytes(StandardCharsets.UTF_8));
		ReelsExport.Result r = ReelsExport.save(photo, blocked, ID);
		assertSame(ReelsExport.Status.FAILED, r.status);
		assertNull(r.fileName);
		assertEquals("ich bin eine Datei", new String(Files.readAllBytes(blocked), StandardCharsets.UTF_8));
		List<String> left = names(tmp);
		assertEquals(java.util.Arrays.asList("photos", "screenshots"), left, "nichts Halbes bleibt liegen");
		assertSame(ReelsExport.Status.FAILED, ReelsExport.save(photo, null, ID).status, "ohne Ordner");
	}

	@Test
	void savedNameIsNoVanillaScreenshotNameButAValidShareName() {
		for (int n = 1; n <= 99; n += 49) {
			String name = ReelsExport.fileName(ID, n);
			assertFalse(ScreenshotNames.vanilla(name), "kein Vanilla-Name: die Screenshot-Werkzeuge melden die Datei nicht – " + name);
			assertTrue(ScreenshotShare.valid(name), "Galerie und Bildauswahl nehmen den Namen an – " + name);
		}
		assertTrue(ScreenshotNames.vanilla("2026-11-13_14.03.22.png"), "zum Vergleich");
	}

	@Test
	void invalidPostIdGivesNoFileName() throws IOException {
		assertNull(ReelsExport.fileName(null, 1));
		assertNull(ReelsExport.fileName("", 1));
		assertNull(ReelsExport.fileName("../../options", 1));
		assertNull(ReelsExport.fileName("20261113_140322-7e51", 1));
		assertNull(ReelsExport.fileName(ID + ".png", 1));
		Path photo = photo(ID + ".png", "PNG");
		Path shots = tmp.resolve("screenshots");
		assertSame(ReelsExport.Status.FAILED, ReelsExport.save(photo, shots, "../x").status);
		assertFalse(Files.exists(shots), "ohne gültigen Namen wird nichts angelegt");
	}
}
