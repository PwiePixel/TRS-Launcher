package dev.theredstonee.trsclient.core.reels;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Woher der Feed kommt und wohin Posts, Likes und Kommentare gehen. In der ersten Testversion gibt es nur
 * {@link LocalReelsBackend} (Dateien auf diesem PC, kein Netz). Ein späterer Server-Client implementiert dieselbe
 * Schnittstelle mit einem zwischengespeicherten Stand: {@link #feed()} liefert dann den zuletzt geladenen Feed, die
 * ändernden Aufrufe wirken sofort auf diesen Stand und gehen im Hintergrund an den Server.
 *
 * <p>Alle Aufrufe sind schnell (kein Warten auf Platte oder Netz), außer {@link #flush()}.
 */
public interface ReelsBackend {
	/**
	 * Der Feed, neueste Posts zuerst; nicht änderbar. Solange sich nichts ändert, ist es dieselbe Liste (die Oberfläche
	 * erkennt Änderungen an der Identität). Die Posts darin nur lesen.
	 */
	List<ReelsPost> feed();

	/**
	 * Neuen Post anlegen (steht danach vorn im Feed).
	 *
	 * @param photoFileName Dateiname aus {@link #newPhotoFile(String)} ({@code <id>.png})
	 * @return der Post, oder null, wenn das Foto keinen gültigen Namen hat
	 */
	ReelsPost post(String photoFileName, int width, int height, boolean portrait, boolean selfie, String caption, String author,
			long now);

	/**
	 * Neuen Post anlegen und dazu festhalten, womit das Foto aufgenommen wurde ({@code meta}, darf null sein). Ein
	 * Backend, das diese Angaben nicht speichert, legt den Post ohne sie an.
	 */
	default ReelsPost post(String photoFileName, int width, int height, boolean portrait, boolean selfie, String caption,
			String author, long now, ReelsMeta meta) {
		return post(photoFileName, width, height, portrait, selfie, caption, author, now);
	}

	/** Like umschalten. Rückgabe: der neue Zustand (false auch, wenn es den Post nicht gibt). */
	boolean toggleLike(String postId);

	/** Kommentar anhängen; null, wenn es den Post nicht gibt oder der Text leer ist. */
	ReelsComment comment(String postId, String author, String text, long now);

	/**
	 * Auf einen Kommentar antworten (eine Ebene: die Antwort auf eine Antwort hängt am selben Kommentar der obersten
	 * Ebene). Gibt es {@code parentId} nicht, wird es ein Kommentar der obersten Ebene. Null wie bei {@link #comment}.
	 */
	default ReelsComment reply(String postId, String parentId, String author, String text, long now) {
		return comment(postId, author, text, now);
	}

	/**
	 * Den Text eines eigenen Kommentars ändern ({@code author} muss der Verfasser sein, ohne Groß-/Kleinschreibung).
	 * Rückgabe: der Kommentar – bei unverändertem Text ohne Änderung –, oder null (unbekannt, fremd, leerer Text, oder
	 * dieses Backend kann nicht bearbeiten).
	 */
	default ReelsComment editComment(String postId, String commentId, String author, String text, long now) {
		return null;
	}

	/**
	 * Erwähnungen und Antworten unter diesem Post, die {@code self} betreffen ({@link Pings}), als gesehen merken.
	 * Rückgabe: wie viele es waren.
	 */
	default int markPingsSeen(String postId, String self) {
		return 0;
	}

	/** Tage in Folge mit mindestens einem Post (siehe {@link ReelsTime#streak}). */
	int streak(long now);

	/** Die Streak, die gälte, wenn jetzt ein Post dazukäme (ändert nichts). */
	int streakAfterPost(long now);

	/** Stand des Streak-Schutzes ({@link StreakGuard#NONE}, wenn dieses Backend keinen kennt). */
	default StreakGuard streakGuard(long now) {
		return StreakGuard.NONE;
	}

	/** Einen Tag schützen: heute („pausieren“) oder gestern („retten“). Ändert nur bei {@link StreakGuard.Result#OK} etwas. */
	default StreakGuard.Result protect(boolean yesterday, long now) {
		return StreakGuard.Result.UNAVAILABLE;
	}

	/**
	 * Das Handy wurde herausgeholt: den Kalendertag merken, damit ein späteres Zurückstellen der Uhr keinen
	 * Streak-Schutz erlaubt.
	 */
	default void noteClock(long now) {
	}

	/** Datei des Fotos eines Posts (null, wenn der Post keinen gültigen Dateinamen hat). */
	Path photoFile(ReelsPost post);

	/** Wohin die Kamera das Foto mit dieser ID schreibt (null bei ungültiger ID). */
	Path newPhotoFile(String id);

	/**
	 * Lassen sich Änderungen gerade nicht sichern (lokal: Zweitfenster derselben Instanz)? Dann nimmt die Kamera kein
	 * Foto auf – der Post wäre nach dem nächsten Start wieder weg und das Foto bliebe ohne Post liegen.
	 */
	default boolean readOnly() {
		return false;
	}

	/** Ausstehende Änderungen sichern (Platte bzw. Server). Darf von einem Hintergrund-Thread kommen. */
	void flush() throws IOException;
}
