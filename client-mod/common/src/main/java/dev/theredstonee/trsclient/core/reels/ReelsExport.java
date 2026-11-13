package dev.theredstonee.trsclient.core.reels;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * „Bild speichern“ (MC-Reels): kopiert das Foto eines Posts in den Screenshots-Ordner des Spiels, als
 * {@code mc-reels_<yyyy-MM-dd_HH.mm.ss>.png} (die Zeit steht in der ID des Posts). Überschrieben wird nie: ein belegter
 * Name bekommt {@code _2} bis {@code _99}; liegt unter einem dieser Namen schon dasselbe Bild, wird nichts geschrieben.
 * Kein Netz – nur diese eine Kopie auf der Platte.
 */
public final class ReelsExport {
	/** Höchstens so viele Namen werden versucht. */
	static final int MAX_NAMES = 99;

	public enum Status {
		/** Die Kopie liegt jetzt im Ordner. */
		SAVED,
		/** Dasselbe Bild lag schon dort. */
		ALREADY,
		/** Die Foto-Datei des Posts fehlt. */
		MISSING,
		/** Der Ordner ließ sich nicht anlegen oder die Kopie nicht schreiben. */
		FAILED
	}

	public static final class Result {
		public final Status status;
		/** Name der Datei im Screenshots-Ordner (bei {@link Status#SAVED} und {@link Status#ALREADY}), sonst null. */
		public final String fileName;

		Result(Status status, String fileName) {
			this.status = status;
			this.fileName = fileName;
		}
	}

	private ReelsExport() {
	}

	/**
	 * Dateiname der Kopie: {@code mc-reels_2026-11-13_14.03.22.png}, ab {@code n = 2} mit {@code _n} vor der Endung.
	 * Null bei einer ungültigen ID.
	 */
	public static String fileName(String postId, int n) {
		if (!ReelsPost.validId(postId)) return null;
		String name = "mc-reels_" + postId.substring(0, 4) + "-" + postId.substring(4, 6) + "-" + postId.substring(6, 8) + "_"
				+ postId.substring(9, 11) + "." + postId.substring(11, 13) + "." + postId.substring(13, 15);
		return name + (n >= 2 ? "_" + n : "") + ".png";
	}

	/** Foto in den Ordner kopieren (wartet auf die Platte – nur im Hintergrund-Thread aufrufen). */
	public static Result save(Path photo, Path screenshotsDir, String postId) {
		if (photo == null || !Files.isRegularFile(photo)) return new Result(Status.MISSING, null);
		if (screenshotsDir == null || fileName(postId, 1) == null) return new Result(Status.FAILED, null);
		// Nur eine Datei, die hier selbst angefangen wurde, darf nach einem Fehler wieder gelöscht werden.
		Path partial = null;
		try {
			Files.createDirectories(screenshotsDir);
			for (int n = 1; n <= MAX_NAMES; n++) {
				String name = fileName(postId, n);
				Path candidate = screenshotsDir.resolve(name);
				// Files.exists folgt Verknüpfungen: ein verlinkter gemeinsamer Screenshots-Ordner zählt wie ein echter.
				if (Files.exists(candidate)) {
					if (same(photo, candidate)) return new Result(Status.ALREADY, name);
					continue;
				}
				try {
					partial = candidate;
					Files.copy(photo, candidate);
					return new Result(Status.SAVED, name);
				} catch (FileAlreadyExistsException e) {
					// Gerade eben von jemand anderem angelegt: nicht anfassen, nächster Name.
					partial = null;
				}
			}
			return new Result(Status.FAILED, null);
		} catch (IOException | RuntimeException e) {
			if (partial != null) {
				try {
					Files.deleteIfExists(partial);
				} catch (IOException | RuntimeException ignored) {
					// bleibt liegen
				}
			}
			return new Result(Status.FAILED, null);
		}
	}

	/** Gleiche Größe und gleiche Bytes? */
	private static boolean same(Path a, Path b) throws IOException {
		if (!Files.isRegularFile(b) || Files.size(a) != Files.size(b)) return false;
		try (InputStream x = Files.newInputStream(a); InputStream y = Files.newInputStream(b)) {
			byte[] p = new byte[8192];
			byte[] q = new byte[8192];
			while (true) {
				int n = fill(x, p);
				int m = fill(y, q);
				if (n != m) return false;
				if (n <= 0) return true;
				for (int i = 0; i < n; i++) if (p[i] != q[i]) return false;
			}
		}
	}

	private static int fill(InputStream in, byte[] buffer) throws IOException {
		int total = 0;
		while (total < buffer.length) {
			int n = in.read(buffer, total, buffer.length - total);
			if (n < 0) break;
			total += n;
		}
		return total;
	}
}
