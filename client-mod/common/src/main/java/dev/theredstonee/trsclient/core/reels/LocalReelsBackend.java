package dev.theredstonee.trsclient.core.reels;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.theredstonee.trsclient.core.config.ConfigStore;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Der Feed auf diesem PC, ohne Netz: {@code <Ordner>/feed.json} (Posts mit Text, Likes und Kommentaren) und
 * {@code <Ordner>/photos/<id>.png}. Die Tage der Streak stehen nicht in der Datei: sie ergeben sich beim Laden aus den
 * Posts, in der Zeitzone, die dann gilt – so zählt ein Post nach einem Wechsel der Zeitzone nie für zwei Tage.
 *
 * <p>Dazu kommen, nur wenn es sie gibt: die eingelösten Schutztage der Streak ({@code freezes}, als Kalenderdatum, wie
 * der Spieler es gesehen hat) und der späteste Tag, an dem das Handy herausgeholt wurde ({@code clockDay}). Alle neueren
 * Felder sind freiwillig – eine ältere {@code feed.json} lädt unverändert.
 *
 * <p>Geschrieben wird atomar (wie die Notizen) und nur bei Änderungen. Eine {@code feed.json}, die sich nicht lesen
 * lässt, bleibt als {@code feed.json.broken} erhalten, bevor sie zum ersten Mal überschrieben wird. Im Zweitfenster
 * derselben Instanz ({@link ConfigStore#readOnly()}) wird {@code feed.json} nie geschrieben ({@link #readOnly()}).
 *
 * <p>Geändert wird im Spiel-Thread, gesichert im Hintergrund ({@link Reels#worker()}). Die Sperre des Feeds gilt beim
 * Sichern nur für die Kopie des Stands – geschrieben wird danach ohne sie, damit {@link #feed()} und die Änderungen im
 * Spiel-Thread nie auf die Platte warten.
 */
public final class LocalReelsBackend implements ReelsBackend {
	static final String FEED = "feed.json";
	static final String BROKEN = "feed.json.broken";
	static final String PHOTOS = "photos";
	/** Höchstens so viele Kommentare je Post. */
	static final int MAX_COMMENTS = 500;
	/** Höchstens so viele Schutztage bleiben in der Datei (zwei je Monat über zehn Jahre). */
	static final int MAX_FREEZES = 240;
	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Die Datei (Gson-DTO). */
	static final class FeedFile {
		int version = 1;
		List<ReelsPost> posts = new ArrayList<ReelsPost>();
		/** Eingelöste Schutztage der Streak – null, solange keiner eingelöst wurde. */
		List<ReelsFreeze> freezes;
		/** Spätester Kalendertag ({@code yyyy-MM-dd}), an dem das Handy herausgeholt wurde – oder null. */
		String clockDay;
	}

	private final Path dir;
	private final TimeZone zone;
	/** Neueste zuerst. */
	private final List<ReelsPost> posts = new ArrayList<ReelsPost>();
	/** Kalendertage (seit 1970, in {@link #zone}) mit mindestens einem Post – aus den Posts gerechnet. */
	private final List<Long> postDays = new ArrayList<Long>();
	/** Eingelöste Schutztage, in der Reihenfolge des Einlösens. Sie werden nie wieder entfernt. */
	private final List<ReelsFreeze> freezes = new ArrayList<ReelsFreeze>();
	/** Die geschützten Kalendertage (seit 1970) – aus {@link #freezes} gerechnet. */
	private final List<Long> frozenDays = new ArrayList<Long>();
	/** Spätester Kalendertag, an dem das Handy herausgeholt wurde ({@code yyyy-MM-dd}), oder null. */
	private String clockDay;
	/** Die Liste, die {@link #feed()} herausgibt – nach jeder Änderung eine neue. */
	private volatile List<ReelsPost> view = Collections.emptyList();
	/** Nur ein Schreiber zugleich (zwei Sicherungen dürfen sich auf der Platte nicht überholen). */
	private final Object writeLock = new Object();
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
	 * @param dir  Ordner des Feeds (z. B. {@code config/trsclient/reels}); null = nur im Speicher
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
			JsonElement tree = new JsonParser().parse(r);
			lenient(tree);
			f = GSON.fromJson(tree, FeedFile.class);
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
		// Jeder Post zählt für seinen Tag in der Zeitzone von jetzt. Eine früher gespeicherte Liste der Tage wird nicht
		// gelesen: sie stammte womöglich aus einer anderen Zeitzone und gäbe demselben Post einen zweiten Tag.
		for (ReelsPost p : posts) addDay(p.createdAt);
		Collections.sort(postDays);
		loadFreezes(f.freezes);
		if (ReelsTime.civilDay(f.clockDay) != Long.MIN_VALUE) clockDay = f.clockDay;
		refresh();
	}

	/**
	 * Die neueren, freiwilligen Felder ({@code freezes}, {@code clockDay}, {@code meta} eines Posts, {@code replyTo},
	 * {@code editedAt} und {@code seen} eines Kommentars) mit falschem JSON-Typ entfernen, bevor Gson die Datei bindet:
	 * ein falscher Wert kostet nur diesen Wert, nicht den ganzen Feed.
	 */
	static void lenient(JsonElement tree) {
		if (tree == null || !tree.isJsonObject()) return;
		JsonObject root = tree.getAsJsonObject();
		JsonElement freezes = root.get("freezes");
		if (freezes != null && !freezes.isJsonNull()) {
			if (!freezes.isJsonArray()) {
				root.remove("freezes");
			} else {
				JsonArray kept = new JsonArray();
				for (JsonElement e : freezes.getAsJsonArray()) {
					if (e == null || !e.isJsonObject()) continue;
					JsonObject o = e.getAsJsonObject();
					dropUnless(o, "day", true, false, false);
					dropUnless(o, "usedAt", false, true, false);
					kept.add(o);
				}
				root.add("freezes", kept);
			}
		}
		dropUnless(root, "clockDay", true, false, false);
		JsonElement posts = root.get("posts");
		if (posts == null || !posts.isJsonArray()) return;
		for (JsonElement pe : posts.getAsJsonArray()) {
			if (pe == null || !pe.isJsonObject()) continue;
			JsonObject post = pe.getAsJsonObject();
			JsonElement meta = post.get("meta");
			if (meta != null && !meta.isJsonNull()) {
				if (!meta.isJsonObject()) {
					post.remove("meta");
				} else {
					JsonObject m = meta.getAsJsonObject();
					for (String key : new String[]{"mc", "loader", "shader", "pack"}) dropUnless(m, key, true, false, false);
				}
			}
			JsonElement comments = post.get("comments");
			if (comments == null || !comments.isJsonArray()) continue;
			for (JsonElement ce : comments.getAsJsonArray()) {
				if (ce == null || !ce.isJsonObject()) continue;
				JsonObject c = ce.getAsJsonObject();
				dropUnless(c, "replyTo", true, false, false);
				dropUnless(c, "editedAt", false, true, false);
				dropUnless(c, "seen", false, false, true);
			}
		}
	}

	/** Entfernt {@code key} aus {@code o}, wenn der Wert weder null noch vom erlaubten Typ ist. */
	private static void dropUnless(JsonObject o, String key, boolean string, boolean number, boolean bool) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) return;
		if (string && isString(e) || number && isNumber(e) || bool && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return;
		o.remove(key);
	}

	private static boolean isString(JsonElement e) {
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
	}

	private static boolean isNumber(JsonElement e) {
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
	}

	/**
	 * Schutztage aus der Datei: nur gültige Daten, jeden Tag einmal, in der Reihenfolge des Einlösens – und auch aus
	 * einer von Hand geänderten Datei nie mehr als {@link StreakGuard#PER_MONTH} je Monat.
	 */
	private void loadFreezes(List<ReelsFreeze> raw) {
		if (raw == null) return;
		List<ReelsFreeze> valid = new ArrayList<ReelsFreeze>();
		Set<String> days = new HashSet<String>();
		for (ReelsFreeze f : raw) {
			if (f == null || ReelsTime.civilDay(f.day) == Long.MIN_VALUE || !days.add(f.day)) continue;
			valid.add(new ReelsFreeze(f.day, f.usedAt));
		}
		Collections.sort(valid, new Comparator<ReelsFreeze>() {
			@Override
			public int compare(ReelsFreeze a, ReelsFreeze b) {
				if (a.usedAt != b.usedAt) return a.usedAt < b.usedAt ? -1 : 1;
				return a.day.compareTo(b.day);
			}
		});
		Map<String, Integer> perMonth = new HashMap<String, Integer>();
		List<ReelsFreeze> kept = new ArrayList<ReelsFreeze>();
		for (ReelsFreeze f : valid) {
			String month = f.day.substring(0, 7);
			Integer used = perMonth.get(month);
			int n = used == null ? 0 : used.intValue();
			if (n >= StreakGuard.PER_MONTH) continue;
			perMonth.put(month, Integer.valueOf(n + 1));
			kept.add(f);
		}
		// Bei mehr als der Höchstzahl fallen die ältesten weg.
		for (int i = Math.max(0, kept.size() - MAX_FREEZES); i < kept.size(); i++) addFreeze(kept.get(i));
	}

	private void addFreeze(ReelsFreeze f) {
		freezes.add(f);
		Long day = Long.valueOf(ReelsTime.civilDay(f.day));
		if (!frozenDays.contains(day)) frozenDays.add(day);
	}

	/** Ein gelesener Post in brauchbarer Form oder null (ohne gültige ID bzw. gültigen Foto-Namen). */
	private static ReelsPost normalized(ReelsPost p) {
		if (p == null || !ReelsPost.validId(p.id) || !ReelsPost.validPhotoName(p.photo)) return null;
		p.author = ReelsPost.clean(p.author, ReelsPost.MAX_AUTHOR);
		p.caption = ReelsPost.clean(p.caption, ReelsPost.MAX_CAPTION);
		p.width = Math.max(1, p.width);
		p.height = Math.max(1, p.height);
		p.likes = Math.max(p.likedByMe ? 1 : 0, p.likes);
		p.meta = ReelsMeta.normalized(p.meta);
		p.comments = normalizedComments(p.comments);
		return p;
	}

	/**
	 * Die gelesenen Kommentare eines Posts in brauchbarer Form. Jeder bekommt eine ID: fehlt sie (ältere Datei) oder ist
	 * sie ungültig oder doppelt, ergibt sie sich aus Zeit und Stelle des Kommentars – bei jedem Laden derselben Datei
	 * dieselbe, damit Antworten bis zum nächsten Schreiben nicht auf einen anderen Kommentar zeigen. Antworten gibt es
	 * nur eine Ebene tief: zeigt {@code replyTo} auf eine Antwort, hängt der Kommentar danach an deren Kommentar der
	 * obersten Ebene; zeigt es ins Leere, auf sich selbst oder im Kreis, ist es ein Kommentar der obersten Ebene.
	 */
	private static List<ReelsComment> normalizedComments(List<ReelsComment> raw) {
		List<ReelsComment> comments = new ArrayList<ReelsComment>();
		if (raw == null) return comments;
		Set<String> ids = new HashSet<String>();
		for (ReelsComment c : raw) {
			if (c == null || comments.size() >= MAX_COMMENTS) continue;
			String text = ReelsPost.clean(c.text, ReelsComment.MAX_TEXT);
			if (text.isEmpty()) continue;
			ReelsComment n = new ReelsComment(ReelsPost.clean(c.author, ReelsPost.MAX_AUTHOR), text, c.at);
			if (ReelsPost.validId(c.id) && ids.add(c.id)) n.id = c.id;
			n.replyTo = c.replyTo;
			n.editedAt = c.editedAt != null && c.editedAt.longValue() > 0L ? c.editedAt : null;
			n.seen = Boolean.TRUE.equals(c.seen) ? Boolean.TRUE : null;
			comments.add(n);
		}
		Map<String, ReelsComment> byId = new HashMap<String, ReelsComment>();
		for (int i = 0; i < comments.size(); i++) {
			ReelsComment c = comments.get(i);
			if (c.id == null) c.id = derivedId(c.at, i, ids);
			byId.put(c.id, c);
		}
		for (ReelsComment c : comments) {
			if (c.replyTo == null) continue;
			ReelsComment parent = byId.get(c.replyTo);
			if (parent == null || parent == c) c.replyTo = null;
		}
		// Ketten auflösen – erst rechnen, dann setzen, damit jede Kette am ursprünglichen Stand entlangläuft.
		String[] root = new String[comments.size()];
		for (int i = 0; i < comments.size(); i++) {
			ReelsComment c = comments.get(i);
			if (c.replyTo == null) continue;
			ReelsComment top = byId.get(c.replyTo);
			int steps = 0;
			while (top.replyTo != null && steps++ <= comments.size()) top = byId.get(top.replyTo);
			root[i] = top.replyTo == null && top != c ? top.id : null;
		}
		for (int i = 0; i < comments.size(); i++) comments.get(i).replyTo = root[i];
		return comments;
	}

	/** ID für einen Kommentar ohne eigene: aus seiner Zeit (UTC) und seiner Stelle, im Post noch nicht vergeben. */
	private static String derivedId(long at, int index, Set<String> taken) {
		// Eine Zeit außerhalb der Jahre 0000–9999 ergäbe keine gültige ID.
		long base = ReelsPost.validId(ReelsPost.newId(at, UTC, 0)) ? at : 0L;
		int salt = index;
		String id;
		do {
			id = ReelsPost.newId(base, UTC, salt++);
		} while (!taken.add(id));
		return id;
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

	private static ReelsComment commentById(ReelsPost p, String id) {
		if (p == null || id == null) return null;
		for (ReelsComment c : p.comments) if (id.equals(c.id)) return c;
		return null;
	}

	/** Neue, in diesem Post noch nicht vergebene Kommentar-ID zum Zeitpunkt {@code now}. */
	private String newCommentId(ReelsPost p, long now) {
		String id;
		do {
			id = ReelsPost.newId(now, zone, idSalt++);
		} while (commentById(p, id) != null);
		return id;
	}

	private long today(long now) {
		return ReelsTime.epochDay(now, zone.getOffset(now));
	}

	// --- ReelsBackend ---

	@Override
	public List<ReelsPost> feed() {
		return view;
	}

	@Override
	public boolean readOnly() {
		return dir != null && readOnly.getAsBoolean();
	}

	@Override
	public ReelsPost post(String photoFileName, int width, int height, boolean portrait, boolean selfie, String caption,
			String author, long now) {
		return post(photoFileName, width, height, portrait, selfie, caption, author, now, null);
	}

	@Override
	public synchronized ReelsPost post(String photoFileName, int width, int height, boolean portrait, boolean selfie,
			String caption, String author, long now, ReelsMeta meta) {
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
		p.meta = ReelsMeta.normalized(meta);
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
		return add(postId, null, author, text, now);
	}

	@Override
	public synchronized ReelsComment reply(String postId, String parentId, String author, String text, long now) {
		return add(postId, parentId, author, text, now);
	}

	private ReelsComment add(String postId, String parentId, String author, String text, long now) {
		ReelsPost p = byId(postId);
		String clean = ReelsPost.clean(text, ReelsComment.MAX_TEXT);
		if (p == null || clean.isEmpty() || p.comments.size() >= MAX_COMMENTS) return null;
		// Nur eine Ebene: die Antwort auf eine Antwort hängt an deren Kommentar der obersten Ebene.
		ReelsComment parent = commentById(p, parentId);
		if (parent != null && parent.replyTo != null) parent = commentById(p, parent.replyTo);
		ReelsComment c = new ReelsComment(ReelsPost.clean(author, ReelsPost.MAX_AUTHOR), clean, now);
		c.id = newCommentId(p, now);
		c.replyTo = parent == null ? null : parent.id;
		p.comments.add(c);
		changed();
		return c;
	}

	@Override
	public synchronized ReelsComment editComment(String postId, String commentId, String author, String text, long now) {
		ReelsComment c = commentById(byId(postId), commentId);
		String by = ReelsPost.clean(author, ReelsPost.MAX_AUTHOR);
		if (c == null || by.isEmpty() || !by.equalsIgnoreCase(c.author)) return null;
		String clean = ReelsPost.clean(text, ReelsComment.MAX_TEXT);
		if (clean.isEmpty()) return null;
		if (clean.equals(c.text)) return c;
		c.text = clean;
		c.editedAt = Long.valueOf(now);
		changed();
		return c;
	}

	@Override
	public synchronized int markPingsSeen(String postId, String self) {
		ReelsPost p = byId(postId);
		if (p == null) return 0;
		int n = 0;
		for (ReelsComment c : p.comments) {
			if (!Pings.counts(p, c, self)) continue;
			c.seen = Boolean.TRUE;
			n++;
		}
		if (n > 0) changed();
		return n;
	}

	@Override
	public synchronized int streak(long now) {
		return ReelsTime.streak(postDays, frozenDays, today(now));
	}

	@Override
	public synchronized int streakAfterPost(long now) {
		long today = today(now);
		List<Long> days = new ArrayList<Long>(postDays);
		days.add(Long.valueOf(today));
		return ReelsTime.streak(days, frozenDays, today);
	}

	// --- Streak-Schutz ---

	/**
	 * Der neueste bekannte Tag: der späteste Post, der späteste geschützte Tag, der Tag, an dem zuletzt ein Schutz
	 * eingelöst wurde, und der späteste Tag, an dem das Handy herausgeholt wurde. Liegt „heute“ davor, wurde die Uhr
	 * zurückgestellt. {@link Long#MIN_VALUE}, wenn es nichts davon gibt.
	 */
	private long newestEventDay() {
		long newest = Long.MIN_VALUE;
		for (Long d : postDays) newest = Math.max(newest, d.longValue());
		for (Long d : frozenDays) newest = Math.max(newest, d.longValue());
		for (ReelsFreeze f : freezes) newest = Math.max(newest, today(f.usedAt));
		long clock = ReelsTime.civilDay(clockDay);
		if (clock != Long.MIN_VALUE) newest = Math.max(newest, clock);
		return newest;
	}

	@Override
	public synchronized StreakGuard streakGuard(long now) {
		return StreakGuard.of(postDays, freezes, today(now), newestEventDay(), readOnly());
	}

	@Override
	public synchronized StreakGuard.Result protect(boolean yesterday, long now) {
		// Alles noch einmal prüfen – der Stand, den die Oberfläche zeigt, kann einen Augenblick alt sein.
		StreakGuard g = streakGuard(now);
		if (g.readOnly) return StreakGuard.Result.READ_ONLY;
		if (g.clockBehind) return StreakGuard.Result.CLOCK;
		long today = today(now);
		long day;
		if (yesterday) {
			if (!g.rescueOffered) return StreakGuard.Result.NOT_NEEDED;
			if (g.leftForYesterday <= 0) return StreakGuard.Result.NONE_LEFT;
			day = today - 1;
		} else {
			if (g.postedToday) return StreakGuard.Result.POSTED_TODAY;
			if (g.todayFrozen) return StreakGuard.Result.ALREADY;
			if (g.streak <= 0) return StreakGuard.Result.NO_STREAK;
			if (g.leftThisMonth <= 0) return StreakGuard.Result.NONE_LEFT;
			day = today;
		}
		addFreeze(new ReelsFreeze(ReelsTime.civil(day), now));
		changed();
		return StreakGuard.Result.OK;
	}

	@Override
	public synchronized void noteClock(long now) {
		// Im Zweitfenster wird nichts gesichert; und ein leerer Feed bleibt ungeschrieben.
		if (readOnly() || posts.isEmpty() && freezes.isEmpty()) return;
		long today = today(now);
		long known = ReelsTime.civilDay(clockDay);
		// Nie zurück: sonst ließe sich der gemerkte Tag mit der Uhr wieder loswerden.
		if (known != Long.MIN_VALUE && today <= known) return;
		clockDay = ReelsTime.civil(today);
		dirty = true;
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
	public void flush() throws IOException {
		if (dir == null || readOnly.getAsBoolean()) return;
		synchronized (writeLock) {
			FeedFile f = new FeedFile();
			boolean keepBroken;
			// Unter der Sperre des Feeds nur den Stand kopieren – das Schreiben wartet auf die Platte, der Feed nicht.
			synchronized (this) {
				if (!dirty) return;
				for (ReelsPost p : posts) f.posts.add(p.copy());
				if (!freezes.isEmpty()) {
					f.freezes = new ArrayList<ReelsFreeze>();
					for (ReelsFreeze z : freezes) f.freezes.add(z.copy());
				}
				f.clockDay = clockDay;
				keepBroken = brokenPending;
				dirty = false;
			}
			Path file = dir.resolve(FEED);
			try {
				if (keepBroken) {
					// Die unlesbare Datei aufheben, bevor sie überschrieben wird.
					if (Files.isRegularFile(file)) Files.copy(file, dir.resolve(BROKEN), StandardCopyOption.REPLACE_EXISTING);
					synchronized (this) {
						brokenPending = false;
					}
				}
				write(file, f);
			} catch (IOException | RuntimeException e) {
				// Nicht geschrieben: der Stand gilt weiter als ungesichert, der nächste Versuch schreibt ihn.
				synchronized (this) {
					dirty = true;
				}
				throw e;
			}
		}
	}

	private static void write(Path file, Object data) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) Files.createDirectories(parent);
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		// Nicht Files.newBufferedWriter: dessen Kodierer bricht bei einem Text ab, der sich nicht als UTF-8 schreiben
		// lässt (ein halbes Zeichenpaar) – ein einziger solcher Text hielte dann jede weitere Sicherung auf. Dieser
		// hier ersetzt die Stelle durch „?“ und schreibt den Rest.
		try (Writer w = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(tmp), StandardCharsets.UTF_8))) {
			GSON.toJson(data, w);
		}
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
