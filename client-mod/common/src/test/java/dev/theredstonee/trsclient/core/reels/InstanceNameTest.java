package dev.theredstonee.trsclient.core.reels;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** MC-Reels: Name der Instanz aus der instance.json des TRS Launchers – nur mit Dateien in einem Testordner. */
class InstanceNameTest {
	private static final String BRAND = "TRS-Launcher";

	@TempDir
	Path tmp;

	private static String json(String name) {
		return "{\"id\":\"atm10\",\"name\":" + name + ",\"gameVersion\":\"26.2\",\"loader\":{\"kind\":\"fabric\",\"version\":null},"
				+ "\"createdAt\":\"2026-11-01T10:00:00Z\",\"lastPlayed\":null,\"totalPlaySeconds\":12}";
	}

	/** Legt {@code <tmp>/<instanz>/instance.json} und den Spielordner daneben an; liefert den Spielordner. */
	private Path instance(String folder, String gameFolder, String content) throws IOException {
		Path dir = tmp.resolve(folder);
		Path game = dir.resolve(gameFolder);
		Files.createDirectories(game);
		if (content != null) Files.write(dir.resolve("instance.json"), content.getBytes(StandardCharsets.UTF_8));
		return game;
	}

	@Test
	void readsTheNameBesideAGameFolderCalledMinecraft() throws IOException {
		Path game = instance("atm10", "minecraft", json("\"All the Mods 10\""));
		assertEquals("All the Mods 10", InstanceName.read(game, BRAND));
		// Der Spielordner ist im Spiel oft relativ oder nicht normalisiert.
		assertEquals("All the Mods 10", InstanceName.read(game.resolve("saves").resolve(".."), BRAND));
		assertEquals("All the Mods 10", InstanceName.read(game.resolve("."), BRAND));
		assertEquals("Überleben – Ä", InstanceName.read(instance("u", "minecraft", json("\"Überleben – Ä\"")), BRAND));
	}

	@Test
	void otherLauncherBrandGivesNothing() throws IOException {
		Path game = instance("atm10", "minecraft", json("\"All the Mods 10\""));
		assertNull(InstanceName.read(game, null));
		assertNull(InstanceName.read(game, ""));
		assertNull(InstanceName.read(game, "minecraft-launcher"));
		assertNull(InstanceName.read(game, "trs-launcher"), "genau die Marke des TRS Launchers");
		assertNull(InstanceName.read(game, "TRS-Launcher-Messe"));
		assertNull(InstanceName.read(null, BRAND));
	}

	@Test
	void otherFolderNameGivesNothing() throws IOException {
		assertNull(InstanceName.read(instance("a", ".minecraft", json("\"Name\"")), BRAND));
		assertNull(InstanceName.read(instance("b", "run", json("\"Name\"")), BRAND));
		assertNull(InstanceName.read(instance("c", "minecraft", json("\"Name\"")).resolveSibling("minecraft2"), BRAND));
		assertNull(InstanceName.read(instance("c2", "minecraft", json("\"Name\"")).resolve("saves"), BRAND));
		// Die Datei muss neben dem Spielordner liegen, nicht darin.
		Path inside = instance("d", "minecraft", null);
		Files.write(inside.resolve("instance.json"), json("\"Name\"").getBytes(StandardCharsets.UTF_8));
		assertNull(InstanceName.read(inside, BRAND));
		assertNull(InstanceName.read(inside.getRoot(), BRAND), "ein Ordner ohne Namen und ohne Elternordner");
	}

	@Test
	void missingBrokenOrOversizedFileGivesNothing() throws IOException {
		assertNull(InstanceName.read(instance("fehlt", "minecraft", null), BRAND));
		assertNull(InstanceName.read(instance("kaputt", "minecraft", "{ \"name\": \"X\", kaputt"), BRAND));
		assertNull(InstanceName.read(instance("leer", "minecraft", ""), BRAND));
		assertNull(InstanceName.read(instance("liste", "minecraft", "[" + json("\"X\"") + "]"), BRAND));
		assertNull(InstanceName.read(instance("text", "minecraft", "\"X\""), BRAND));
		// Ein Ordner an der Stelle der Datei.
		Path game = instance("ordner", "minecraft", null);
		Files.createDirectories(game.resolveSibling("instance.json"));
		assertNull(InstanceName.read(game, BRAND));
		// Größer als 64 KiB: wird nicht gelesen, auch wenn sie gültig wäre.
		StringBuilder pad = new StringBuilder();
		for (int i = 0; i < 70000; i++) pad.append(' ');
		assertNull(InstanceName.read(instance("riesig", "minecraft", json("\"X\"") + pad), BRAND));
		StringBuilder small = new StringBuilder();
		for (int i = 0; i < 60000; i++) small.append(' ');
		assertEquals("X", InstanceName.read(instance("gross", "minecraft", json("\"X\"") + small), BRAND));
	}

	@Test
	void fileWithoutGameVersionOrLoaderGivesNothing() throws IOException {
		assertNull(InstanceName.read(instance("a", "minecraft", "{\"name\":\"X\"}"), BRAND));
		assertNull(InstanceName.read(instance("b", "minecraft", "{\"name\":\"X\",\"gameVersion\":\"26.2\"}"), BRAND));
		assertNull(InstanceName.read(instance("c", "minecraft", "{\"name\":\"X\",\"loader\":{\"kind\":\"fabric\"}}"), BRAND));
		assertNull(InstanceName.read(instance("d", "minecraft", "{\"name\":\"X\",\"gameVersion\":26,\"loader\":{}}"), BRAND));
		assertNull(InstanceName.read(instance("e", "minecraft", "{\"name\":\"X\",\"gameVersion\":\"26.2\",\"loader\":\"fabric\"}"), BRAND));
		assertNull(InstanceName.read(instance("f", "minecraft", "{\"gameVersion\":\"26.2\",\"loader\":{}}"), BRAND), "ohne Namen");
		assertNull(InstanceName.read(instance("g", "minecraft", json("42")), BRAND), "der Name muss ein Text sein");
		assertNull(InstanceName.read(instance("h", "minecraft", json("null")), BRAND));
		assertNull(InstanceName.read(instance("i", "minecraft", json("{\"de\":\"X\"}")), BRAND));
		assertEquals("X", InstanceName.read(instance("j", "minecraft", "{\"name\":\"X\",\"gameVersion\":\"26.2\",\"loader\":{}}"), BRAND));
	}

	@Test
	void nameIsCleanedAndCapped() throws IOException {
		assertEquals("Mein Pack", InstanceName.read(instance("a", "minecraft", json("\"  Mein\\nPack \"")), BRAND));
		assertNull(InstanceName.read(instance("b", "minecraft", json("\"  \\n \"")), BRAND), "leer nach dem Säubern");
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 200; i++) b.append((char) ('a' + i % 26));
		String name = InstanceName.read(instance("c", "minecraft", json("\"" + b + "\"")), BRAND);
		assertEquals(ReelsMeta.MAX_PACK, name.length());
		assertEquals(64, ReelsMeta.MAX_PACK);
		assertEquals("bunt c", InstanceName.read(instance("d", "minecraft", json("\"bunt§c\"")), BRAND));
	}
}
