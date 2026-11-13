package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.wardrobe.PngWriter;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * MC-Reels – der Dienst des laufenden Spiels: der Feed ({@link ReelsBackend}, vorerst nur lokal), Fotos speichern
 * (Ausschnitt, Verkleinern, PNG – im Hintergrund) und die Sperre der Handy-Taste. Die Oberfläche liegt in
 * {@code core.ui.reels}; der Baum ruft nur {@link #init} und beim Beenden {@link #stop()}.
 *
 * <p>Kein Netz: alles bleibt im Ordner, den {@link #init} bekommt. Nichts hier wirft Fehler zum Aufrufer – sie werden
 * gemeldet ({@code log}) und ein Foto, das nicht gespeichert werden konnte, kommt als {@link Shot#failed} zurück.
 *
 * <p><b>Kein Foto ohne Post:</b> jedes Foto bekommt beim Auslösen eine Nummer ({@link #savePhoto}), und es wartet immer
 * nur einer auf höchstens ein Foto. Wartet beim Fertigwerden niemand mehr darauf ({@link #abandon}, ein neueres Foto,
 * {@link #stop()}), löscht der Hintergrund-Thread die Datei gleich wieder, statt sie liegen zu lassen.
 */
public final class Reels {
	private static volatile Reels instance;

	/** Ein aufgenommenes Foto, bevor es gepostet (oder verworfen) wird. */
	public static final class Shot {
		/** Dateiname im Foto-Ordner ({@code <id>.png}) – so geht er an {@link ReelsBackend#post}. */
		public final String fileName;
		public final Path file;
		public final int width;
		public final int height;
		/** Verkleinerte Fassung für die Vorschau (ARGB, höchstens {@link PhotoFrame#MAX_PREVIEW_SIDE} lang). */
		public final int[] preview;
		public final int previewW;
		public final int previewH;
		public final boolean selfie;
		public final boolean portrait;
		/** Das Foto konnte nicht gespeichert werden (dann sind Datei und Vorschau leer). */
		public final boolean failed;
		/** Nummer des Auslösens, zu dem dieses Foto gehört ({@link Reels#savePhoto}). */
		public final long ticket;

		Shot(long ticket, String fileName, Path file, int width, int height, int[] preview, int previewW, int previewH,
				boolean selfie, boolean portrait, boolean failed) {
			this.ticket = ticket;
			this.fileName = fileName;
			this.file = file;
			this.width = width;
			this.height = height;
			this.preview = preview;
			this.previewW = previewW;
			this.previewH = previewH;
			this.selfie = selfie;
			this.portrait = portrait;
			this.failed = failed;
		}

		static Shot failed(long ticket, boolean selfie, boolean portrait) {
			return new Shot(ticket, null, null, 0, 0, null, 0, 0, selfie, portrait, true);
		}
	}

	private final TrsModules modules;
	private final ReelsBackend backend;
	private final Consumer<String> log;
	private final PhoneKeyGate keyGate = new PhoneKeyGate();
	private final ConcurrentLinkedQueue<Shot> shots = new ConcurrentLinkedQueue<Shot>();
	private final TimeZone zone;
	private Executor worker;
	private int shotSalt = (int) (System.nanoTime() >>> 8);
	private volatile long lastSaveError;
	/** Nummer des Fotos, auf das gerade jemand wartet (0 = niemand). */
	private volatile long wanted;
	private long lastTicket;
	/** Gedächtnis dieser Sitzung (nicht gespeichert): so viele ungesehene Erwähnungen waren zuletzt gemeldet. */
	private int announcedPings;
	/** Gedächtnis dieser Sitzung: der Tag, für den „gestern lässt sich retten“ schon gemeldet wurde. */
	private long rescueHintDay = Long.MIN_VALUE;

	Reels(TrsModules modules, ReelsBackend backend, Consumer<String> log, Executor worker, TimeZone zone) {
		this.modules = modules;
		this.backend = backend;
		this.log = log != null ? log : new Consumer<String>() {
			@Override
			public void accept(String s) {
			}
		};
		this.worker = worker;
		this.zone = zone != null ? zone : TimeZone.getDefault();
	}

	/**
	 * Beim Start (nur der Baum, der das Handy zeichnen kann): Feed aus {@code dir} laden ({@code feed.json},
	 * {@code photos/}).
	 */
	public static Reels init(TrsModules modules, Path dir, Consumer<String> log) {
		TimeZone zone = TimeZone.getDefault();
		Reels r = new Reels(modules, new LocalReelsBackend(dir, zone), log, null, zone);
		instance = r;
		return r;
	}

	/** Für Tests: Dienst mit eigenem Speicher und eigenem „Hintergrund“ (null = echter Hintergrund-Thread). */
	public static Reels forTest(TrsModules modules, ReelsBackend backend, Executor worker, TimeZone zone) {
		Reels r = new Reels(modules, backend, null, worker, zone);
		instance = r;
		return r;
	}

	/** Für Tests: Dienst wieder entfernen. */
	public static void clearForTest() {
		instance = null;
	}

	/** Der Dienst des laufenden Spiels – null auf jeder Plattform ohne MC-Reels. */
	public static Reels get() {
		return instance;
	}

	public TrsModules modules() {
		return modules;
	}

	public ReelsBackend backend() {
		return backend;
	}

	public PhoneKeyGate keyGate() {
		return keyGate;
	}

	/** Ein einzelner Hintergrund-Thread für alles Langsame (Bilder rechnen, PNG und {@code feed.json} schreiben). */
	public synchronized Executor worker() {
		if (worker == null) {
			ExecutorService service = Executors.newSingleThreadExecutor(new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r, "TRS-Reels");
					t.setDaemon(true);
					return t;
				}
			});
			worker = service;
		}
		return worker;
	}

	// --- Fotos ---

	/**
	 * Foto aus einem Bild des Spiels machen (ARGB, {@code w}×{@code h}, Zeilen von oben): im Hintergrund mittig auf
	 * 16:9 bzw. 9:16 zuschneiden, auf höchstens {@link PhotoFrame#MAX_LONG_SIDE} verkleinern (nie vergrößern) und als
	 * PNG speichern. Das Ergebnis kommt über {@link #pollShot()} (Render-Thread) und, wenn angegeben, über {@code done}
	 * (im Hintergrund-Thread).
	 *
	 * @return die Nummer dieses Fotos ({@link Shot#ticket}). Ab jetzt wartet der Aufrufer auf genau dieses Foto; ein
	 *         älteres, das noch gespeichert wird, verfällt damit (seine Datei wird wieder gelöscht).
	 */
	public long savePhoto(final int[] argb, final int w, final int h, final PhoneHold hold, final boolean selfie,
			final Consumer<Shot> done) {
		final boolean portrait = hold == PhoneHold.PORTRAIT;
		final long now = System.currentTimeMillis();
		final long ticket = nextTicket();
		wanted = ticket;
		Runnable job = new Runnable() {
			@Override
			public void run() {
				Shot shot;
				try {
					shot = write(ticket, argb, w, h, hold, selfie, now);
				} catch (IOException | RuntimeException | OutOfMemoryError e) {
					log.accept("MC-Reels: Foto nicht gespeichert: " + e);
					shot = Shot.failed(ticket, selfie, portrait);
				}
				deliver(shot, done);
			}
		};
		try {
			worker().execute(job);
		} catch (RejectedExecutionException e) {
			deliver(Shot.failed(ticket, selfie, portrait), done);
		}
		return ticket;
	}

	private synchronized long nextTicket() {
		return ++lastTicket;
	}

	/**
	 * Auf das Foto mit dieser Nummer wartet niemand mehr (Kamera verlassen, Handy weggesteckt): wird es noch fertig,
	 * löscht der Hintergrund-Thread die Datei wieder. Ein anderes, neueres Foto bleibt davon unberührt.
	 */
	public void abandon(long ticket) {
		if (wanted == ticket) wanted = 0L;
	}

	private void deliver(Shot shot, Consumer<Shot> done) {
		if (shot.ticket == wanted) {
			shots.add(shot);
		} else {
			// Niemand wartet mehr darauf: nicht liegen lassen (läuft schon im Hintergrund-Thread).
			delete(shot);
		}
		if (done == null) return;
		try {
			done.accept(shot);
		} catch (RuntimeException e) {
			log.accept("MC-Reels: " + e);
		}
	}

	private Shot write(long ticket, int[] argb, int w, int h, PhoneHold hold, boolean selfie, long now) throws IOException {
		boolean portrait = hold == PhoneHold.PORTRAIT;
		int[] aspect = PhotoFrame.aspect(hold);
		int[] crop = PhotoFrame.cropRect(w, h, aspect[0], aspect[1]);
		int[] size = PhotoFrame.outSize(crop[2], crop[3], PhotoFrame.MAX_LONG_SIDE);
		int[] pixels = PhotoFrame.render(argb, w, h, crop, size[0], size[1]);

		String id = null;
		Path file = null;
		// Eine freie ID suchen (zwei Fotos in derselben Sekunde unterscheiden sich im Anhang).
		for (int i = 0; i < 64; i++) {
			String candidate = ReelsPost.newId(now, zone, nextSalt());
			Path p = backend.newPhotoFile(candidate);
			if (p == null) throw new IOException("kein Foto-Ordner");
			if (!Files.exists(p)) {
				id = candidate;
				file = p;
				break;
			}
		}
		if (file == null) throw new IOException("keine freie Foto-ID");

		byte[] png = PngWriter.write(size[0], size[1], pixels);
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) Files.createDirectories(parent);
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.write(tmp, png);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}

		int[] small = PhotoFrame.outSize(size[0], size[1], PhotoFrame.MAX_PREVIEW_SIDE);
		int[] preview = PhotoFrame.render(pixels, size[0], size[1], new int[]{0, 0, size[0], size[1]}, small[0], small[1]);
		return new Shot(ticket, id + ".png", file, size[0], size[1], preview, small[0], small[1], selfie, portrait, false);
	}

	private synchronized int nextSalt() {
		return shotSalt++;
	}

	/** Render-Thread: das nächste fertige Foto, oder null, solange der Hintergrund noch rechnet. */
	public Shot pollShot() {
		return shots.poll();
	}

	/**
	 * Ein Foto, das nicht gepostet wurde, wieder löschen („Nochmal“, Handy weggesteckt). Fotos, die zu einem Post
	 * gehören, bleiben.
	 */
	public void discard(final Shot shot) {
		if (shot == null || shot.failed || shot.file == null) return;
		Runnable job = new Runnable() {
			@Override
			public void run() {
				delete(shot);
			}
		};
		try {
			worker().execute(job);
		} catch (RejectedExecutionException e) {
			job.run();
		}
	}

	/** Datei eines nicht geposteten Fotos löschen (wartet auf die Platte). Fotos, die zu einem Post gehören, bleiben. */
	private void delete(Shot shot) {
		if (shot == null || shot.failed || shot.file == null) return;
		for (ReelsPost p : backend.feed()) {
			if (shot.fileName != null && shot.fileName.equals(p.photo)) return;
		}
		try {
			Files.deleteIfExists(shot.file);
		} catch (IOException | RuntimeException e) {
			log.accept("MC-Reels: Foto nicht gelöscht: " + e);
		}
	}

	// --- Feed ändern (und gleich im Hintergrund sichern) ---

	/** Foto posten: steht danach vorn im Feed. Rückgabe: der Post oder null (Foto fehlgeschlagen). */
	public ReelsPost post(Shot shot, String caption, String author, long now) {
		return post(shot, caption, author, now, null);
	}

	/** Foto posten und dazu festhalten, womit es aufgenommen wurde ({@code meta}, darf null sein). */
	public ReelsPost post(Shot shot, String caption, String author, long now, ReelsMeta meta) {
		if (shot == null || shot.failed || shot.fileName == null) return null;
		ReelsPost p = backend.post(shot.fileName, shot.width, shot.height, shot.portrait, shot.selfie, caption, author, now,
				meta);
		if (p != null) saveLater();
		return p;
	}

	/** Like umschalten. Rückgabe: der neue Zustand. */
	public boolean toggleLike(String postId) {
		boolean liked = backend.toggleLike(postId);
		saveLater();
		return liked;
	}

	/** Kommentar anhängen; null, wenn der Text leer ist oder es den Post nicht gibt. */
	public ReelsComment comment(String postId, String author, String text, long now) {
		ReelsComment c = backend.comment(postId, author, text, now);
		if (c != null) saveLater();
		return c;
	}

	/** Auf einen Kommentar antworten; null, wenn der Text leer ist oder es den Post nicht gibt. */
	public ReelsComment reply(String postId, String parentId, String author, String text, long now) {
		ReelsComment c = backend.reply(postId, parentId, author, text, now);
		if (c != null) saveLater();
		return c;
	}

	/**
	 * Den Text eines eigenen Kommentars ändern. Rückgabe: der Kommentar (bei unverändertem Text ohne Änderung) oder
	 * null (fremd, unbekannt oder leerer Text).
	 */
	public ReelsComment editComment(String postId, String commentId, String author, String text, long now) {
		List<ReelsPost> before = backend.feed();
		ReelsComment c = backend.editComment(postId, commentId, author, text, now);
		if (c != null && backend.feed() != before) saveLater();
		return c;
	}

	/** Erwähnungen und Antworten unter diesem Post als gesehen merken. Rückgabe: wie viele es waren. */
	public int markPingsSeen(String postId, String self) {
		int n = backend.markPingsSeen(postId, self);
		if (n > 0) saveLater();
		return n;
	}

	// --- Streak-Schutz ---

	/** Einen Tag der Streak schützen: heute („pausieren“) oder gestern („retten“). */
	public StreakGuard.Result protect(boolean yesterday, long now) {
		StreakGuard.Result r = backend.protect(yesterday, now);
		if (r == StreakGuard.Result.OK) saveLater();
		return r;
	}

	/**
	 * Das Handy wurde herausgeholt: der Feed merkt sich den Kalendertag ({@link ReelsBackend#noteClock}). War es der
	 * erste Blick an diesem Tag, wird gleich gesichert – sonst ließe sich der gemerkte Tag durch ein hartes Beenden des
	 * Spiels wieder loswerden.
	 */
	public void noteClock(long now) {
		LocalReelsBackend local = backend instanceof LocalReelsBackend ? (LocalReelsBackend) backend : null;
		boolean clean = local != null && !local.dirty();
		backend.noteClock(now);
		if (clean && local.dirty()) saveLater();
	}

	/** Der Kalendertag (Tage seit 1970) eines Zeitpunkts in der Zeitzone dieses Dienstes. */
	public long epochDay(long now) {
		return ReelsTime.epochDay(now, zone.getOffset(now));
	}

	// --- Gedächtnis dieser Sitzung (Meldungen nicht bei jedem Herausholen wiederholen) ---

	/**
	 * Soll die Zahl der ungesehenen Erwähnungen und Antworten jetzt gemeldet werden? Ja, wenn sie seit der letzten
	 * Abfrage gestiegen ist – also einmal nach dem Spielstart und wieder, wenn eine neue dazukommt, aber nicht bei
	 * jedem Herausholen des Handys. Die Zahl wird dabei gemerkt.
	 */
	public synchronized boolean announcePings(int unseen) {
		boolean rose = unseen > announcedPings;
		announcedPings = unseen;
		return rose;
	}

	/** Soll „gestern lässt sich retten“ jetzt gemeldet werden? Ja beim ersten Aufruf für diesen Tag. */
	public synchronized boolean hintRescue(long epochDay) {
		if (epochDay == rescueHintDay) return false;
		rescueHintDay = epochDay;
		return true;
	}

	// --- Sichern ---

	/** Jetzt sichern (Handy weggesteckt, Spiel wird beendet). Fehler werden nur gemeldet. */
	public void save() {
		try {
			backend.flush();
		} catch (IOException | RuntimeException e) {
			long now = System.currentTimeMillis();
			// Eine volle Platte soll das Protokoll nicht fluten.
			if (now - lastSaveError > 30000L) {
				lastSaveError = now;
				log.accept("MC-Reels: Feed nicht gespeichert: " + e);
			}
		}
	}

	/**
	 * Das Spiel wird beendet: auf kein Foto wartet mehr jemand. Kurz abwarten, was der Hintergrund-Thread gerade noch
	 * schreibt oder löscht (höchstens {@code waitMs}), liegengebliebene Fotos ohne Post löschen und den Feed sichern.
	 */
	public void stop(long waitMs) {
		wanted = 0L;
		Executor w;
		synchronized (this) {
			w = worker;
		}
		if (w instanceof ExecutorService && waitMs > 0) {
			try {
				Future<?> idle = ((ExecutorService) w).submit(new Runnable() {
					@Override
					public void run() {
					}
				});
				idle.get(waitMs, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				// Zu langsam oder schon beendet: der Feed wird trotzdem gesichert.
			}
		}
		Shot left;
		while ((left = shots.poll()) != null) delete(left);
		save();
	}

	/** Wie {@link #stop(long)} mit zwei Sekunden Geduld. */
	public void stop() {
		stop(2000L);
	}

	/** Im Hintergrund sichern – nach jedem Post, Like und Kommentar, damit ein Absturz nichts kostet. */
	public void saveLater() {
		try {
			worker().execute(new Runnable() {
				@Override
				public void run() {
					save();
				}
			});
		} catch (RejectedExecutionException e) {
			save();
		}
	}
}
