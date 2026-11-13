package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.wardrobe.PngWriter;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * MC-Reels – der Dienst des laufenden Spiels: der Feed ({@link ReelsBackend}, vorerst nur lokal), Fotos speichern
 * (Ausschnitt, Verkleinern, PNG – im Hintergrund) und die Sperre der Handy-Taste. Die Oberfläche liegt in
 * {@code core.ui.reels}; der Baum ruft nur {@link #init} und beim Beenden {@link #save()}.
 *
 * <p>Kein Netz: alles bleibt im Ordner, den {@link #init} bekommt. Nichts hier wirft Fehler zum Aufrufer – sie werden
 * gemeldet ({@code log}) und ein Foto, das nicht gespeichert werden konnte, kommt als {@link Shot#failed} zurück.
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

		Shot(String fileName, Path file, int width, int height, int[] preview, int previewW, int previewH, boolean selfie,
				boolean portrait, boolean failed) {
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

		static Shot failed(boolean selfie, boolean portrait) {
			return new Shot(null, null, 0, 0, null, 0, 0, selfie, portrait, true);
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
	 */
	public void savePhoto(final int[] argb, final int w, final int h, final PhoneHold hold, final boolean selfie,
			final Consumer<Shot> done) {
		final boolean portrait = hold == PhoneHold.PORTRAIT;
		final long now = System.currentTimeMillis();
		Runnable job = new Runnable() {
			@Override
			public void run() {
				Shot shot;
				try {
					shot = write(argb, w, h, hold, selfie, now);
				} catch (IOException | RuntimeException | OutOfMemoryError e) {
					log.accept("MC-Reels: Foto nicht gespeichert: " + e);
					shot = Shot.failed(selfie, portrait);
				}
				deliver(shot, done);
			}
		};
		try {
			worker().execute(job);
		} catch (RejectedExecutionException e) {
			deliver(Shot.failed(selfie, portrait), done);
		}
	}

	private void deliver(Shot shot, Consumer<Shot> done) {
		shots.add(shot);
		if (done == null) return;
		try {
			done.accept(shot);
		} catch (RuntimeException e) {
			log.accept("MC-Reels: " + e);
		}
	}

	private Shot write(int[] argb, int w, int h, PhoneHold hold, boolean selfie, long now) throws IOException {
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
		return new Shot(id + ".png", file, size[0], size[1], preview, small[0], small[1], selfie, portrait, false);
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
		for (ReelsPost p : backend.feed()) {
			if (shot.fileName != null && shot.fileName.equals(p.photo)) return;
		}
		Runnable job = new Runnable() {
			@Override
			public void run() {
				try {
					Files.deleteIfExists(shot.file);
				} catch (IOException | RuntimeException e) {
					log.accept("MC-Reels: Foto nicht gelöscht: " + e);
				}
			}
		};
		try {
			worker().execute(job);
		} catch (RejectedExecutionException e) {
			job.run();
		}
	}

	// --- Feed ändern (und gleich im Hintergrund sichern) ---

	/** Foto posten: steht danach vorn im Feed. Rückgabe: der Post oder null (Foto fehlgeschlagen). */
	public ReelsPost post(Shot shot, String caption, String author, long now) {
		if (shot == null || shot.failed || shot.fileName == null) return null;
		ReelsPost p = backend.post(shot.fileName, shot.width, shot.height, shot.portrait, shot.selfie, caption, author, now);
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
