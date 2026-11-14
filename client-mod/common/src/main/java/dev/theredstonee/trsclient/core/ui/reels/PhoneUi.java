package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.clips.Thumbnails;
import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.input.KeyCombo;
import dev.theredstonee.trsclient.core.module.KeySetting;
import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.qol.Qol;
import dev.theredstonee.trsclient.core.reels.ArmRig;
import dev.theredstonee.trsclient.core.reels.CommentThread;
import dev.theredstonee.trsclient.core.reels.FeedFilter;
import dev.theredstonee.trsclient.core.reels.FeedPager;
import dev.theredstonee.trsclient.core.reels.FeedQuery;
import dev.theredstonee.trsclient.core.reels.FeedSort;
import dev.theredstonee.trsclient.core.reels.Mentions;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.reels.PhotoFrame;
import dev.theredstonee.trsclient.core.reels.Pings;
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.reels.ReelsComment;
import dev.theredstonee.trsclient.core.reels.ReelsExport;
import dev.theredstonee.trsclient.core.reels.ReelsFriends;
import dev.theredstonee.trsclient.core.reels.ReelsMeta;
import dev.theredstonee.trsclient.core.reels.ReelsPost;
import dev.theredstonee.trsclient.core.reels.StreakGuard;
import dev.theredstonee.trsclient.core.screenshot.ImageClipboard;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.FadeCanvas;
import dev.theredstonee.trsclient.core.ui.Hits;
import dev.theredstonee.trsclient.core.ui.TextInput;
import dev.theredstonee.trsclient.core.ui.TextureRef;
import dev.theredstonee.trsclient.core.ui.Textures;
import dev.theredstonee.trsclient.core.ui.UiKey;
import dev.theredstonee.trsclient.core.ui.UiScreen;
import dev.theredstonee.trsclient.core.util.OpenPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * der Bildschirm. Ansichten: Feed ({@link FeedView}) mit dem Kommentar-Blatt ({@link CommentsView}), Kamera mit „Foto“
 * und „Selfie“ ({@link CameraView}) und „Neuer Post“ mit Text ({@link ComposeView}). „Handy drehen“ wechselt zwischen
 * quer und hochkant und merkt sich die Haltung.
 *
 * <p><b>Kommentare:</b> Enter sendet – je nach {@link CommentMode} als neuen Kommentar, als Antwort oder als Änderung
 * eines eigenen Kommentars. Das Feld hat eine bewegliche Schreibmarke (Pfeile, Pos1, Ende, Entf; ein Emoji-Paar zählt
 * als ein Zeichen). Wird {@code @} und ein Namensanfang getippt, stehen bis zu fünf Namen zur Wahl (Tab oder Klick
 * vervollständigt, Pfeil hoch/runter wählt): aus diesem Post, vom Server, aus der Freundesliste und aus dem Feed – im
 * Streamer-Modus ohne Server und Freunde. Esc verlässt erst den Modus, dann das Blatt. Das Mausrad blättert in den
 * Kommentaren, nie im Feed. Das Blatt gehört zu einem Post: verschwindet der aus der Liste, schließt es sich.
 *
 * <p><b>Erwähnungen und Antworten</b> ({@link Pings}): ungesehene zählt die Marke am Kommentar-Symbol des Posts. Steigt
 * ihre Zahl, kommt eine Meldung – einmal nach dem Spielstart und wieder bei einer neuen, nicht bei jedem Herausholen
 * ({@link Reels#announcePings}); ein Klick darauf öffnet die Kommentare des Posts. Das Öffnen der Kommentare merkt sie
 * als gesehen. Ohne Server kommt nichts von anderen an – das alles bleibt auf diesem PC.
 *
 * <p><b>Blätter über dem Feed</b> ({@link Sheet}, gezeichnet von {@link SheetView}): „Bild“ (speichern, kopieren, Ordner
 * öffnen), „Feed“ (Filter und Reihenfolge) und „Streak“ (Streak-Schutz). Solange eines offen ist, gehören Tasten und
 * Mausrad dem Blatt; Esc schließt nur das Blatt. Der Feed zeigt die gefilterte und sortierte Liste ({@link FeedQuery}) –
 * der Post, der gerade zu sehen ist, bleibt dabei zu sehen. Speichern, Kopieren und Ordner öffnen laufen im
 * Hintergrund ({@link Reels#worker()}); ihr Ergebnis kommt über eine Warteschlange zurück, die {@code draw} leert.
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

		/** Screenshots-Ordner des Spiels („Bild speichern“) oder null, wenn der Baum keinen kennt. */
		default Path screenshotsDir() {
			return null;
		}

		/**
		 * Ordner im Dateimanager zeigen („Ordner öffnen“). Läuft im Hintergrund-Thread ({@link Reels#worker()}), nicht im
		 * Render-Thread. false = ließ sich nicht öffnen.
		 */
		default boolean openFolder(Path dir) {
			return OpenPath.open(dir);
		}

		/** Bild in die Zwischenablage des Systems („Bild kopieren“). Läuft im Hintergrund-Thread, wie {@link #openFolder}. */
		default ImageClipboard.Result copyImage(Path png) {
			return ImageClipboard.copy(png);
		}

		/**
		 * Womit jetzt gerade ein Foto aufgenommen würde (Version, Loader, Shaderpack, Instanz) oder null. Wird beim
		 * Auslösen gerufen und darf nicht auf die Platte warten.
		 */
		default ReelsMeta photoMeta() {
			return null;
		}

		/** Namen der Freunde (Filter „Freunde“) – null, solange die Liste unbekannt ist. */
		default Collection<String> friendNames() {
			return ReelsFriends.names();
		}

		/** Namen der Spieler, die gerade auf dem Server sind (Namensvorschläge). */
		default List<String> onlinePlayers() {
			return Collections.<String>emptyList();
		}
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

	/** Blatt, das im Feed über dem unteren Teil des Bildschirms liegt. */
	public enum Sheet {
		NONE,
		/** „Bild“: speichern, kopieren, Ordner öffnen. */
		SAVE,
		/** „Feed“: Filter und Reihenfolge. */
		FILTER,
		/** „Streak“: Streak-Schutz. */
		STREAK
	}

	/** Was Enter im Kommentar-Blatt tut. */
	public enum CommentMode {
		/** Neuer Kommentar. */
		NEW,
		/** Antwort auf den Kommentar {@code commentTarget}. */
		REPLY,
		/** Der eigene Kommentar {@code commentTarget} wird geändert. */
		EDIT
	}

	/** Eine Meldung, die wartet, bis die laufende vorbei ist. */
	private static final class Note {
		final String text;
		final Runnable action;
		/** Die Meldung zu Erwähnungen und Antworten – davon steht oder wartet immer höchstens eine. */
		final boolean ping;

		Note(String text, Runnable action, boolean ping) {
			this.text = text;
			this.action = action;
			this.ping = ping;
		}
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
	/** So viele Meldungen warten höchstens hinter der laufenden ({@link #announce}). */
	static final int TOAST_QUEUE = 3;
	/** So viele Namen stehen beim Tippen von {@code @} höchstens zur Wahl. */
	static final int SUGGEST_MAX = 5;
	/** Streak-Schutz: so lange gilt „Sicher? Nochmal tippen“. */
	static final long STREAK_CONFIRM_MS = 3000L;
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
	private final FeedView feedView = new FeedView(this, new SheetView(this), new CommentsView(this));
	private final CameraView cameraView = new CameraView(this);
	private final ComposeView composeView = new ComposeView(this);
	private final PhoneVeil veil = new PhoneVeil();
	private final PhoneShadow shadow = new PhoneShadow();
	private final Map<String, int[]> spots = new HashMap<String, int[]>();
	/** Eingabefeld des Kommentar-Blatts. */
	final TextInput commentInput = new TextInput(ReelsComment.MAX_TEXT);
	/** Wie viele Kommentare das Blatt zuletzt gezeichnet hat (Tests). */
	int commentsShown;
	/** Anfang des Teils, den das Kommentarfeld zeigt ({@link PhoneText#window}); 0 nach dem Leeren oder Setzen. */
	int commentFieldStart;
	// --- Kommentar-Blatt ---
	private CommentMode commentMode = CommentMode.NEW;
	/** ID des Kommentars, der beantwortet bzw. bearbeitet wird (nur außerhalb von {@link CommentMode#NEW}). */
	private String commentTarget;
	/** Der Post, zu dem das offene Blatt gehört. */
	private String commentsPostId;
	/** Blättern in den Kommentaren: so viele Zeilen der Liste sind unten übersprungen. */
	private final FeedPager commentScroll = new FeedPager();
	/** ID eines eben gesendeten Kommentars: das nächste Bild blättert so, dass er zu sehen ist. */
	private String commentReveal;
	/** Die Kommentare des Posts in Anzeige-Reihenfolge – gerechnet für diesen Feed und diesen Post. */
	private List<CommentThread.Row> commentRows = Collections.<CommentThread.Row>emptyList();
	private List<ReelsPost> commentRowsFeed;
	private String commentRowsPost;
	/** Der Namensanfang, für den die Vorschläge gerechnet wurden (null: es wird gerade kein Name getippt). */
	private String suggestToken;
	private List<String> suggestions = Collections.<String>emptyList();
	private int suggestIndex;
	/** Die Spieler auf dem Server – einmal gelesen, wenn ein Namensanfang auftaucht, und danach wieder vergessen. */
	private List<String> suggestOnline;
	/** Tests: Streamer-Modus an oder aus (null: wie im Spiel). */
	Boolean testStreamer;
	// --- Erwähnungen und Antworten ---
	/** Zahl der ungesehenen im ganzen Feed – gerechnet für {@link #pingFeed}. */
	private int unseenPings;
	private List<ReelsPost> pingFeed;
	private boolean pingsKnown;
	/** Zahl der ungesehenen unter dem Post im Bild – gerechnet für diesen Feed und diesen Post. */
	private int badgeCount;
	private List<ReelsPost> badgeFeed;
	private String badgePost;
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
	/** Der Post, der mit diesem Handy zuletzt gepostet wurde (für das kleine Bild in der Kamera), oder null. */
	private String lastPosted;
	/** Wann „Neuer Post“ aufging, und ob Enter seitdem einmal losgelassen war. */
	private long composeSince;
	private boolean composeEnterUp;
	/** Beim Ziehen im Sucher: letzte Mausposition (Fensterpixel). */
	private double lookX, lookY;
	private boolean lookStart;
	/** Womit das Foto aufgenommen wurde, das gerade entsteht (beim Auslösen festgehalten), oder null. */
	private ReelsMeta shutterMeta;
	/** Womit dieses Spiel läuft (beim ersten Bild gelesen) – entscheidet, ob die Versions-Marke allein etwas sagt. */
	private ReelsMeta ownMeta;
	// --- Meldung auf dem Handy ---
	private String toast;
	private long toastUntil;
	/** Klick auf die laufende Meldung (null: sie ist nur Text). */
	private Runnable toastAction;
	/** Die laufende Meldung ist die zu Erwähnungen und Antworten. */
	private boolean toastPing;
	private final ArrayDeque<Note> toastQueue = new ArrayDeque<Note>();
	/** Unterkante von Seitenzahl und Filter-Marke im letzten Bild des Feeds (Fensterpixel; 0 = keine). */
	int headBottom;
	// --- Blätter über dem Feed ---
	private Sheet sheet = Sheet.NONE;
	/** Speichern, Kopieren oder Ordner öffnen läuft gerade im Hintergrund – so lange ruhen die drei Knöpfe. */
	private boolean sheetBusy;
	/** Ergebnisse aus dem Hintergrund-Thread; {@link #draw} führt sie im Render-Thread aus. */
	private final ConcurrentLinkedQueue<Runnable> results = new ConcurrentLinkedQueue<Runnable>();
	/** Streak-Schutz: welcher Knopf gerade „Sicher?“ fragt (0 keiner, 1 pausieren, 2 retten), seit wann, und ob Enter seitdem los war. */
	private int streakArmed;
	private long streakArmedAt;
	private boolean streakEnterUp;
	/**
	 * Die Taste (Enter, Tab oder Esc), die zuletzt ein Blatt oder einen Modus geöffnet oder geschlossen hat: solange sie
	 * unten bleibt, gehen ihre Wiederholungen nicht in die nächste Ebene (null = keine).
	 */
	private UiKey heldKey;
	private StreakGuard guard;
	private long guardSecond = Long.MIN_VALUE;
	private List<ReelsPost> guardFeed;
	/** Das erste Bild dieses Handys ist gezeichnet (Uhr gemerkt, Rettungs-Hinweis geprüft). */
	private boolean started;

	private Thumbnails photos;
	private PhoneHold hold;
	private View view = View.FEED;
	private PhoneLayout layout;
	private ArmRig leftArm;
	private ArmRig rightArm;
	/** Die Liste des Backends (alle Posts, neueste zuerst). */
	private List<ReelsPost> feed;
	/** Was der Feed zeigt: {@link #feed} gefiltert und sortiert – und womit zuletzt gerechnet wurde. */
	private List<ReelsPost> shown = Collections.<ReelsPost>emptyList();
	private List<ReelsPost> shownFeed;
	private FeedFilter shownFilter;
	private FeedSort shownSort;
	private Set<String> shownFriends;
	private FeedFilter filter;
	private FeedSort sort;
	/** Namen der Freunde in Kleinbuchstaben (null = unbekannt); je Bild höchstens einmal neu gelesen. */
	private Set<String> friends;
	private boolean friendsFresh;
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
		FeedFilter f = modules.reels.feedFilter.get();
		this.filter = f != null ? f : FeedFilter.ALL;
		FeedSort o = modules.reels.feedSort.get();
		this.sort = o != null ? o : FeedSort.NEWEST;
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
		friendsFresh = false;
		if (!started) start();
		// Was der Hintergrund inzwischen fertig hat (gespeichert, kopiert, Ordner geöffnet).
		Runnable done;
		while ((done = results.poll()) != null) done.run();
		trackOpenKey();
		if (layout == null || !layout.matches(ww, wh, hold)) {
			layout = PhoneLayout.of(ww, wh, hold);
			leftArm = ArmRig.of(layout, true);
			rightArm = ArmRig.of(layout, false);
		}
		PhoneLayout l = layout;
		refreshFeed();
		checkCommentTarget();
		checkPings();
		if (photos != null) photos.frame();
		cameraTick(l, raw.images());
		trackEnter();
		if (heldKey != null && !down(heldKey)) heldKey = null;

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

	/** Ist die Taste gerade unten? (Für Enter zählt auch das Enter des Ziffernblocks.) */
	private boolean down(UiKey key) {
		switch (key) {
			case ENTER:
				return host.keyDown("key.keyboard.enter") || host.keyDown("key.keyboard.keypad.enter");
			case TAB:
				return host.keyDown("key.keyboard.tab");
			case ESCAPE:
				return host.keyDown("key.keyboard.escape");
			default:
				return false;
		}
	}

	private void trackOpenKey() {
		if (openKeySeenUp) return;
		KeySetting key = modules.reels.openKey;
		if (!key.isBound() || !host.keyDown(KeyCombo.main(key.get()))) openKeySeenUp = true;
	}

	/**
	 * „Neuer Post“: Enter zählt erst, wenn die Taste seit dem Öffnen einmal losgelassen war. Dasselbe gilt für das zweite
	 * Enter beim Streak-Schutz; dort verfällt außerdem die Nachfrage nach {@link #STREAK_CONFIRM_MS}.
	 */
	private void trackEnter() {
		if (streakArmed != 0 && ticker() - streakArmedAt >= STREAK_CONFIRM_MS) streakArmed = 0;
		boolean compose = view == View.COMPOSE && !composeEnterUp;
		boolean streak = streakArmed != 0 && !streakEnterUp;
		if (!compose && !streak) return;
		if (host.keyDown("key.keyboard.enter") || host.keyDown("key.keyboard.keypad.enter")) return;
		if (compose) composeEnterUp = true;
		if (streak) streakEnterUp = true;
	}

	/**
	 * Erstes Bild dieses Handys: der Feed merkt sich den Kalendertag ({@link Reels#noteClock}), und lässt sich gestern
	 * retten, sagt es einmal am Tag eine Meldung.
	 */
	private void start() {
		started = true;
		ownMeta = ReelsMeta.normalized(host.photoMeta());
		long now = now();
		reels.noteClock(now);
		StreakGuard g = reels.backend().streakGuard(now);
		if (g != null && g.canRescueYesterday && reels.hintRescue(reels.epochDay(now))) {
			announce(I18n.tr("reels.toast.streak.rescuable"), null);
		}
	}

	/**
	 * Die gezeigte Liste neu rechnen, wenn sich die Liste des Backends, der Filter, die Reihenfolge oder (nur bei
	 * „Freunde“) die Freundesliste geändert hat. Der Post, der gerade zu sehen ist, bleibt zu sehen, solange es ihn in
	 * der neuen Liste gibt.
	 */
	private void refreshFeed() {
		List<ReelsPost> now = reels.backend().feed();
		Set<String> fr = filter == FeedFilter.FRIENDS ? friends() : null;
		if (now == shownFeed && filter == shownFilter && sort == shownSort && (fr == null ? shownFriends == null : fr.equals(shownFriends))) {
			return;
		}
		int at = pager.index();
		String keep = at >= 0 && at < shown.size() ? shown.get(at).id : null;
		boolean feedChanged = now != shownFeed;
		feed = now;
		shownFeed = now;
		shownFilter = filter;
		shownSort = sort;
		shownFriends = fr;
		shown = FeedQuery.apply(now, filter, sort, host.playerName(), fr);
		pager.size(shown.size());
		int i = FeedQuery.indexOf(shown, keep);
		if (i >= 0 && i != pager.index()) pager.to(i);
		// „Bild“ gehört zu einem Post: ohne Post kein Blatt.
		if (sheet == Sheet.SAVE && shown.isEmpty()) closeSheet();
		if (view != View.COMMENTS) return;
		// Das Kommentar-Blatt gehört zu seinem Post: ist der nicht mehr im Bild, schließt es sich.
		ReelsPost inView = shown.isEmpty() ? null : shown.get(Math.max(0, Math.min(shown.size() - 1, pager.index())));
		if (inView == null || inView.id == null || !inView.id.equals(commentsPostId)) {
			closeComments();
		} else if (feedChanged && reels.markPingsSeen(inView.id, host.playerName()) > 0) {
			// Was unter dem offenen Blatt ankommt, ist gleich gesehen – das hat den Feed noch einmal geändert.
			refreshFeed();
		}
	}

	/** Namen der Freunde in Kleinbuchstaben oder null (unbekannt) – je Bild höchstens einmal vom Baum geholt. */
	private Set<String> friends() {
		if (friendsFresh) return friends;
		friendsFresh = true;
		Collection<String> names;
		try {
			names = host.friendNames();
		} catch (RuntimeException e) {
			names = null;
		}
		if (names == null) {
			friends = null;
		} else {
			Set<String> lower = new HashSet<String>();
			for (String n : names) if (n != null && !n.isEmpty()) lower.add(n.toLowerCase(Locale.ROOT));
			friends = lower;
		}
		return friends;
	}

	// --- Für die Ansichten (FeedView) ---

	View view() {
		return view;
	}

	/** Der Post der aktuellen Seite oder null (leerer Feed, oder der Filter zeigt nichts). */
	ReelsPost current() {
		refreshFeed();
		if (shown.isEmpty()) return null;
		int i = Math.max(0, Math.min(shown.size() - 1, pager.index()));
		return shown.get(i);
	}

	/** Wie viele Posts der Feed insgesamt hat – ohne Filter. */
	int feedSize() {
		refreshFeed();
		return feed == null ? 0 : feed.size();
	}

	FeedFilter filter() {
		return filter;
	}

	FeedSort sort() {
		return sort;
	}

	/** Anzeigename einer Filter-Option bzw. einer Reihenfolge in der aktiven Sprache (dieselben Texte wie im TRS-Menü). */
	String filterLabel(FeedFilter f) {
		return modules.reels.feedFilter.optionLabel(f.ordinal());
	}

	String sortLabel(FeedSort s) {
		return modules.reels.feedSort.optionLabel(s.ordinal());
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

	/** Name des Spielers (kann null sein, solange es keinen gibt). */
	String selfName() {
		return host.playerName();
	}

	/** Die Liste des Backends, mit der gerade gerechnet wird – ändert sich der Feed, ist es eine andere. */
	Object feedIdentity() {
		return shownFeed;
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
		int i = pager.index();
		if (i + 1 < shown.size()) load(shown.get(i + 1));
		if (i - 1 >= 0 && i - 1 < shown.size()) load(shown.get(i - 1));
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

	// --- Kommentare ---

	/** Kommentare des Posts im Bild öffnen. Was ihn an Erwähnungen und Antworten betrifft, gilt damit als gesehen. */
	void openComments() {
		ReelsPost post = current();
		if (post == null || disposed) return;
		closeSheet();
		view = View.COMMENTS;
		commentsPostId = post.id;
		resetComments();
		commentInput.setFocused(true);
		String me = host.playerName();
		// Ist damit alles gesehen, hat sich auch die Meldung dazu erledigt.
		if (reels.markPingsSeen(post.id, me) > 0 && Pings.unseen(reels.backend().feed(), me) == 0) dropPingNote();
		host.click();
	}

	void closeComments() {
		if (view != View.COMMENTS) return;
		view = View.FEED;
		commentsPostId = null;
		resetComments();
		commentInput.setFocused(false);
	}

	/** Feld leer, neuer Kommentar, nichts markiert, Liste ganz unten, keine Namensvorschläge. */
	private void resetComments() {
		commentMode = CommentMode.NEW;
		commentTarget = null;
		commentReveal = null;
		setCommentText("");
		commentScroll.size(1);
		commentScroll.to(0);
	}

	/** Text des Kommentarfelds setzen; die Schreibmarke steht danach am Ende. */
	private void setCommentText(String text) {
		commentInput.setText(text);
		commentFieldStart = 0;
		refreshSuggestions();
	}

	CommentMode commentMode() {
		return commentMode;
	}

	String commentTargetId() {
		return commentTarget;
	}

	/** Der Kommentar, der gerade beantwortet bzw. bearbeitet wird – null im Modus NEW oder wenn es ihn nicht mehr gibt. */
	ReelsComment commentTarget(ReelsPost post) {
		return commentMode == CommentMode.NEW ? null : commentById(post, commentTarget);
	}

	private static ReelsComment commentById(ReelsPost post, String id) {
		if (post == null || post.comments == null || id == null) return null;
		for (ReelsComment c : post.comments) if (c != null && id.equals(c.id)) return c;
		return null;
	}

	/** Die Kommentare des Posts in Anzeige-Reihenfolge ({@link CommentThread}) – je Feed und Post einmal gerechnet. */
	List<CommentThread.Row> commentRows(ReelsPost post) {
		if (post == null) return Collections.<CommentThread.Row>emptyList();
		if (shownFeed != commentRowsFeed || post.id == null || !post.id.equals(commentRowsPost)) {
			commentRowsFeed = shownFeed;
			commentRowsPost = post.id;
			commentRows = CommentThread.rows(post.comments);
		}
		return commentRows;
	}

	/** So viele Zeilen der Kommentar-Liste sind unten übersprungen (0 = der neueste steht über dem Feld). */
	int commentSkip() {
		return commentScroll.index();
	}

	/** Wie weit sich blättern lässt: {@code size} − 1 Zeilen (setzt {@link CommentsView} je Bild). */
	void commentScrollLimit(int size) {
		commentScroll.size(Math.max(1, size));
	}

	/** Auf diese Stelle blättern ({@code size} Zeilen hat die Liste). */
	void commentScrollTo(int size, int skip) {
		commentScroll.size(Math.max(1, size));
		commentScroll.to(skip);
	}

	/** Der eben gesendete Kommentar, der im nächsten Bild zu sehen sein soll – einmal abgeholt, dann vergessen. */
	String takeCommentReveal() {
		String id = commentReveal;
		commentReveal = null;
		return id;
	}

	/** So blättern, dass dieser Kommentar die unterste gezeichnete Zeile ist. */
	private void scrollToComment(ReelsPost post, String commentId) {
		List<CommentThread.Row> rows = commentRows(post);
		for (int i = 0; i < rows.size(); i++) {
			if (commentId != null && commentId.equals(rows.get(i).comment.id)) {
				commentScroll.size(rows.size());
				commentScroll.to(rows.size() - 1 - i);
				return;
			}
		}
	}

	/** Klick auf das Feld: es bekommt den Fokus. Hat es ihn schon, bleibt die Schreibmarke, wo sie ist. */
	void focusComment() {
		if (!commentInput.focused()) commentInput.setFocused(true);
	}

	/**
	 * „Antworten“ unter einem Kommentar. Geantwortet wird immer auf den Kommentar der obersten Ebene; steht der Klick
	 * unter einer Antwort, kommt „@Name “ ihres Verfassers ins leere Feld – wenn das ein gültiger Spielername und nicht
	 * der eigene ist.
	 */
	void startReply(String commentId) {
		ReelsPost post = current();
		ReelsComment c = commentById(post, commentId);
		if (view != View.COMMENTS || c == null) return;
		// Der alte Text einer angefangenen Bearbeitung ist kein Entwurf für eine Antwort.
		if (commentMode == CommentMode.EDIT) setCommentText("");
		commentMode = CommentMode.REPLY;
		commentTarget = c.id;
		if (c.replyTo != null && commentInput.isEmpty() && Mentions.validName(c.author) && !isSelf(c.author)) {
			setCommentText("@" + c.author + " ");
		}
		scrollToComment(post, c.id);
		focusComment();
		host.click();
	}

	/** „Bearbeiten“ unter einem eigenen Kommentar: sein Text kommt ins Feld. */
	void startEdit(String commentId) {
		ReelsPost post = current();
		ReelsComment c = commentById(post, commentId);
		if (view != View.COMMENTS || c == null || !isSelf(c.author)) return;
		commentMode = CommentMode.EDIT;
		commentTarget = c.id;
		setCommentText(c.text);
		scrollToComment(post, c.id);
		focusComment();
		host.click();
	}

	/** Antworten bzw. Bearbeiten beenden (Kreuz der Leiste, Esc): der Text einer Antwort bleibt, der einer Bearbeitung geht. */
	void cancelCommentMode() {
		if (commentMode == CommentMode.NEW) return;
		boolean edit = commentMode == CommentMode.EDIT;
		commentMode = CommentMode.NEW;
		commentTarget = null;
		if (edit) setCommentText("");
		host.click();
	}

	/** Gibt es den Kommentar nicht mehr, der beantwortet bzw. bearbeitet wird, gilt wieder „neuer Kommentar“ – der Text bleibt. */
	private void checkCommentTarget() {
		if (view != View.COMMENTS || commentMode == CommentMode.NEW) return;
		if (commentTarget(current()) != null) return;
		commentMode = CommentMode.NEW;
		commentTarget = null;
	}

	/** Enter im Kommentar-Blatt: je nach Modus neuer Kommentar, Antwort oder Änderung. */
	private void sendComment() {
		ReelsPost post = current();
		if (post == null || view != View.COMMENTS) return;
		String text = commentInput.text().trim();
		String me = host.playerName();
		if (commentMode != CommentMode.NEW) {
			ReelsComment target = commentTarget(post);
			if (target == null) {
				// Den Kommentar gibt es nicht mehr: nichts senden, der getippte Text bleibt.
				commentMode = CommentMode.NEW;
				commentTarget = null;
				return;
			}
			if (text.isEmpty()) return;
			String id = target.id;
			ReelsComment done = commentMode == CommentMode.EDIT ? reels.editComment(post.id, id, me, text, now())
					: reels.reply(post.id, id, me, text, now());
			if (done == null) return;
			commentMode = CommentMode.NEW;
			commentTarget = null;
			setCommentText("");
			commentReveal = done.id;
			host.click();
			return;
		}
		if (text.isEmpty()) return;
		ReelsComment sent = reels.comment(post.id, me, text, now());
		if (sent == null) return;
		setCommentText("");
		commentReveal = sent.id;
		host.click();
	}

	// --- Kommentare: Namensvorschläge beim Tippen von @ ---

	/** Die Namen, die gerade zur Wahl stehen (leer: keine). */
	List<String> suggestions() {
		return suggestions;
	}

	int suggestIndex() {
		return suggestIndex;
	}

	/**
	 * Der blasse Hinweis „niemand wird benachrichtigt“: solange Namen zur Wahl stehen oder das Feld jemand anderen
	 * erwähnt.
	 */
	boolean commentNote() {
		return !suggestions.isEmpty() || Mentions.mentionsOther(commentInput.text(), host.playerName());
	}

	/** Ist der Streamer-Modus an? Dann stehen die Namen vom Server und aus der Freundesliste nicht zur Wahl. */
	private boolean streamerActive() {
		if (testStreamer != null) return testStreamer.booleanValue();
		Qol q = Qol.get();
		return q != null && q.streamer.active();
	}

	/**
	 * Nach jeder Änderung von Text oder Schreibmarke: wird davor gerade ein Name getippt ({@link Mentions#token})? Die
	 * Vorschläge werden nur neu gerechnet, wenn sich dieser Namensanfang ändert; die Spieler auf dem Server werden nur
	 * einmal gelesen, wenn ein Namensanfang auftaucht.
	 */
	private void refreshSuggestions() {
		String token = view == View.COMMENTS ? Mentions.token(commentInput.text(), commentInput.cursor()) : null;
		if (token == null) {
			suggestToken = null;
			suggestOnline = null;
			suggestions = Collections.<String>emptyList();
			suggestIndex = 0;
			return;
		}
		if (token.equals(suggestToken)) return;
		boolean streamer = streamerActive();
		if (suggestToken == null) {
			List<String> online = null;
			if (!streamer) {
				try {
					online = host.onlinePlayers();
				} catch (RuntimeException e) {
					online = null;
				}
			}
			suggestOnline = online == null ? Collections.<String>emptyList() : new ArrayList<String>(online);
		}
		suggestToken = token;
		String me = host.playerName();
		List<Collection<String>> sources = new ArrayList<Collection<String>>();
		// 1. Wer in diesem Post schon vorkommt.
		List<String> here = new ArrayList<String>();
		ReelsPost post = current();
		if (post != null) {
			here.add(post.author);
			if (post.comments != null) for (ReelsComment c : post.comments) if (c != null) here.add(c.author);
		}
		sources.add(here);
		// 2. und 3. Server und Freunde – nicht im Streamer-Modus.
		if (!streamer) {
			sources.add(suggestOnline);
			Collection<String> known;
			try {
				known = host.friendNames();
			} catch (RuntimeException e) {
				known = null;
			}
			if (known != null) sources.add(new ArrayList<String>(known));
		}
		// 4. Alle, die im Feed gepostet haben.
		List<String> authors = new ArrayList<String>();
		if (feed != null) for (ReelsPost p : feed) if (p != null) authors.add(p.author);
		sources.add(authors);
		List<String> found = Mentions.suggest(token, sources, me, SUGGEST_MAX);
		// Steht der Name schon ganz da, gibt es nichts vorzuschlagen.
		if (found.size() == 1 && found.get(0).equalsIgnoreCase(token)) found = Collections.<String>emptyList();
		suggestions = found;
		suggestIndex = 0;
	}

	/** Den Namen {@code index} der Vorschläge einsetzen („@Name “); die Schreibmarke steht danach dahinter. */
	void completeSuggestion(int index) {
		if (view != View.COMMENTS || index < 0 || index >= suggestions.size()) return;
		Mentions.Edit edit = Mentions.complete(commentInput.text(), commentInput.cursor(), suggestions.get(index), ReelsComment.MAX_TEXT);
		commentInput.setText(edit.text);
		commentInput.setCursor(edit.caret);
		commentFieldStart = 0;
		refreshSuggestions();
		focusComment();
		host.click();
	}

	/** Tasten im Kommentar-Blatt. Im Textfeld sind Buchstaben nie Kurztasten – jede Taste ist verbraucht. */
	private void commentKey(UiKey key) {
		boolean names = !suggestions.isEmpty();
		switch (key) {
			case ENTER:
				sendComment();
				return;
			case ESCAPE:
				// Erst den Modus verlassen, dann das Blatt schließen – je Druck eine Ebene, nie per Tastenwiederholung mehr.
				heldKey = key;
				if (commentMode != CommentMode.NEW) cancelCommentMode();
				else closeComments();
				return;
			case PASTE:
				paste(commentInput);
				break;
			case TAB:
				if (names) completeSuggestion(suggestIndex);
				return;
			case UP:
				if (names) suggestIndex = Math.max(0, suggestIndex - 1);
				else commentScroll.step(1);
				return;
			case DOWN:
				if (names) suggestIndex = Math.min(suggestions.size() - 1, suggestIndex + 1);
				else commentScroll.step(-1);
				return;
			default:
				editFree(commentInput, key);
				break;
		}
		refreshSuggestions();
	}

	// --- Erwähnungen und Antworten ---

	/** Zahl der ungesehenen Erwähnungen und Antworten unter diesem Post (Marke am Kommentar-Symbol). */
	int unseenPings(ReelsPost post) {
		if (post == null) return 0;
		if (shownFeed != badgeFeed || post.id == null || !post.id.equals(badgePost)) {
			badgeFeed = shownFeed;
			badgePost = post.id;
			badgeCount = Pings.unseen(post, host.playerName());
		}
		return badgeCount;
	}

	/**
	 * Im ersten Bild und nach jeder Änderung des Feeds: ist die Zahl der ungesehenen Erwähnungen und Antworten
	 * gestiegen ({@link Reels#announcePings}), kommt eine Meldung. Eine einzelne nennt den Verfasser, mehrere die Zahl.
	 * Der Klick darauf führt zum Post – außer der Filter zeigt ihn gerade nicht.
	 */
	private void checkPings() {
		if (pingsKnown && feed == pingFeed) return;
		pingsKnown = true;
		pingFeed = feed;
		String me = host.playerName();
		unseenPings = Pings.unseen(feed, me);
		if (!reels.announcePings(unseenPings)) return;
		Pings.Hit hit = Pings.first(feed, me);
		if (hit == null) return;
		String text;
		if (unseenPings == 1) {
			String author = hit.comment.author == null || hit.comment.author.isEmpty() ? "?" : hit.comment.author;
			text = I18n.tr(hit.mention ? "reels.toast.ping" : "reels.toast.reply", author);
		} else {
			text = I18n.tr("reels.toast.pings", unseenPings);
		}
		Runnable open = FeedQuery.indexOf(shown, hit.postId) < 0 ? null : new Runnable() {
			@Override
			public void run() {
				openPing();
			}
		};
		// Steht oder wartet schon eine, wird sie ersetzt – nie zwei mit verschiedenen Zahlen.
		if (toastPing && toast != null && ticker() < toastUntil) {
			show(text, open, true);
			return;
		}
		boolean replaced = false;
		ArrayDeque<Note> queue = new ArrayDeque<Note>();
		for (Note n : toastQueue) {
			if (!n.ping) queue.add(n);
			else if (!replaced) {
				queue.add(new Note(text, open, true));
				replaced = true;
			}
		}
		if (replaced) {
			toastQueue.clear();
			toastQueue.addAll(queue);
			return;
		}
		announce(text, open, true);
	}

	/** Die Meldung zu Erwähnungen und Antworten wegnehmen, falls sie noch steht oder wartet. */
	private void dropPingNote() {
		if (toastPing) {
			toast = null;
			toastAction = null;
			toastPing = false;
		}
		for (Iterator<Note> it = toastQueue.iterator(); it.hasNext(); ) {
			if (it.next().ping) it.remove();
		}
	}

	/** Klick auf die Meldung: zum Post mit der ältesten ungesehenen Erwähnung bzw. Antwort und seine Kommentare öffnen. */
	private void openPing() {
		if (disposed || isClosing() || (view != View.FEED && view != View.COMMENTS)) return;
		refreshFeed();
		Pings.Hit hit = Pings.first(feed, host.playerName());
		int i = hit == null ? -1 : FeedQuery.indexOf(shown, hit.postId);
		if (i < 0) return;
		// Die Meldung hat ihren Dienst getan.
		dropPingNote();
		closeSheet();
		closeComments();
		pager.to(i);
		openComments();
	}

	/** „Feed“ in der Leiste: zurück zum Feed (schließt die Kommentare bzw. die Kamera). */
	void home() {
		closeSheet();
		closeComments();
		closeCamera();
	}

	// --- Kamera ---

	/** Kamera öffnen (aus dem Feed). */
	void openCamera() {
		if (disposed || view == View.CAMERA || view == View.COMPOSE) return;
		closeSheet();
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
	 *
	 * <p>Nicht mehr, sobald das Handy weggesteckt wird: im letzten Bild der Animation verschwindet der Bildschirm, nachdem
	 * die Kamera des Spiels schon ausgerichtet ist – wäre sie da noch umgedreht, stünde die eigene Figur ein Bild lang
	 * groß im Fenster. Beim Wegstecken zeigt das Spiel deshalb gleich wieder die normale Sicht.
	 */
	public boolean selfieActive() {
		return !disposed && !isClosing() && view == View.CAMERA && selfie;
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
		ReelsMeta meta;
		try {
			meta = host.photoMeta();
		} catch (RuntimeException e) {
			meta = null;
		}
		shutterMeta = ReelsMeta.normalized(meta);
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

	/**
	 * Das letzte Foto (kleines Bild neben dem Auslöser) oder null: der Post, der mit diesem Handy zuletzt gepostet
	 * wurde, sonst der neueste im Feed.
	 */
	TextureRef lastPhoto() {
		// Der Filter des Feeds zählt hier nicht: das letzte Foto ist das letzte Foto.
		if (disposed || feed == null || feed.isEmpty()) return null;
		int i = FeedQuery.indexOf(feed, lastPosted);
		return load(feed.get(i < 0 ? 0 : i));
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
		// Beim Wegstecken kein neues Sucherbild mehr: der Sucher behält sein letztes (ein Selfie spränge sonst um).
		if (finderState == FINDER_IDLE && images && !isClosing() && t - finderLast >= finderInterval(l.windowW, l.windowH)) {
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

	/**
	 * „Posten“: der Feed zeigt danach den neuen Post. Das ist die erste Seite – außer die Uhr des PCs wurde
	 * zurückgestellt: dann stehen ältere Posts mit späterer Zeit davor, und der neue ist ein paar Seiten weiter.
	 */
	void postShot() {
		if (view != View.COMPOSE || shot == null || !postArmed()) return;
		ReelsPost posted = reels.post(shot, captionInput.text(), host.playerName(), now(), shutterMeta);
		if (posted == null) {
			toast(I18n.tr("reels.toast.failed"));
			return;
		}
		shot = null;
		shutterMeta = null;
		releasePreview();
		captionInput.clear();
		captionInput.setFocused(false);
		view = View.FEED;
		// Unter „Freunde“ wäre der eigene neue Post nicht zu sehen: zurück zu „Alle“.
		if (filter == FeedFilter.FRIENDS) applyFilter(FeedFilter.ALL);
		refreshFeed();
		if (FeedQuery.indexOf(shown, posted.id) < 0 && filter != FeedFilter.ALL) {
			applyFilter(FeedFilter.ALL);
			refreshFeed();
		}
		lastPosted = posted.id;
		pager.to(Math.max(0, FeedQuery.indexOf(shown, posted.id)));
		// Die Streak sofort neu rechnen.
		streakSecond = Long.MIN_VALUE;
		int days = streak();
		toast(days == 1 ? I18n.tr("reels.toast.posted.one", days) : I18n.tr("reels.toast.posted", days));
		host.click();
	}

	// --- Meldung auf dem Handy ---

	/**
	 * Kurze Meldung oben auf dem Bildschirm des Handys („Gepostet!“) – die Antwort auf einen Klick: sie ersetzt sofort,
	 * was gerade dasteht.
	 */
	void toast(String text) {
		show(text, null, false);
	}

	private void show(String text, Runnable action, boolean ping) {
		toast = text;
		toastAction = action;
		toastPing = ping;
		toastUntil = ticker() + TOAST_MS;
	}

	/**
	 * Meldung, die von selbst kommt (Hinweis beim Herausholen): steht gerade eine andere da, wartet sie, bis die vorbei
	 * ist – höchstens {@link #TOAST_QUEUE} warten. {@code action}: Klick auf die Meldung (null: nur Text).
	 */
	void announce(String text, Runnable action) {
		announce(text, action, false);
	}

	private void announce(String text, Runnable action, boolean ping) {
		if (text == null || text.isEmpty() || disposed) return;
		if (toast == null || ticker() >= toastUntil) show(text, action, ping);
		else if (toastQueue.size() < TOAST_QUEUE) toastQueue.add(new Note(text, action, ping));
	}

	private void drawToast(Canvas c, PhoneLayout l) {
		if (toast != null && ticker() >= toastUntil) {
			toast = null;
			toastAction = null;
			toastPing = false;
		}
		if (toast == null) {
			Note next = toastQueue.poll();
			if (next == null) return;
			show(next.text, next.action, next.ping);
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
		// Hochkant steht die Filter-Marke unter dem Titel: die Meldung rückt darunter.
		if ((view == View.FEED || view == View.COMMENTS) && l.hold == PhoneHold.PORTRAIT && headBottom > 0) {
			y = Math.max(y, headBottom + Math.round(6 * l.q));
		}
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
		// Nur eine Meldung, die irgendwohin führt, nimmt Klicks an – und nur dort, wo sie hinführen kann (nicht in der Kamera).
		if (toastAction != null && (view == View.FEED || view == View.COMMENTS)) tap("toast", x, y, w, h, toastAction);
		else spot("toast", x, y, w, h);
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
		return view == View.FEED && sheet == Sheet.NONE && pager.step(delta);
	}

	// --- Blätter über dem Feed: „Bild“, „Feed“, „Streak“ ---

	Sheet sheet() {
		return sheet;
	}

	boolean sheetBusy() {
		return sheetBusy;
	}

	/** Blatt öffnen – nur im Feed; „Bild“ braucht einen Post, „Feed“ mindestens einen Post im ganzen Feed. */
	void openSheet(Sheet which) {
		if (which == null || which == Sheet.NONE || disposed || view != View.FEED || isClosing()) return;
		if (which == Sheet.SAVE && current() == null) return;
		if (which == Sheet.FILTER && feedSize() == 0) return;
		sheet = which;
		streakArmed = 0;
		host.click();
	}

	void closeSheet() {
		sheet = Sheet.NONE;
		streakArmed = 0;
	}

	/** Tasten, solange ein Blatt offen ist. Verbraucht jede Taste – auch Esc: es schließt nur das Blatt. */
	private boolean sheetKey(UiKey key) {
		if (sheet == Sheet.SAVE) {
			if (key == UiKey.ESCAPE) closeSheet();
			else if (key == UiKey.ENTER) savePicture();
		} else if (sheet == Sheet.FILTER) {
			if (key == UiKey.ESCAPE || key == UiKey.ENTER || key == UiKey.TAB) closeSheet();
			else if (key == UiKey.LEFT) stepFilter(-1);
			else if (key == UiKey.RIGHT) stepFilter(1);
			else if (key == UiKey.UP) stepSort(-1);
			else if (key == UiKey.DOWN) stepSort(1);
		} else if (sheet == Sheet.STREAK) {
			if (key == UiKey.ESCAPE) {
				closeSheet();
			} else if (key == UiKey.ENTER) {
				StreakGuard g = streakGuard();
				if (g.canPauseToday) useProtection(false, true);
				else if (g.canRescueYesterday) useProtection(true, true);
			}
		}
		return true;
	}

	// --- „Bild“: speichern, kopieren, Ordner öffnen (ohne Netz – nur diese Platte und die Zwischenablage) ---

	/** Der Screenshots-Ordner als fester Pfad (der Spielordner ist im Entwickler-Start nur „.“) oder null. */
	private Path screenshots() {
		try {
			Path dir = host.screenshotsDir();
			return dir == null ? null : dir.toAbsolutePath().normalize();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Auftrag an den Hintergrund-Thread; so lange ruhen die drei Knöpfe. false: der Thread nimmt nichts mehr an. */
	private boolean background(Runnable job) {
		sheetBusy = true;
		try {
			reels.worker().execute(job);
			return true;
		} catch (RejectedExecutionException e) {
			sheetBusy = false;
			return false;
		}
	}

	/** „Bild speichern“: das Foto des Posts in den Screenshots-Ordner kopieren (überschreibt nie). */
	void savePicture() {
		if (sheet != Sheet.SAVE || sheetBusy || disposed) return;
		ReelsPost post = current();
		if (post == null) return;
		host.click();
		final Path dir = screenshots();
		if (dir == null) {
			toast(I18n.tr("reels.toast.save.failed"));
			return;
		}
		final Path photo = reels.backend().photoFile(post);
		final String id = post.id;
		Runnable job = new Runnable() {
			@Override
			public void run() {
				ReelsExport.Status status;
				try {
					status = ReelsExport.save(photo, dir, id).status;
				} catch (RuntimeException e) {
					status = ReelsExport.Status.FAILED;
				}
				final ReelsExport.Status done = status;
				results.add(new Runnable() {
					@Override
					public void run() {
						saved(done);
					}
				});
			}
		};
		if (!background(job)) toast(I18n.tr("reels.toast.save.failed"));
	}

	private void saved(ReelsExport.Status status) {
		sheetBusy = false;
		if (status == ReelsExport.Status.SAVED || status == ReelsExport.Status.ALREADY) {
			if (sheet == Sheet.SAVE) closeSheet();
			toast(I18n.tr(status == ReelsExport.Status.SAVED ? "reels.toast.saved" : "reels.toast.saved.already"));
		} else {
			// Das Blatt bleibt: „Bild kopieren“ oder der Ordner helfen vielleicht weiter.
			toast(I18n.tr(status == ReelsExport.Status.MISSING ? "reels.toast.save.missing" : "reels.toast.save.failed"));
		}
	}

	/** „Bild kopieren“: das Foto in die Zwischenablage des Systems. */
	void copyPicture() {
		if (sheet != Sheet.SAVE || sheetBusy || disposed) return;
		ReelsPost post = current();
		if (post == null) return;
		host.click();
		final Path photo = reels.backend().photoFile(post);
		Runnable job = new Runnable() {
			@Override
			public void run() {
				ImageClipboard.Result result;
				try {
					result = photo == null ? ImageClipboard.Result.FAILED : host.copyImage(photo);
				} catch (RuntimeException e) {
					result = ImageClipboard.Result.FAILED;
				}
				final ImageClipboard.Result done = result;
				results.add(new Runnable() {
					@Override
					public void run() {
						sheetBusy = false;
						toast(I18n.tr(done == ImageClipboard.Result.OK ? "reels.toast.copied"
								: done == ImageClipboard.Result.UNSUPPORTED ? "reels.toast.copy.unsupported" : "reels.toast.copy.failed"));
					}
				});
			}
		};
		if (!background(job)) toast(I18n.tr("reels.toast.copy.failed"));
	}

	/** „Ordner öffnen“: den Screenshots-Ordner anlegen, falls nötig, und im Dateimanager zeigen. */
	void openPictureFolder() {
		if (sheet != Sheet.SAVE || sheetBusy || disposed) return;
		host.click();
		final Path dir = screenshots();
		if (dir == null) {
			toast(I18n.tr("reels.toast.folder.failed"));
			return;
		}
		Runnable job = new Runnable() {
			@Override
			public void run() {
				boolean ok;
				try {
					Files.createDirectories(dir);
					ok = host.openFolder(dir);
				} catch (IOException | RuntimeException e) {
					ok = false;
				}
				final boolean opened = ok;
				results.add(new Runnable() {
					@Override
					public void run() {
						sheetBusy = false;
						if (!opened) toast(I18n.tr("reels.toast.folder.failed"));
					}
				});
			}
		};
		if (!background(job)) toast(I18n.tr("reels.toast.folder.failed"));
	}

	// --- „Feed“: Filter und Reihenfolge ---

	/** Filter wählen: gilt sofort und wird gemerkt. */
	void setFilter(FeedFilter f) {
		if (f == null || f == filter) return;
		applyFilter(f);
		host.click();
	}

	private void applyFilter(FeedFilter f) {
		filter = f;
		modules.reels.feedFilter.set(f);
		// Die Freundesliste gleich neu lesen, nicht erst im nächsten Bild.
		friendsFresh = false;
	}

	/** Reihenfolge wählen: gilt sofort und wird gemerkt. */
	void setSort(FeedSort s) {
		if (s == null || s == sort) return;
		sort = s;
		modules.reels.feedSort.set(s);
		host.click();
	}

	private void stepFilter(int delta) {
		FeedFilter[] all = FeedFilter.values();
		setFilter(all[Math.max(0, Math.min(all.length - 1, filter.ordinal() + delta))]);
	}

	private void stepSort(int delta) {
		FeedSort[] all = FeedSort.values();
		setSort(all[Math.max(0, Math.min(all.length - 1, sort.ordinal() + delta))]);
	}

	// --- „Streak“: Streak-Schutz ---

	/** Tipp auf die Flamme (nur im Feed). */
	void openStreakSheet() {
		openSheet(Sheet.STREAK);
	}

	/** Stand des Streak-Schutzes (höchstens einmal je Sekunde und nach jeder Änderung des Feeds neu gerechnet). */
	StreakGuard streakGuard() {
		long second = now() / 1000L;
		List<ReelsPost> list = reels.backend().feed();
		if (guard == null || second != guardSecond || list != guardFeed) {
			guardSecond = second;
			guardFeed = list;
			StreakGuard g = reels.backend().streakGuard(now());
			guard = g != null ? g : StreakGuard.NONE;
		}
		return guard;
	}

	/** Welcher Knopf gerade „Sicher? Nochmal tippen“ zeigt: 0 keiner, 1 „Heute pausieren“, 2 „Gestern retten“. */
	int streakArmed() {
		if (streakArmed != 0 && ticker() - streakArmedAt >= STREAK_CONFIRM_MS) streakArmed = 0;
		return streakArmed;
	}

	/**
	 * „Heute pausieren“ bzw. „Gestern retten“. Der erste Druck fragt nur nach; der zweite löst den Schutz ein – aber erst
	 * {@link #POST_GUARD_MS} nach dem ersten (ein Doppelklick gibt nichts aus), und Enter erst, nachdem es einmal
	 * losgelassen war (eine gehaltene Taste auch nicht).
	 */
	void useProtection(boolean yesterday, boolean byEnter) {
		if (sheet != Sheet.STREAK || disposed) return;
		int button = yesterday ? 2 : 1;
		long t = ticker();
		if (streakArmed() != button) {
			streakArmed = button;
			streakArmedAt = t;
			streakEnterUp = false;
			host.click();
			return;
		}
		if (t - streakArmedAt < POST_GUARD_MS || (byEnter && !streakEnterUp)) return;
		streakArmed = 0;
		StreakGuard.Result result = reels.protect(yesterday, now());
		// Streak und Schutz-Stand sofort neu rechnen.
		streakSecond = Long.MIN_VALUE;
		guard = null;
		if (result != StreakGuard.Result.OK) return;
		closeSheet();
		host.click();
		if (yesterday) {
			int days = streak();
			toast(days == 1 ? I18n.tr("reels.toast.streak.rescued.one", days) : I18n.tr("reels.toast.streak.rescued", days));
		} else {
			toast(I18n.tr("reels.toast.streak.paused"));
		}
	}

	/** Weicht die gespeicherte Version oder der Loader eines Posts von diesem Spiel ab? Mit unbekannten Werten nie. */
	boolean versionDiffers(ReelsMeta m) {
		ReelsMeta own = ownMeta;
		if (m == null || own == null) return false;
		if (m.mc != null && own.mc != null && !m.mc.equals(own.mc)) return true;
		return m.loader != null && own.loader != null && !m.loader.equals(own.loader);
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
		if (isClosing()) return true;
		// Im Kommentar-Blatt blättert das Rad in den Kommentaren (nach oben = ältere), nie im Feed.
		if (view == View.COMMENTS) commentScroll.wheel(-amount);
		else if (view == View.FEED && sheet == Sheet.NONE) pager.wheel(amount);
		return true;
	}

	@Override
	public boolean keyPressed(int rawKey, UiKey key, boolean shift) {
		if (isClosing()) return true;
		if (key != null && key == heldKey) {
			// Tastenwiederholung der Taste, die eben die Ebene gewechselt hat: verbraucht, sonst nichts.
			if (down(key)) return true;
			heldKey = null;
		}
		if (view == View.COMMENTS) {
			commentKey(key);
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
		if (view == View.FEED && sheet != Sheet.NONE) {
			if (key == UiKey.ENTER || key == UiKey.TAB || key == UiKey.ESCAPE) heldKey = key;
			return sheetKey(key);
		}
		switch (key) {
			case ENTER:
				heldKey = key;
				openComments();
				return true;
			case TAB:
				// „Feed“ (Filter und Reihenfolge) – ohne einen einzigen Post gibt es nichts zu filtern.
				heldKey = key;
				openSheet(Sheet.FILTER);
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
	 * Taste im Feld von „Neuer Post“. Der Cursor bleibt immer am Ende: das Feld zeigt ihn dort und blättert nicht im
	 * Text – Pfeile, Pos1 und Ende würden ihn sonst unsichtbar verschieben. (Das Kommentarfeld hat eine bewegliche
	 * Schreibmarke: {@link #editFree}.)
	 */
	private static void edit(TextInput input, UiKey key) {
		if (key == UiKey.LEFT || key == UiKey.RIGHT || key == UiKey.HOME || key == UiKey.END) return;
		if (key == UiKey.BACKSPACE) {
			// Ein Zeichenpaar (Emoji aus der Zwischenablage) geht als Ganzes weg – eine Hälfte allein ist kein Zeichen.
			String text = input.text();
			int n = text.length();
			if (n >= 2 && Character.isLowSurrogate(text.charAt(n - 1)) && Character.isHighSurrogate(text.charAt(n - 2))) {
				input.key(key);
			}
		}
		input.key(key);
	}

	/**
	 * Taste im Kommentarfeld mit beweglicher Schreibmarke: Pfeile, Pos1 und Ende bewegen sie, Rücktaste und Entf löschen
	 * an ihr. Ein Zeichenpaar (Emoji) zählt dabei als ein Zeichen – die Schreibmarke steht nie mitten darin, und es geht
	 * als Ganzes weg. Andere Tasten tun nichts.
	 */
	private static void editFree(TextInput input, UiKey key) {
		if (key != UiKey.LEFT && key != UiKey.RIGHT && key != UiKey.HOME && key != UiKey.END && key != UiKey.BACKSPACE
				&& key != UiKey.DELETE) {
			return;
		}
		String text = input.text();
		int at = input.cursor();
		boolean pairBefore = at >= 2 && Character.isLowSurrogate(text.charAt(at - 1)) && Character.isHighSurrogate(text.charAt(at - 2));
		boolean pairAfter = at + 1 < text.length() && Character.isHighSurrogate(text.charAt(at))
				&& Character.isLowSurrogate(text.charAt(at + 1));
		if ((key == UiKey.LEFT || key == UiKey.BACKSPACE) && pairBefore) input.key(key);
		if ((key == UiKey.RIGHT || key == UiKey.DELETE) && pairAfter) input.key(key);
		input.key(key);
	}

	/**
	 * Einfügen aus der Zwischenablage, an der Schreibmarke. Ein Zeichenpaar (Emoji) kommt ganz oder gar nicht – auch an
	 * der Längengrenze.
	 */
	private void paste(TextInput input) {
		String text = host.paste();
		if (text == null) return;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (Character.isHighSurrogate(ch) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
				// Feld voll: Schluss. Passt nur die zweite Hälfte nicht mehr hinein, geht die erste wieder heraus.
				if (!input.type(ch)) break;
				if (!input.type(text.charAt(i + 1))) {
					input.key(UiKey.BACKSPACE);
					break;
				}
				i++;
				continue;
			}
			if (Character.isSurrogate(ch)) continue;
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
		// Die erste Hälfte eines Zeichenpaars (Emoji) nur, wenn auch die zweite noch ins Feld passt.
		if (Character.isHighSurrogate(ch) && commentInput.text().length() + 2 > ReelsComment.MAX_TEXT) return true;
		// Und die zweite nur direkt hinter ihrer ersten.
		if (Character.isLowSurrogate(ch)) {
			int at = commentInput.cursor();
			if (at == 0 || !Character.isHighSurrogate(commentInput.text().charAt(at - 1))) return true;
		}
		commentInput.type(ch);
		refreshSuggestions();
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
		closeSheet();
		sheetBusy = false;
		// Was der Hintergrund noch fertig macht, meldet niemand mehr: eine gespeicherte Datei bleibt einfach liegen.
		results.clear();
		toastQueue.clear();
		toastAction = null;
		toastPing = false;
		closeComments();
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

	/** IDs der Posts, die der Feed gerade zeigt (gefiltert und sortiert), in dieser Reihenfolge. */
	public List<String> testShown() {
		refreshFeed();
		List<String> ids = new ArrayList<String>();
		for (ReelsPost p : shown) ids.add(p.id);
		return ids;
	}

	/**
	 * Wo eine Stelle des Handys im letzten Bild lag: {x, y, Breite, Höhe} in Fensterpixeln, oder null. Namen:
	 * {@code phone}, {@code screen}, {@code photo}, {@code heart}, {@code comment}, {@code save}, {@code camera},
	 * {@code rotate}, {@code home}, {@code streak}, {@code filterChip}, {@code metaShader}, {@code metaPack},
	 * {@code metaVersion}, {@code pingBadge}; Kommentare: {@code commentSheet}, {@code commentsBack}, {@code commentsClose},
	 * {@code commentInput}, {@code commentCaret}, {@code commentRow<i>}, {@code commentReply<i>}, {@code commentEdit<i>}
	 * (i = Stelle in der Liste, 0 = der älteste), {@code commentTarget}, {@code commentBanner}, {@code commentCancel},
	 * {@code commentNote}, {@code suggest0} bis {@code suggest4}; Kamera: {@code viewfinder},
	 * {@code camBack}, {@code camNote}, {@code camPhoto}, {@code camSelfie}, {@code camShutter}, {@code camFlip},
	 * {@code camThumb}; Neuer Post: {@code composePhoto}, {@code composeField}, {@code composeRetry},
	 * {@code composePost}; Blätter: {@code saveSheet}, {@code saveBack}, {@code saveClose}, {@code saveDo},
	 * {@code saveCopy}, {@code saveFolder}, {@code filterSheet}, {@code filterBack}, {@code filterClose},
	 * {@code filterAll}, {@code filterFriends}, {@code filterMine}, {@code sortNewest}, {@code sortPopular},
	 * {@code streakSheet}, {@code streakBack}, {@code streakClose}, {@code streakPause}, {@code streakRescue}; dazu
	 * {@code toast}.
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

	/** Das Blatt, das gerade über dem Feed liegt. */
	public Sheet testSheet() {
		return sheet;
	}

	/** Läuft gerade Speichern, Kopieren oder Ordner öffnen im Hintergrund? */
	public boolean testSheetBusy() {
		return sheetBusy;
	}

	public FeedFilter testFilter() {
		return filter;
	}

	public FeedSort testSort() {
		return sort;
	}

	/** Was Enter im Kommentar-Blatt gerade tut: {@code NEW}, {@code REPLY} oder {@code EDIT}. */
	public String testCommentMode() {
		return commentMode.name();
	}

	/** Text im Kommentarfeld. */
	public String testCommentText() {
		return commentInput.text();
	}

	/** Stelle der Schreibmarke im Kommentarfeld (0 = vor dem ersten Zeichen). */
	public int testCommentCaret() {
		return commentInput.cursor();
	}

	/** Die Namen, die beim Tippen von {@code @} gerade zur Wahl stehen. */
	public List<String> testSuggestions() {
		return new ArrayList<String>(suggestions);
	}

	/** So viele Zeilen der Kommentar-Liste sind unten übersprungen (0 = ganz unten). */
	public int testCommentScroll() {
		return commentScroll.index();
	}

	/** Ungesehene Erwähnungen und Antworten im ganzen Feed, frisch gerechnet. */
	public int testUnseenPings() {
		return Pings.unseen(reels.backend().feed(), host.playerName());
	}

	/** Stand des Streak-Schutzes, frisch gerechnet. */
	public StreakGuard testStreakGuard() {
		StreakGuard g = reels.backend().streakGuard(now());
		return g != null ? g : StreakGuard.NONE;
	}
}
