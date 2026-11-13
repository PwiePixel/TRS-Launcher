package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.clips.Thumbnails;
import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.input.KeyCombo;
import dev.theredstonee.trsclient.core.module.KeySetting;
import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.reels.ArmRig;
import dev.theredstonee.trsclient.core.reels.FeedPager;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.reels.PhotoFrame;
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.reels.ReelsComment;
import dev.theredstonee.trsclient.core.reels.ReelsPost;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.FadeCanvas;
import dev.theredstonee.trsclient.core.ui.Hits;
import dev.theredstonee.trsclient.core.ui.TextInput;
import dev.theredstonee.trsclient.core.ui.TextureRef;
import dev.theredstonee.trsclient.core.ui.Textures;
import dev.theredstonee.trsclient.core.ui.UiKey;
import dev.theredstonee.trsclient.core.ui.UiScreen;

import java.nio.file.Path;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * Das Handy im Spiel (MC-Reels): zwei gezeichnete Arme halten ein Handy, auf dem der Foto-Feed läuft. Versionsunabhängig
 * – der Baum liefert nur Fenstergröße, Skin, Tasten und Töne ({@link Host}) und reicht Zeichnen und Eingaben durch.
 *
 * <p><b>Fensterpixel, nicht GUI-Einheiten:</b> gezeichnet wird mit {@code scale(1 / GUI-Skalierung)}, alle Maße kommen
 * aus der Fenstergröße ({@link PhoneLayout}). Handy und Arme sind deshalb bei jeder GUI-Skalierung gleich groß, und
 * die Arme laufen immer unten aus dem Bild.
 *
 * <p>Reihenfolge: Schleier über dem Spiel, Arme (aus dem Skin, Innenseite vor Vorderseite), Schatten und Gehäuse, dann
 * der Bildschirm. Ansichten: Feed und Kommentare ({@link FeedView}), Kamera mit „Foto“ und „Selfie“
 * ({@link CameraView}) und „Neuer Post“ mit Text ({@link ComposeView}). „Handy drehen“ wechselt zwischen quer und
 * hochkant und merkt sich die Haltung.
 *
 * <p><b>Kamera:</b> der Baum liefert Bilder der Welt <i>ohne</i> Oberfläche ({@link Host#requestFrame} /
 * {@link Host#pollFrame}) – auf einem Foto sind deshalb nie HUD, Chat, Handy oder Arme. Etwa sechsmal je Sekunde kommt
 * ein Bild für den Sucher; es wird im Hintergrund ({@link Reels#worker()}) auf die Größe des Suchers gebracht (nie
 * vergrößert) und hier nur noch hochgeladen. Der Auslöser fordert ein Bild in voller Auflösung an, das
 * {@link Reels#savePhoto} zuschneidet und als PNG speichert; danach öffnet sich „Neuer Post“. „Nochmal“ und Esc
 * löschen das Foto wieder, „Posten“ stellt es vorn in den Feed. Der Druck, der ausgelöst hat, postet nie gleich mit:
 * „Posten“ und Enter gelten erst kurz nach dem Öffnen von „Neuer Post“, Enter außerdem erst nach dem Loslassen
 * ({@link #POST_GUARD_MS}).
 *
 * <p><b>Aufräumen:</b> {@link #dispose()} gibt die Texturen frei, löscht ein nicht gepostetes Foto, sperrt die
 * Handy-Taste bis zum Loslassen und sichert den Feed. Es läuft nach der Schließ-Animation und muss vom Baum zusätzlich
 * gerufen werden, wenn der Bildschirm anders verschwindet (Tod, Verbindung getrennt) – mehrfach rufen schadet nicht.
 */
public final class PhoneUi extends UiScreen {
	/** Anbindung an den Baum (Minecraft). Alle Aufrufe im Render-/Spiel-Thread. */
	public interface Host {
		/** Fenstergröße in echten Pixeln (Framebuffer), nicht in GUI-Einheiten. */
		int windowWidth();

		int windowHeight();

		/** GUI-Skalierung des Spiels (GUI-Einheit → Fensterpixel), mindestens 1. */
		int guiScale();

		/** Skin des Spielers (64×64) oder null, solange es keinen Spieler gibt. */
		TextureRef skin();

		/** Schlanker Skin (Arme 3 Pixel breit)? Die gezeichneten Arme bleiben trotzdem 4 Pixel breit. */
		boolean slimSkin();

		/** Zeigt der Spieler den Ärmel (zweite Skin-Schicht) dieses Arms (Skin-Einstellungen)? */
		boolean sleeve(boolean left);

		String playerName();

		/** Name der Taste zu einem Tastencode der Version (z. B. {@code key.keyboard.h}) oder null. */
		String keyName(int rawKey);

		/** Ist die Taste mit diesem Namen gerade unten? */
		boolean keyDown(String keyName);

		/** Beschriftung einer Taste für Hinweise („H“). */
		String keyLabel(String keyName);

		/** Text aus der Zwischenablage oder null. */
		String paste();

		/** Klick-Ton. */
		void click();

		/** Blick des Spielers drehen (Kamera des Handys): Grad nach rechts bzw. nach unten. */
		void turn(float yawDeg, float pitchDeg);

		/**
		 * Das nächste Bild der Welt anfordern – ohne Oberfläche (kein HUD, kein Chat, kein Handy, keine Arme). Für ein
		 * Foto ({@code photo}) in voller Auflösung. Sonst für den Sucher, der {@code needW}×{@code needH} Fensterpixel
		 * groß ist: der Baum darf beim Auslesen verkleinern, solange der mittige Ausschnitt in diesem Seitenverhältnis
		 * mindestens so groß bleibt ({@link PhotoFrame#readbackFactor}). Eine neue Anforderung derselben Art ersetzt eine
		 * noch nicht bediente.
		 */
		void requestFrame(boolean photo, int needW, int needH);

		/** Das nächste fertige Bild oder null, solange keines da ist. */
		Frame pollFrame();

		/** Bildschirm wirklich schließen (nach der Animation). */
		void close();
	}

	/** Ein Bild der Welt ohne Oberfläche: ARGB, Zeilen von oben. */
	public static final class Frame {
		public final int[] argb;
		public final int width;
		public final int height;
		/** Für ein Foto angefordert (sonst für den Sucher). */
		public final boolean photo;

		public Frame(int[] argb, int width, int height, boolean photo) {
			this.argb = argb;
			this.width = width;
			this.height = height;
			this.photo = photo;
		}
	}

	/** Was der Bildschirm des Handys zeigt. */
	public enum View {
		FEED,
		COMMENTS,
		CAMERA,
		COMPOSE
	}

	/** Fertig gerechnetes Bild für den Sucher (kommt aus dem Hintergrund-Thread). */
	private static final class Finder {
		final int[] argb;
		final int width;
		final int height;

		Finder(int[] argb, int width, int height) {
			this.argb = argb;
			this.width = width;
			this.height = height;
		}
	}

	/** So weit (Anteil der Fensterhöhe) kommen Arme und Handy beim Öffnen von unten herauf. */
	static final float SLIDE = 0.35f;
	/** Fotos im Feed: höchstens so groß geladen (längste Seite), so viele zugleich. */
	static final int PHOTO_MAX = 1024;
	static final int PHOTO_CACHE = 6;
	/**
	 * Sucher: so oft kommt ein neues Bild (Millisekunden) – etwa sechsmal je Sekunde bis Full HD. Jedes Bild kostet den
	 * Render-Thread Zeit im Verhältnis zur Pixelzahl des Fensters, deshalb wird der Abstand darüber länger
	 * ({@link #finderInterval}), höchstens {@link #FINDER_INTERVAL_MAX_MS}.
	 */
	static final long FINDER_INTERVAL_MS = 160L;
	static final long FINDER_INTERVAL_MAX_MS = 320L;
	/** Bis zu dieser Pixelzahl des Fensters (1920×1080) gilt {@link #FINDER_INTERVAL_MS}. */
	static final long FINDER_FULL_RATE_PIXELS = 1920L * 1080L;
	/**
	 * „Neuer Post“: so lange nach dem Öffnen gelten „Posten“ und Enter noch nicht – ein zweiter Druck auf den Auslöser
	 * (Doppelklick, ungeduldiges zweites Enter, Tastenwiederholung) postet das Foto sonst ohne Text.
	 */
	static final long POST_GUARD_MS = 400L;
	/** Kommt so lange kein angefordertes Bild, gilt die Anforderung als verloren. */
	static final long FRAME_TIMEOUT_MS = 2000L;
	/** So lange darf das Speichern eines Fotos dauern, bevor die Kamera wieder frei ist. */
	static final long SAVE_TIMEOUT_MS = 15000L;
	/** Dauer des weißen Blitzes nach dem Auslösen und der Meldung auf dem Handy. */
	static final long FLASH_MS = 220L;
	static final long TOAST_MS = 3500L;
	/** Blick drehen: Grad je Fensterpixel beim Ziehen und Grad je Pfeiltasten-Druck. */
	static final float LOOK_DEG_PER_PX = 0.09f;
	static final float LOOK_DEG_PER_KEY = 5f;
	static final String FINDER_TEXTURE = "reels/viewfinder";
	static final String PREVIEW_TEXTURE = "reels/preview";
	private static final int FINDER_IDLE = 0;
	private static final int FINDER_REQUESTED = 1;
	private static final int FINDER_WORKING = 2;
	private static final Finder FINDER_FAILED = new Finder(null, 0, 0);

	private final Reels reels;
	private final Host host;
	private final TrsModules modules;
	private final FeedPager pager = new FeedPager();
	private final FeedView feedView = new FeedView(this);
	private final CameraView cameraView = new CameraView(this);
	private final ComposeView composeView = new ComposeView(this);
	private final PhoneVeil veil = new PhoneVeil();
	private final PhoneShadow shadow = new PhoneShadow();
	private final Map<String, int[]> spots = new HashMap<String, int[]>();
	/** Eingabefeld des Kommentar-Blatts. */
	final TextInput commentInput = new TextInput(ReelsComment.MAX_TEXT);
	/** Wie viele Kommentare das Blatt zuletzt gezeigt hat (Tests). */
	int commentsShown;
	/** Eingabefeld für den Text zum Foto („Neuer Post“). */
	final TextInput captionInput = new TextInput(ReelsPost.MAX_CAPTION);

	// --- Kamera ---
	private boolean selfie;
	private TextureRef finderTexture;
	private int finderState = FINDER_IDLE;
	private long finderSince;
	private long finderLast = Long.MIN_VALUE / 2;
	private volatile Finder finderReady;
	/** Auslöser gedrückt: das Bild in voller Auflösung ist angefordert. */
	private boolean shutterPending;
	private long shutterAt;
	private boolean shutterSelfie;
	private PhoneHold shutterHold;
	/** Das Foto wird gerade im Hintergrund gespeichert. */
	private boolean saving;
	private long savingSince;
	/** Nummer des Fotos, das gerade gespeichert wird ({@link Reels#savePhoto}) – nur dieses öffnet „Neuer Post“. */
	private long saveTicket;
	private long flashAt = Long.MIN_VALUE / 2;
	/** Das aufgenommene, noch nicht gepostete Foto („Neuer Post“). */
	private Reels.Shot shot;
	private TextureRef previewTexture;
	/** Wann „Neuer Post“ aufging, und ob Enter seitdem einmal losgelassen war. */
	private long composeSince;
	private boolean composeEnterUp;
	/** Beim Ziehen im Sucher: letzte Mausposition (Fensterpixel). */
	private double lookX, lookY;
	private boolean lookStart;
	// --- Meldung auf dem Handy ---
	private String toast;
	private long toastUntil;

	private Thumbnails photos;
	private PhoneHold hold;
	private View view = View.FEED;
	private PhoneLayout layout;
	private ArmRig leftArm;
	private ArmRig rightArm;
	private List<ReelsPost> feed;
	private int guiScale = 1;
	/** Die Handy-Taste war seit dem Öffnen einmal losgelassen – erst dann steckt sie das Handy wieder weg. */
	private boolean openKeySeenUp;
	private boolean disposed;
	private int streak;
	private long streakSecond = Long.MIN_VALUE;
	private List<ReelsPost> streakFeed;
	private Boolean forceSlim;
	private int armBlits;
	/** Tests: feste Uhrzeit (0 = echte Uhr). */
	long testNow;
	/** Tests: fester Taktgeber in Millisekunden für Sucher, Blitz und Meldung (0 = echte Uhr). */
	long testTicker;

	public PhoneUi(Reels reels, Host host) {
		if (reels == null || host == null) throw new IllegalArgumentException("reels/host");
		this.reels = reels;
		this.host = host;
		this.modules = reels.modules();
		PhoneHold saved = modules.reels.hold.get();
		this.hold = saved != null ? saved : PhoneHold.LANDSCAPE;
		I18n.refresh();
		// Fotos, die noch für ein früheres Handy gespeichert wurden (weggesteckt, bevor sie fertig waren): niemand
		// postet sie mehr.
		Reels.Shot stale;
		while ((stale = reels.pollShot()) != null) reels.discard(stale);
	}

	// --- Zeichnen ---

	@Override
	protected void draw(Canvas raw, int width, int height, int mouseX, int mouseY, float dt) {
		int gs = Math.max(1, host.guiScale());
		guiScale = gs;
		int ww = host.windowWidth();
		int wh = host.windowHeight();
		spots.clear();
		// Nach dem Aufräumen wird nichts mehr gezeichnet (sonst kämen freigegebene Texturen zurück).
		if (disposed || ww <= 0 || wh <= 0) return;
		trackOpenKey();
		if (layout == null || !layout.matches(ww, wh, hold)) {
			layout = PhoneLayout.of(ww, wh, hold);
			leftArm = ArmRig.of(layout, true);
			rightArm = ArmRig.of(layout, false);
		}
		PhoneLayout l = layout;
		refreshFeed();
		if (photos != null) photos.frame();
		cameraTick(l, raw.images());
		trackEnter();

		// Ab hier ist eine Einheit ein Fensterpixel – die GUI-Skalierung spielt keine Rolle mehr.
		raw.push();
		raw.scale(1f / gs);
		try {
			Canvas c = FadeCanvas.of(raw, alpha());
			veil.draw(c, ww, wh);
			// Arme und Handy kommen von unten: nur nach unten versetzt, damit die Arme auch dabei unten aus dem Bild laufen.
			int slide = Math.round((1f - alpha()) * SLIDE * wh);
			c.push();
			try {
				if (slide > 0) c.translate(0f, slide);
				boolean slim = forceSlim != null ? forceSlim.booleanValue() : host.slimSkin();
				armBlits = ArmDraw.draw(c, leftArm, rightArm, ArmRig.stripsPerRow(l.k), host.skin(), slim, host.sleeve(true),
						host.sleeve(false));
				shadow.draw(c, l);
				PhoneBody.draw(c, l);
				spot("phone", l.phoneX, l.phoneY, l.phoneW, l.phoneH);
				spot("screen", l.screenX, l.screenY, l.screenW, l.screenH);
				if (view == View.CAMERA) cameraView.draw(c, l);
				else if (view == View.COMPOSE) composeView.draw(c, l);
				else feedView.draw(c, l);
				drawToast(c, l);
			} finally {
				c.pop();
			}
		} finally {
			raw.pop();
		}
	}

	private void trackOpenKey() {
		if (openKeySeenUp) return;
		KeySetting key = modules.reels.openKey;
		if (!key.isBound() || !host.keyDown(KeyCombo.main(key.get()))) openKeySeenUp = true;
	}

	/** „Neuer Post“: Enter zählt erst, wenn die Taste seit dem Öffnen einmal losgelassen war. */
	private void trackEnter() {
		if (view != View.COMPOSE || composeEnterUp) return;
		if (!host.keyDown("key.keyboard.enter") && !host.keyDown("key.keyboard.keypad.enter")) composeEnterUp = true;
	}

	private void refreshFeed() {
		List<ReelsPost> now = reels.backend().feed();
		if (now != feed) {
			feed = now;
			pager.size(now == null ? 0 : now.size());
		}
	}

	// --- Für die Ansichten (FeedView) ---

	View view() {
		return view;
	}

	/** Der Post der aktuellen Seite oder null (leerer Feed). */
	ReelsPost current() {
		refreshFeed();
		if (feed == null || feed.isEmpty()) return null;
		int i = Math.max(0, Math.min(feed.size() - 1, pager.index()));
		return feed.get(i);
	}

	int page() {
		return pager.index();
	}

	int pages() {
		return pager.size();
	}

	long now() {
		return testNow != 0 ? testNow : System.currentTimeMillis();
	}

	/** Uhrzeit für die Kopfzeile des Handys („14:32“). */
	String clock() {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(now());
		int h = cal.get(Calendar.HOUR_OF_DAY), m = cal.get(Calendar.MINUTE);
		return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
	}

	/** Blinkt der Textcursor gerade? */
	boolean caretOn() {
		return System.currentTimeMillis() / 500L % 2L == 0L;
	}

	/** Tage in Folge mit einem Post (höchstens einmal je Sekunde neu gerechnet). */
	int streak() {
		long second = now() / 1000L;
		if (second != streakSecond || feed != streakFeed) {
			streakSecond = second;
			streakFeed = feed;
			streak = Math.max(0, reels.backend().streak(now()));
		}
		return streak;
	}

	boolean isSelf(String author) {
		String me = host.playerName();
		return author != null && me != null && author.equalsIgnoreCase(me);
	}

	TextureRef skin() {
		return host.skin();
	}

	String openKeyLabel() {
		return keyLabel(modules.reels.openKey);
	}

	String rotateKeyLabel() {
		return keyLabel(modules.reels.rotateKey);
	}

	private String keyLabel(KeySetting key) {
		if (!key.isBound()) return "–";
		String label = KeyCombo.label(key.get(), new KeyCombo.Label() {
			@Override
			public String label(String keyName) {
				String s = host.keyLabel(keyName);
				return s == null || s.isEmpty() ? "?" : s;
			}
		});
		return label;
	}

	/**
	 * Textur des Fotos eines Posts oder null, solange es noch lädt (oder fehlt). Die Nachbarn werden mitgeladen, damit
	 * das Blättern nicht auf ein leeres Bild trifft.
	 */
	TextureRef photo(ReelsPost post) {
		if (disposed || post == null) return null;
		TextureRef t = load(post);
		if (feed != null) {
			int i = pager.index();
			if (i + 1 < feed.size()) load(feed.get(i + 1));
			if (i - 1 >= 0 && i - 1 < feed.size()) load(feed.get(i - 1));
		}
		return t;
	}

	private TextureRef load(ReelsPost post) {
		Path file = reels.backend().photoFile(post);
		if (file == null) return null;
		if (photos == null) photos = new Thumbnails(reels.worker(), PHOTO_MAX, PHOTO_MAX, PHOTO_CACHE);
		// Fotos ändern sich nie mehr: der Zeitpunkt des Posts genügt als Stand (spart die Datei-Abfrage je Bild).
		return photos.get(file, post.createdAt);
	}

	/** Merkt sich eine Stelle des Handys in Fensterpixeln (für Tests und den Selbsttest im Spiel). */
	void spot(String name, int x, int y, int w, int h) {
		spots.put(name, new int[]{x, y, w, h});
	}

	/** Klickfläche in Fensterpixeln ({@code action} null: schluckt den Klick nur). */
	void tap(String name, int x, int y, int w, int h, Runnable action) {
		if (w <= 0 || h <= 0) return;
		hits.add(x, y, w, h, action);
		spot(name, x, y, w, h);
	}

	// --- Aktionen ---

	void like() {
		ReelsPost post = current();
		if (post == null) return;
		reels.toggleLike(post.id);
		host.click();
	}

	void openComments() {
		if (current() == null) return;
		view = View.COMMENTS;
		commentInput.clear();
		commentInput.setFocused(true);
		host.click();
	}

	void closeComments() {
		if (view != View.COMMENTS) return;
		view = View.FEED;
		commentInput.setFocused(false);
	}

	private void sendComment() {
		ReelsPost post = current();
		String text = commentInput.text().trim();
		if (post == null || text.isEmpty()) return;
		if (reels.comment(post.id, host.playerName(), text, now()) != null) {
			commentInput.clear();
			host.click();
		}
	}

	/** „Feed“ in der Leiste: zurück zum Feed (schließt die Kommentare bzw. die Kamera). */
	void home() {
		closeComments();
		closeCamera();
	}

	// --- Kamera ---

	/** Kamera öffnen (aus dem Feed). */
	void openCamera() {
		if (disposed || view == View.CAMERA || view == View.COMPOSE) return;
		closeComments();
		view = View.CAMERA;
		finderState = FINDER_IDLE;
		finderReady = null;
		// Das erste Bild sofort anfordern.
		finderLast = Long.MIN_VALUE / 2;
		host.click();
	}

	/** Kamera verlassen, zurück zum Feed. Ein noch laufendes Foto verfällt. */
	void closeCamera() {
		if (view != View.CAMERA) return;
		view = View.FEED;
		shutterPending = false;
		abandonSave();
		releaseFinder();
	}

	/** Auf das Foto, das gerade gespeichert wird, wartet niemand mehr: es wird gleich wieder gelöscht. */
	private void abandonSave() {
		if (saving) reels.abandon(saveTicket);
		saving = false;
	}

	boolean selfie() {
		return selfie;
	}

	void setSelfie(boolean on) {
		if (view != View.CAMERA || selfie == on || busy()) return;
		selfie = on;
		// Der Sucher zeigt sofort die andere Richtung.
		finderLast = Long.MIN_VALUE / 2;
		host.click();
	}

	void toggleSelfie() {
		setSelfie(!selfie);
	}

	/**
	 * Zeigt die Kamera gerade auf den Spieler („Selfie“)? Der Baum dreht dann die Kamera des Spiels für den Sucher und
	 * das Foto. Hinter dem Handy muss er dabei die normale Sicht stehen lassen (das letzte Bild vor dem Umschalten):
	 * sonst stünde die eigene Figur zwischen den gezeichneten Armen.
	 */
	public boolean selfieActive() {
		return !disposed && view == View.CAMERA && selfie;
	}

	/** Wird gerade ein Foto aufgenommen oder gespeichert? (Der Auslöser ruht so lange.) */
	boolean busy() {
		return shutterPending || saving;
	}

	/** Auslöser: das nächste Bild der Welt wird zum Foto. */
	void shutter() {
		if (view != View.CAMERA || busy() || isClosing()) return;
		if (reels.backend().readOnly()) {
			// Zweitfenster: der Post ließe sich nicht sichern – dann auch kein Foto, das ohne Post liegen bliebe.
			toast(I18n.tr("reels.toast.readonly"));
			return;
		}
		long t = ticker();
		shutterPending = true;
		shutterAt = t;
		shutterSelfie = selfie;
		shutterHold = hold;
		flashAt = t;
		host.requestFrame(true, 0, 0);
		host.click();
	}

	/** Wie stark der weiße Blitz nach dem Auslösen gerade ist (0…1). */
	float flash() {
		long age = ticker() - flashAt;
		return age < 0 || age >= FLASH_MS ? 0f : 1f - age / (float) FLASH_MS;
	}

	/** Textur des Suchers oder null, solange noch kein Bild da ist. */
	TextureRef viewfinder() {
		return finderTexture;
	}

	/** Foto des neuesten Posts (kleines Bild neben dem Auslöser) oder null. */
	TextureRef lastPhoto() {
		if (disposed || feed == null || feed.isEmpty()) return null;
		return load(feed.get(0));
	}

	/** Fläche des Suchers, auf der Ziehen den Blick dreht (unter allen Knöpfen). */
	void lookArea(int x, int y, int w, int h) {
		if (w <= 0 || h <= 0) return;
		hits.addDrag(x, y, w, h, new Hits.Drag() {
			@Override
			public void to(double mouseX, double mouseY) {
				if (lookStart) {
					lookStart = false;
				} else {
					float dx = (float) (mouseX - lookX), dy = (float) (mouseY - lookY);
					if (dx != 0f || dy != 0f) host.turn(dx * LOOK_DEG_PER_PX, dy * LOOK_DEG_PER_PX);
				}
				lookX = mouseX;
				lookY = mouseY;
			}
		});
	}

	/**
	 * Je Bild: fertige Fotos und Bilder des Spiels abholen, den Sucher hochladen und das nächste Sucherbild anfordern.
	 * Bilder und Fotos, auf die niemand mehr wartet (Kamera verlassen), verfallen.
	 */
	private void cameraTick(PhoneLayout l, boolean images) {
		long t = ticker();
		Reels.Shot done;
		while ((done = reels.pollShot()) != null) {
			// Nur das Foto dieses Auslösens zählt – ein älteres, dessen Speichern verlassen wurde, wird gelöscht.
			if (saving && done.ticket == saveTicket && view == View.CAMERA && shot == null) {
				saving = false;
				if (done.failed) toast(I18n.tr("reels.toast.failed"));
				else compose(done);
			} else {
				reels.discard(done);
			}
		}
		Frame f;
		while ((f = host.pollFrame()) != null) {
			if (f.photo) {
				if (!shutterPending) continue;
				shutterPending = false;
				if (view != View.CAMERA || f.argb == null || f.width <= 0 || f.height <= 0) continue;
				saving = true;
				savingSince = t;
				saveTicket = reels.savePhoto(f.argb, f.width, f.height, shutterHold, shutterSelfie, null);
			} else if (view == View.CAMERA && finderState == FINDER_REQUESTED) {
				scaleFinder(f, l.screenW, l.screenH);
			}
		}
		if (shutterPending && t - shutterAt > FRAME_TIMEOUT_MS) {
			// Der Baum hat kein Bild geliefert: die Kamera bleibt benutzbar.
			shutterPending = false;
			toast(I18n.tr("reels.toast.failed"));
		}
		if (saving && t - savingSince > SAVE_TIMEOUT_MS) {
			abandonSave();
			toast(I18n.tr("reels.toast.failed"));
		}
		if (view != View.CAMERA) return;
		Finder ready = finderReady;
		if (ready != null) {
			finderReady = null;
			finderState = FINDER_IDLE;
			Textures.Store store = Textures.store();
			if (ready != FINDER_FAILED && images && store != null) {
				try {
					TextureRef tex = store.upload(FINDER_TEXTURE, ready.width, ready.height, ready.argb);
					if (tex != null) finderTexture = tex;
				} catch (RuntimeException e) {
					// Sucher bleibt beim letzten Bild.
				}
			}
		}
		if (finderState != FINDER_IDLE && t - finderSince > FRAME_TIMEOUT_MS) finderState = FINDER_IDLE;
		if (finderState == FINDER_IDLE && images && t - finderLast >= finderInterval(l.windowW, l.windowH)) {
			finderLast = t;
			finderSince = t;
			finderState = FINDER_REQUESTED;
			host.requestFrame(false, l.screenW, l.screenH);
		}
	}

	/**
	 * Abstand der Sucherbilder für ein Fenster dieser Größe: {@link #FINDER_INTERVAL_MS} bis Full HD, darüber im
	 * Verhältnis der Pixelzahl länger (jedes Bild wird in voller Fenstergröße ausgelesen), höchstens
	 * {@link #FINDER_INTERVAL_MAX_MS}.
	 */
	static long finderInterval(int windowW, int windowH) {
		long pixels = (long) Math.max(0, windowW) * Math.max(0, windowH);
		if (pixels <= FINDER_FULL_RATE_PIXELS) return FINDER_INTERVAL_MS;
		return Math.min(FINDER_INTERVAL_MAX_MS, FINDER_INTERVAL_MS * pixels / FINDER_FULL_RATE_PIXELS);
	}

	/**
	 * Sucherbild im Hintergrund zuschneiden (mittig, im Seitenverhältnis des Suchers) und auf die Größe des Suchers
	 * bringen – nie größer als die Quelle.
	 */
	private void scaleFinder(final Frame f, final int needW, final int needH) {
		finderState = FINDER_WORKING;
		Runnable job = new Runnable() {
			@Override
			public void run() {
				Finder out = FINDER_FAILED;
				try {
					int[] crop = PhotoFrame.cropRect(f.width, f.height, needW, needH);
					int[] size = PhotoFrame.viewSize(crop[2], crop[3], needW, needH);
					out = new Finder(PhotoFrame.render(f.argb, f.width, f.height, crop, size[0], size[1]), size[0], size[1]);
				} catch (RuntimeException | OutOfMemoryError e) {
					out = FINDER_FAILED;
				}
				finderReady = out;
			}
		};
		try {
			reels.worker().execute(job);
		} catch (RejectedExecutionException e) {
			finderReady = FINDER_FAILED;
		}
	}

	private void releaseFinder() {
		TextureRef t = finderTexture;
		finderTexture = null;
		finderReady = null;
		finderState = FINDER_IDLE;
		Textures.Store store = Textures.store();
		if (t != null && store != null) store.release(t);
	}

	// --- Neuer Post ---

	/** Das Foto ist gespeichert: „Neuer Post“ öffnen. */
	private void compose(Reels.Shot done) {
		shot = done;
		view = View.COMPOSE;
		composeSince = ticker();
		composeEnterUp = false;
		captionInput.clear();
		captionInput.setFocused(true);
		releaseFinder();
		Textures.Store store = Textures.store();
		if (store != null && done.preview != null) {
			try {
				previewTexture = store.upload(PREVIEW_TEXTURE, done.previewW, done.previewH, done.preview);
			} catch (RuntimeException e) {
				previewTexture = null;
			}
		}
	}

	Reels.Shot shot() {
		return shot;
	}

	/** Vorschau des aufgenommenen Fotos oder null. */
	TextureRef preview() {
		return previewTexture;
	}

	/** Streak jetzt und nach dem Posten: {vorher, nachher}. */
	int[] streakChange() {
		return new int[]{streak(), Math.max(0, reels.backend().streakAfterPost(now()))};
	}

	private void releasePreview() {
		TextureRef t = previewTexture;
		previewTexture = null;
		Textures.Store store = Textures.store();
		if (t != null && store != null) store.release(t);
	}

	/** „Nochmal“ (oder Esc): das Foto wieder löschen und zurück zur Kamera. */
	void retry() {
		if (view != View.COMPOSE) return;
		Reels.Shot old = shot;
		shot = null;
		if (old != null) reels.discard(old);
		releasePreview();
		captionInput.clear();
		captionInput.setFocused(false);
		view = View.CAMERA;
		finderLast = Long.MIN_VALUE / 2;
		host.click();
	}

	/** Gilt „Posten“ schon? Kurz nach dem Öffnen von „Neuer Post“ noch nicht ({@link #POST_GUARD_MS}). */
	boolean postArmed() {
		return view == View.COMPOSE && ticker() - composeSince >= POST_GUARD_MS;
	}

	/** „Posten“: das Foto steht danach vorn im Feed. */
	void postShot() {
		if (view != View.COMPOSE || shot == null || !postArmed()) return;
		ReelsPost posted = reels.post(shot, captionInput.text(), host.playerName(), now());
		if (posted == null) {
			toast(I18n.tr("reels.toast.failed"));
			return;
		}
		shot = null;
		releasePreview();
		captionInput.clear();
		captionInput.setFocused(false);
		view = View.FEED;
		refreshFeed();
		pager.first();
		// Die Streak sofort neu rechnen.
		streakSecond = Long.MIN_VALUE;
		int days = streak();
		toast(days == 1 ? I18n.tr("reels.toast.posted.one", days) : I18n.tr("reels.toast.posted", days));
		host.click();
	}

	// --- Meldung auf dem Handy ---

	/** Kurze Meldung oben auf dem Bildschirm des Handys („Gepostet!“). */
	void toast(String text) {
		toast = text;
		toastUntil = ticker() + TOAST_MS;
	}

	private void drawToast(Canvas c, PhoneLayout l) {
		if (toast == null) return;
		if (ticker() >= toastUntil) {
			toast = null;
			return;
		}
		int m = PhoneText.scale(24, l.q);
		int pad = Math.max(2, Math.round(12 * l.q));
		int line = Math.max(1, Math.min(pad / 2, l.px(3)));
		int side = Math.round(14 * l.q);
		int max = l.screenW - 2 * side - 2 * pad;
		List<String> lines = PhoneText.wrap(c, toast, m, max, 3);
		if (lines.isEmpty()) return;
		int lineH = (PhoneText.GLYPH_H + 2) * m;
		int textW = 0;
		for (String s : lines) textW = Math.max(textW, PhoneText.width(c, s, m));
		int w = textW + 2 * pad;
		int h = (lines.size() - 1) * lineH + PhoneText.height(m) + 2 * pad;
		int x = l.screenX + (l.screenW - w) / 2;
		// Unter Kopfzeile und Titel, damit beides lesbar bleibt.
		int y = l.screenY + Math.round((l.hold == PhoneHold.PORTRAIT ? 96 : 58) * l.q);
		if (y + h > l.screenY + l.screenH) y = l.screenY + Math.max(0, (l.screenH - h) / 2);
		if (w > l.screenW || h > l.screenH) return;
		c.fill(x, y, x + w, y + h, 0xF2111116);
		c.fill(x, y, x + w, y + line, FeedView.AMBER);
		c.fill(x, y + h - line, x + w, y + h, FeedView.AMBER);
		c.fill(x, y + line, x + line, y + h - line, FeedView.AMBER);
		c.fill(x + w - line, y + line, x + w, y + h - line, FeedView.AMBER);
		for (int i = 0; i < lines.size(); i++) {
			String s = lines.get(i);
			PhoneText.draw(c, s, x + (w - PhoneText.width(c, s, m)) / 2, y + pad + i * lineH, m, FeedView.WHITE, false);
		}
		spot("toast", x, y, w, h);
	}

	/** Taktgeber für Sucher, Blitz und Meldung (Millisekunden, läuft nur vorwärts). */
	long ticker() {
		return testTicker != 0 ? testTicker : System.nanoTime() / 1000000L;
	}

	/** Handy drehen (quer ↔ hochkant); die Haltung wird gemerkt. */
	void rotate() {
		hold = hold.other();
		modules.reels.hold.set(hold);
		host.click();
	}

	private boolean page(int delta) {
		return view == View.FEED && pager.step(delta);
	}

	// --- Eingaben (kommen in GUI-Einheiten, die Klickflächen liegen in Fensterpixeln) ---

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Während das Handy verschwindet, zählen keine Klicks mehr.
		if (isClosing()) return true;
		lookStart = true;
		return hits.click(mouseX * guiScale, mouseY * guiScale, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button) {
		return hits.drag(mouseX * guiScale, mouseY * guiScale);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (view == View.FEED && !isClosing()) pager.wheel(amount);
		return true;
	}

	@Override
	public boolean keyPressed(int rawKey, UiKey key, boolean shift) {
		if (isClosing()) return true;
		if (view == View.COMMENTS) {
			// Im Textfeld sind Buchstaben nie Kurztasten.
			if (key == UiKey.ENTER) {
				sendComment();
			} else if (key == UiKey.ESCAPE) {
				closeComments();
			} else if (key == UiKey.PASTE) {
				paste(commentInput);
			} else {
				edit(commentInput, key);
			}
			return true;
		}
		if (view == View.COMPOSE) {
			// Auch hier ein Textfeld: Enter postet, Esc und Tab verwerfen das Foto („Nochmal“).
			if (key == UiKey.ENTER) {
				// Das Enter, das ausgelöst hat (noch unten oder wiederholt), postet nicht.
				if (composeEnterUp) postShot();
			} else if (key == UiKey.ESCAPE || key == UiKey.TAB) {
				retry();
			} else if (key == UiKey.PASTE) {
				paste(captionInput);
			} else {
				edit(captionInput, key);
			}
			return true;
		}
		String name = host.keyName(rawKey);
		if (isKey(name, modules.reels.openKey)) {
			// Gehaltene Taste (Tastenwiederholung direkt nach dem Öffnen) steckt das Handy nicht gleich wieder weg.
			if (openKeySeenUp) requestClose();
			return true;
		}
		if (isKey(name, modules.reels.rotateKey)) {
			rotate();
			return true;
		}
		if (view == View.CAMERA) return cameraKey(name, key);
		switch (key) {
			case ENTER:
				openComments();
				return true;
			case DOWN:
			case RIGHT:
				page(1);
				return true;
			case UP:
			case LEFT:
				page(-1);
				return true;
			default:
				// Esc im Feed: nicht verbraucht – der Bildschirm der Version schließt (mit Animation).
				return false;
		}
	}

	/** Tasten in der Kamera: Enter/Leertaste löst aus, F/Tab wechselt Foto ↔ Selfie, Pfeile drehen den Blick, Esc zurück. */
	private boolean cameraKey(String name, UiKey key) {
		switch (key) {
			case ESCAPE:
				closeCamera();
				return true;
			case ENTER:
				shutter();
				return true;
			case TAB:
				toggleSelfie();
				return true;
			case LEFT:
				host.turn(-LOOK_DEG_PER_KEY, 0f);
				return true;
			case RIGHT:
				host.turn(LOOK_DEG_PER_KEY, 0f);
				return true;
			case UP:
				host.turn(0f, -LOOK_DEG_PER_KEY);
				return true;
			case DOWN:
				host.turn(0f, LOOK_DEG_PER_KEY);
				return true;
			default:
				break;
		}
		if ("key.keyboard.space".equals(name)) {
			shutter();
			return true;
		}
		if ("key.keyboard.f".equals(name)) {
			toggleSelfie();
			return true;
		}
		return true;
	}

	private static boolean isKey(String pressed, KeySetting key) {
		return pressed != null && key.isBound() && pressed.equals(KeyCombo.main(key.get()));
	}

	/**
	 * Taste im Textfeld. Der Cursor bleibt immer am Ende: die Felder des Handys zeigen ihn dort und haben keinen Platz
	 * zum Blättern im Text – Pfeile, Pos1 und Ende würden ihn sonst unsichtbar verschieben.
	 */
	private static void edit(TextInput input, UiKey key) {
		if (key == UiKey.LEFT || key == UiKey.RIGHT || key == UiKey.HOME || key == UiKey.END) return;
		input.key(key);
	}

	private void paste(TextInput input) {
		String text = host.paste();
		if (text == null) return;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			input.type(ch == '\n' || ch == '\r' || ch == '\t' ? ' ' : ch);
		}
	}

	@Override
	public boolean charTyped(char ch) {
		if (isClosing()) return false;
		if (view == View.COMPOSE) {
			captionInput.type(ch);
			return true;
		}
		if (view != View.COMMENTS) return false;
		commentInput.type(ch);
		return true;
	}

	/** Das Handy hängt an der Fenstergröße – auch im Touch-Modus wird nichts vergrößert. */
	@Override
	protected float touchScale(int width, int height) {
		return 1f;
	}

	// --- Schließen ---

	@Override
	protected void onClosed() {
		dispose();
		host.close();
	}

	/**
	 * Aufräumen, wenn das Handy weg ist: Texturen freigeben, ein nicht gepostetes Foto löschen (auch eines, das noch
	 * gespeichert wird), die Handy-Taste bis zum Loslassen sperren, den Feed sichern. Mehrfach rufen schadet nicht.
	 * Render-Thread.
	 */
	public void dispose() {
		if (disposed) return;
		disposed = true;
		commentInput.setFocused(false);
		captionInput.setFocused(false);
		// Ein aufgenommenes, aber nicht gepostetes Foto bleibt nicht auf der Platte liegen.
		Reels.Shot open = shot;
		shot = null;
		if (open != null) reels.discard(open);
		shutterPending = false;
		// Ein Foto, das noch gespeichert wird, löscht der Hintergrund-Thread, sobald es fertig ist – und eines, das
		// gerade fertig wurde, aber noch nicht abgeholt ist, gleich hier.
		abandonSave();
		Reels.Shot late;
		while ((late = reels.pollShot()) != null) reels.discard(late);
		toast = null;
		releaseFinder();
		releasePreview();
		if (photos != null) {
			photos.releaseAll();
			photos = null;
		}
		veil.release();
		shadow.release();
		reels.keyGate().block();
		reels.save();
	}

	public boolean disposed() {
		return disposed;
	}

	// --- Für Tests und den Selbsttest im Spiel ---

	public View testView() {
		return view;
	}

	public PhoneHold testHold() {
		return hold;
	}

	/** Aktuelle Seite des Feeds, 0 = neuester Post. */
	public int testPage() {
		return pager.index();
	}

	/**
	 * Wo eine Stelle des Handys im letzten Bild lag: {x, y, Breite, Höhe} in Fensterpixeln, oder null. Namen:
	 * {@code phone}, {@code screen}, {@code photo}, {@code heart}, {@code comment}, {@code camera}, {@code rotate},
	 * {@code home}, {@code streak}, {@code commentInput}, {@code commentsClose}, {@code commentsBack}; Kamera:
	 * {@code viewfinder}, {@code camBack}, {@code camNote}, {@code camPhoto}, {@code camSelfie}, {@code camShutter},
	 * {@code camFlip}, {@code camThumb}; Neuer Post: {@code composePhoto}, {@code composeField}, {@code composeRetry},
	 * {@code composePost}; dazu {@code toast}.
	 */
	public int[] testSpot(String name) {
		int[] r = spots.get(name);
		return r == null ? null : r.clone();
	}

	/** Schlanken bzw. breiten Skin erzwingen (null = wie der Spieler). */
	public void testForceSlim(Boolean slim) {
		forceSlim = slim;
	}

	/** Öffnen-Animation überspringen (das nächste Bild zeigt das Handy ganz draußen). */
	public void testOpenFully() {
		open = 1f;
	}

	/** Blits, mit denen die Arme im letzten Bild gezeichnet wurden. */
	public int testArmBlits() {
		return armBlits;
	}

	/** Steht die Kamera auf „Selfie“? */
	public boolean testSelfie() {
		return selfie;
	}

	/** Größe der Sucher-Textur {Breite, Höhe} oder null, solange noch kein Bild da ist. */
	public int[] testViewfinder() {
		TextureRef t = finderTexture;
		return t == null ? null : new int[]{t.width, t.height};
	}

	/** Das aufgenommene, noch nicht gepostete Foto („Neuer Post“) oder null. */
	public Reels.Shot testShot() {
		return shot;
	}

	/** Wird gerade ein Foto aufgenommen oder gespeichert? */
	public boolean testBusy() {
		return busy();
	}

	/** Die Meldung, die gerade auf dem Handy steht, oder null. */
	public String testToast() {
		return toast != null && ticker() < toastUntil ? toast : null;
	}

	/** Gelten „Posten“ und Enter in „Neuer Post“ schon (kurz nach dem Öffnen noch nicht)? */
	public boolean testPostArmed() {
		return postArmed() && composeEnterUp;
	}

	/** Text im Feld von „Neuer Post“. */
	public String testCaption() {
		return captionInput.text();
	}
}
