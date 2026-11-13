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
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.reels.ReelsComment;
import dev.theredstonee.trsclient.core.reels.ReelsPost;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.FadeCanvas;
import dev.theredstonee.trsclient.core.ui.TextInput;
import dev.theredstonee.trsclient.core.ui.TextureRef;
import dev.theredstonee.trsclient.core.ui.UiKey;
import dev.theredstonee.trsclient.core.ui.UiScreen;

import java.nio.file.Path;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Handy im Spiel (MC-Reels): zwei gezeichnete Arme halten ein Handy, auf dem der Foto-Feed läuft. Versionsunabhängig
 * – der Baum liefert nur Fenstergröße, Skin, Tasten und Töne ({@link Host}) und reicht Zeichnen und Eingaben durch.
 *
 * <p><b>Fensterpixel, nicht GUI-Einheiten:</b> gezeichnet wird mit {@code scale(1 / GUI-Skalierung)}, alle Maße kommen
 * aus der Fenstergröße ({@link PhoneLayout}). Handy und Arme sind deshalb bei jeder GUI-Skalierung gleich groß, und
 * die Arme laufen immer unten aus dem Bild.
 *
 * <p>Reihenfolge: Schleier über dem Spiel, Arme (aus dem Skin, Innenseite vor Vorderseite), Schatten und Gehäuse, dann
 * der Bildschirm ({@link FeedView}). Ansichten in diesem Stand: Feed und Kommentare; „Handy drehen“ wechselt zwischen
 * quer und hochkant und merkt sich die Haltung.
 *
 * <p><b>Aufräumen:</b> {@link #dispose()} gibt die Texturen frei, sperrt die Handy-Taste bis zum Loslassen und sichert
 * den Feed. Es läuft nach der Schließ-Animation und muss vom Baum zusätzlich gerufen werden, wenn der Bildschirm anders
 * verschwindet (Tod, Verbindung getrennt) – mehrfach rufen schadet nicht.
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

		/** Bildschirm wirklich schließen (nach der Animation). */
		void close();
	}

	/** Was der Bildschirm des Handys zeigt. */
	public enum View {
		FEED,
		COMMENTS
	}

	/** So weit (Anteil der Fensterhöhe) kommen Arme und Handy beim Öffnen von unten herauf. */
	static final float SLIDE = 0.35f;
	/** Fotos im Feed: höchstens so groß geladen (längste Seite), so viele zugleich. */
	static final int PHOTO_MAX = 1024;
	static final int PHOTO_CACHE = 6;

	private final Reels reels;
	private final Host host;
	private final TrsModules modules;
	private final FeedPager pager = new FeedPager();
	private final FeedView feedView = new FeedView(this);
	private final PhoneVeil veil = new PhoneVeil();
	private final Map<String, int[]> spots = new HashMap<String, int[]>();
	/** Eingabefeld des Kommentar-Blatts. */
	final TextInput commentInput = new TextInput(ReelsComment.MAX_TEXT);
	/** Wie viele Kommentare das Blatt zuletzt gezeigt hat (Tests). */
	int commentsShown;

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

	public PhoneUi(Reels reels, Host host) {
		if (reels == null || host == null) throw new IllegalArgumentException("reels/host");
		this.reels = reels;
		this.host = host;
		this.modules = reels.modules();
		PhoneHold saved = modules.reels.hold.get();
		this.hold = saved != null ? saved : PhoneHold.LANDSCAPE;
		I18n.refresh();
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
				PhoneBody.draw(c, l);
				spot("phone", l.phoneX, l.phoneY, l.phoneW, l.phoneH);
				spot("screen", l.screenX, l.screenY, l.screenW, l.screenH);
				feedView.draw(c, l);
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

	/** „Feed“ in der Leiste: zurück zum Feed (schließt die Kommentare). */
	void home() {
		closeComments();
	}

	/** Kamera öffnen – die Kamera-Ansicht kommt mit dem nächsten Bauschritt; bis dahin tut der Knopf nichts. */
	void openCamera() {
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
				commentInput.key(key);
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

	private static boolean isKey(String pressed, KeySetting key) {
		return pressed != null && key.isBound() && pressed.equals(KeyCombo.main(key.get()));
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
		if (view != View.COMMENTS || isClosing()) return false;
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
	 * Aufräumen, wenn das Handy weg ist: Texturen freigeben, die Handy-Taste bis zum Loslassen sperren, den Feed
	 * sichern. Mehrfach rufen schadet nicht. Render-Thread.
	 */
	public void dispose() {
		if (disposed) return;
		disposed = true;
		commentInput.setFocused(false);
		if (photos != null) {
			photos.releaseAll();
			photos = null;
		}
		veil.release();
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
	 * {@code home}, {@code streak}, {@code commentInput}, {@code commentsClose}, {@code commentsBack}.
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
}
