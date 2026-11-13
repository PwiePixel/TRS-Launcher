package dev.theredstonee.trsclient.core.reels;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Name der Instanz im TRS Launcher für die Angaben am Foto (MC-Reels) – einen eigenen Modpack-Namen kennt der Client
 * nicht. Gelesen wird nur {@code name} aus {@code <Instanz>/instance.json} neben dem Spielordner {@code minecraft}, und
 * nur, wenn das Spiel vom TRS Launcher gestartet wurde und die Datei wie eine Instanz-Datei aussieht. Das ist der innere
 * Aufbau des Launchers, kein Vertrag: passt irgendetwas nicht, gibt es schlicht keinen Namen. Version und Loader kommen
 * nie aus dieser Datei, sondern immer vom laufenden Spiel.
 */
public final class InstanceName {
	static final String BRAND = "TRS-Launcher";
	static final String GAME_FOLDER = "minecraft";
	static final String FILE = "instance.json";
	/** Größere Dateien werden nicht gelesen. */
	static final long MAX_BYTES = 65536L;

	private InstanceName() {
	}

	/**
	 * @param gameDir       der Spielordner
	 * @param launcherBrand Systemeigenschaft {@code minecraft.launcher.brand}
	 * @return der Name der Instanz, oder null (anderer Launcher, anderer Aufbau, Datei fehlt oder ist unbrauchbar)
	 */
	public static String read(Path gameDir, String launcherBrand) {
		try {
			if (gameDir == null || !BRAND.equals(launcherBrand)) return null;
			Path game = gameDir.toAbsolutePath().normalize();
			Path folder = game.getFileName();
			Path parent = game.getParent();
			if (folder == null || parent == null || !GAME_FOLDER.equals(folder.toString())) return null;
			Path file = parent.resolve(FILE);
			if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) return null;
			JsonElement root;
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				root = new JsonParser().parse(r);
			}
			if (root == null || !root.isJsonObject()) return null;
			JsonObject o = root.getAsJsonObject();
			// Sieht die Datei wie eine Instanz des Launchers aus?
			if (string(o.get("gameVersion")) == null) return null;
			JsonElement loader = o.get("loader");
			if (loader == null || !loader.isJsonObject()) return null;
			String name = string(o.get("name"));
			if (name == null) return null;
			String clean = ReelsPost.clean(name, ReelsMeta.MAX_PACK);
			return clean.isEmpty() ? null : clean;
		} catch (Exception e) {
			return null;
		}
	}

	private static String string(JsonElement e) {
		if (e == null || !e.isJsonPrimitive()) return null;
		JsonPrimitive p = e.getAsJsonPrimitive();
		return p.isString() ? p.getAsString() : null;
	}
}
