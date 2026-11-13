package dev.theredstonee.trsclient.core.reels;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.theredstonee.trsclient.core.config.ConfigStore;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * Der Feed auf diesem PC, ohne Netz: {@code <Ordner>/feed.json} (Posts mit Text, Likes, Kommentaren und die Tage mit
 * einem Post für die Streak) und {@code <Ordner>/photos/<id>.png}.
 *
 * <p>Geschrieben wird atomar (wie die Notizen) und nur bei Änderungen. Eine {@code feed.json}, die sich nicht lesen
 * lässt, bleibt als {@code feed.json.broken} erhalten, bevor sie zum ersten Mal überschrieben wird. Im Zweitfenster
 * derselben Instanz ({@link ConfigStore#readOnly()}) wird {@code feed.json} nie geschrieben.
 *
 * <p>Alle Methoden sind synchronisiert: geändert wird im Spiel-Thread, gesichert im Hintergrund ({@link Reels#worker()}).
 */
public final class LocalReelsBackend implements ReelsBackend {
	static final String FEED = "feed.json";
	static final String BROKEN = "feed.json.broken";
	static final String PHOTOS = "photos";
	/** Höchstens so viele Kommentare je Post. */
	static final int MAX_COMMENTS = 500;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Die Datei (Gson-DTO). */
	static final class FeedFile {
		int version = 1;
		List<ReelsPost> posts = new ArrayList<ReelsPost>();
		/** Kalendertage (seit 1970, in der Zeitzone des Spielers) mit mindestens einem Post. */
		List<Long> postDays = new ArrayList<Long>();
	}

	private final Path dir;
	private final TimeZone zone;
	/** Neueste zuerst. */
	private final List<ReelsPost> posts = new ArrayList<ReelsPost>();
	private final List<Long> postDays = new ArrayList<Long>();
	/** Die Liste, die {@link #feed()} herausgibt – nach jeder Änderung eine neue. */
	private List<ReelsPost> view = Collections.emptyList();
	private boolean dirty;
	/** {@code feed.json} war da, ließ sich aber nicht lesen: vor dem ersten Schreiben sichern. */
	private boolean brokenPending;
	private int idSalt = (int) (System.nanoTime() & 0xFFFF);
	/** Zweitfenster: nichts schreiben (Tests ersetzen die Abfrage). */
	java.util.function.BooleanSupplier readOnly = new java.util.function.BooleanSupplier() {
		@Override
		public boolean getAsBoolean() {
			return ConfigStore.readOnly();
		}
	};

	/**
	 * @param dir  Ordner des Feeds (z. B. {@code <Spielordner>/trs-reels}); null = nur im Speicher
	 * @param zone Zeitzone für die Kalendertage der Streak
	 */
	public LocalReelsBackend(Path dir, TimeZone zone) {
		this.dir = dir;
		this.zone = zone != null ? zone : TimeZone.getDefault();
		load();
	}

	public Path dir() {
		return dir;
	}

	private void load() {
		if (dir == null) return;
		Path file = dir.resolve(FEED);
		if (!Files.isRegularFile(file)) return;
		FeedFile f = null;
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			f = GSON.fromJson(r, FeedFile.class);
		} catch (IOException | RuntimeException e) {
			f = null;
		}
		if (f == null || f.posts == null) {
			brokenPending = true;
			return;
		}
		Set<String> seen = new HashSet<String>();
		for (ReelsPost p : f.posts) {
			ReelsPost ok = normalized(p);
			if (ok != null && seen.add(ok.id)) posts.add(ok);
		}
		sortNewestFirst(posts);
		if (f.postDays != null) {
			for (Long d : f.postDays) if (d != null && !postDays.contains(d)) postDays.add(d);
		}
		// Jeder vorhandene Post zählt für seinen Tag (auch wenn die Liste der Tage fehlt oder gekürzt wurde).
		for (ReelsPost p : posts) addDay(p.createdAt);
		Collections.sort(postDays);
		refresh();
	}

	/** Ein gelesener Post in brauchbarer Form oder null (ohne gültige ID bzw. gültigen Foto-Namen). */
	private static ReelsPost normalized(ReelsPost p) {
		if (p == null || !ReelsPost.validId(p.id) || !ReelsPost.validPhotoName(p.photo)) return null;
		p.author = ReelsPost.clean(p.author, ReelsPost.MAX_AUTHOR);
		p.caption = ReelsPost.clean(p.caption, ReelsPost.MAX_CAPTION);
		p.width = Math.max(1, p.width);
		p.height = Math.max(1, p.height);
		p.likes = Math.max(p.likedByMe ? 1 : 0, p.likes);
		List<ReelsComment> comments = new ArrayList<ReelsComment>();
		if (p.comments != null) {
			for (ReelsComment c : p.comments) {
				if (c == null || comments.size() >= MAX_COMMENTS) continue;
				String text = ReelsPost.clean(c.text, ReelsComment.MAX_TEXT);
				if (text.isEmpty()) continue;
				comments.add(new ReelsComment(ReelsPost.clean(c.author, ReelsPost.MAX_AUTHOR), text, c.at));
			}
		}
		p.comments = comments;
		return p;
	}

	private static void sortNewestFirst(List<ReelsPost> list) {
		Collections.sort(list, new Comparator<ReelsPost>() {
			@Override
			public int compare(ReelsPost a, ReelsPost b) {
				if (a.createdAt != b.createdAt) return a.createdAt > b.createdAt ? -1 : 1;
				return b.id.compareTo(a.id);
			}
		});
	}

	private void addDay(long millis) {
		Long day = Long.valueOf(ReelsTime.epochDay(millis, zone.getOffset(millis)));
		if (!postDays.contains(day)) postDays.add(day);
	}

	private void refresh() {
		view = Collections.unmodifiableList(new ArrayList<ReelsPost>(posts));
	}

	private void changed() {
		dirty = true;
		refresh();
	}

	private ReelsPost byId(String id) {
		if (id == null) return null;
		for (ReelsPost p : posts) if (id.equals(p.id)) return p;
		return null;
	}

	// --- ReelsBackend ---

	@Override
	public synchronized List<ReelsPost> feed() {
		return view;
	}

	@Override
	public synchronized ReelsPost post(String photoFileName, int width, int height, boolean portrait, boolean selfie,
			String caption, String author, long now) {
		if (!ReelsPost.validPhotoName(photoFileName)) return null;
		ReelsPost p = new ReelsPost();
		// Die ID ist der Name des Fotos; ein zweiter Post mit demselben Foto bekäme eine eigene.
		String id = photoFileName.substring(0, photoFileName.length() - 4);
		p.id = byId(id) == null ? id : newId(now);
		p.photo = photoFileName;
		p.width = Math.max(1, width);
		p.height = Math.max(1, height);
		p.portrait = portrait;
		p.selfie = selfie;
		p.caption = ReelsPost.clean(caption, ReelsPost.MAX_CAPTION);
		p.author = ReelsPost.clean(author, ReelsPost.MAX_AUTHOR);
		p.createdAt = now;
		posts.add(0, p);
		sortNewestFirst(posts);
		addDay(now);
		Collections.sort(postDays);
		changed();
		return p;
	}

	@Override
	public synchronized boolean toggleLike(String postId) {
		ReelsPost p = byId(postId);
		if (p == null) return false;
		p.likedByMe = !p.likedByMe;
		p.likes = Math.max(0, p.likes + (p.likedByMe ? 1 : -1));
		changed();
		return p.likedByMe;
	}

	@Override
	public synchronized ReelsComment comment(String postId, String author, String text, long now) {
		ReelsPost p = byId(postId);
		String clean = ReelsPost.clean(text, ReelsComment.MAX_TEXT);
		if (p == null || clean.isEmpty() || p.comments.size() >= MAX_COMMENTS) return null;
		ReelsComment c = new ReelsComment(ReelsPost.clean(author, ReelsPost.MAX_AUTHOR), clean, now);
		p.comments.add(c);
		changed();
		return c;
	}

	@Override
	public synchronized int streak(long now) {
		return ReelsTime.streak(postDays, ReelsTime.epochDay(now, zone.getOffset(now)));
	}

	@Override
	public Path photoFile(ReelsPost post) {
		if (dir == null || post == null || !ReelsPost.validPhotoName(post.photo)) return null;
		return dir.resolve(PHOTOS).resolve(post.photo);
	}

	@Override
	public Path newPhotoFile(String id) {
		if (dir == null || !ReelsPost.validId(id)) return null;
		return dir.resolve(PHOTOS).resolve(id + ".png");
	}

	/** Neue, im Feed noch nicht vergebene ID zum Zeitpunkt {@code now}. */
	public synchronized String newId(long now) {
		String id;
		do {
			id = ReelsPost.newId(now, zone, idSalt++);
		} while (byId(id) != null);
		return id;
	}

	public synchronized boolean dirty() {
		return dirty;
	}

	@Override
	public synchronized void flush() throws IOException {
		if (!dirty || dir == null || readOnly.getAsBoolean()) return;
		Path file = dir.resolve(FEED);
		if (brokenPending) {
			// Die unlesbare Datei aufheben, bevor sie überschrieben wird.
			if (Files.isRegularFile(file)) Files.copy(file, dir.resolve(BROKEN), StandardCopyOption.REPLACE_EXISTING);
			brokenPending = false;
		}
		FeedFile f = new FeedFile();
		f.posts = new ArrayList<ReelsPost>(posts);
		f.postDays = new ArrayList<Long>(postDays);
		write(file, f);
		dirty = false;
	}

	private static void write(Path file, Object data) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) Files.createDirectories(parent);
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(data, w);
		}
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
