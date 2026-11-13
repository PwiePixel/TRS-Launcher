package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.reels.ArmRig;
import dev.theredstonee.trsclient.core.reels.ArmSkin;
import dev.theredstonee.trsclient.core.reels.FeedFilter;
import dev.theredstonee.trsclient.core.reels.FeedSort;
import dev.theredstonee.trsclient.core.reels.LocalReelsBackend;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.reels.ReelsBackend;
import dev.theredstonee.trsclient.core.reels.ReelsComment;
import dev.theredstonee.trsclient.core.reels.ReelsExport;
import dev.theredstonee.trsclient.core.reels.ReelsMeta;
import dev.theredstonee.trsclient.core.reels.ReelsPost;
import dev.theredstonee.trsclient.core.reels.ReelsTime;
import dev.theredstonee.trsclient.core.reels.StreakGuard;
import dev.theredstonee.trsclient.core.screenshot.ImageClipboard;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.TextureRef;
import dev.theredstonee.trsclient.core.ui.Textures;
import dev.theredstonee.trsclient.core.ui.UiKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das MC-Reels-Handy ohne Spiel: zeichnen in Fensterpixeln bei jeder GUI-Skalierung, Arme aus dem Skin (breit, schlank,
 * Innenseite, Ärmel), Texte bleiben auf dem Bildschirm des Handys, Feed blättern, Herz, Kommentare, Drehen, Schließen
 * und Aufräumen. Dazu die Blätter „Bild“ (speichern, kopieren, Ordner öffnen), „Feed“ (Filter und Reihenfolge) und
 * „Streak“ (Streak-Schutz) und die Marken am Post. Zwischenablage und Dateimanager des Systems fasst kein Test an: beides
 * geht über {@link FakeHost}.
 */
class PhoneUiTest {
	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	/** 2026-11-13 12:00:00 UTC. */
	private static final long NOON = 1794571200000L;
	private static final String[] LANGUAGES = {"en", "de", "es", "fr", "nl", "pl", "pt-BR", "tr"};
	private static final Executor DIRECT = new Executor() {
		@Override
		public void execute(Runnable command) {
			command.run();
		}
	};

	/**
	 * Zeichenfläche, die mitschreibt, was wo landet – in Fensterpixeln. Wie im Spiel arbeitet sie in GUI-Einheiten
	 * (Grundmatrix = GUI-Skalierung); Schrift: 6 px je Zeichen, 8 px hoch.
	 */
	static final class RecCanvas implements Canvas {
		static final class Fill {
			final double x1, y1, x2, y2;
			final int argb;

			Fill(double x1, double y1, double x2, double y2, int argb) {
				this.x1 = x1;
				this.y1 = y1;
				this.x2 = x2;
				this.y2 = y2;
				this.argb = argb;
			}

			@Override
			public String toString() {
				return "Fill(" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ", " + Integer.toHexString(argb) + ")";
			}
		}

		static final class Text {
			final String text;
			final double x, y, w, h, scale;
			final int argb;

			Text(String text, double x, double y, double w, double h, double scale, int argb) {
				this.text = text;
				this.x = x;
				this.y = y;
				this.w = w;
				this.h = h;
				this.scale = scale;
				this.argb = argb;
			}

			@Override
			public String toString() {
				return "\"" + text + "\"@(" + x + ", " + y + ") " + w + "x" + h;
			}
		}

		static final class Img {
			final TextureRef texture;
			/** Ausschnitt in ganzen Texeln (bei den Armen: die Texel, in denen der Blit liest). */
			final float u, v;
			final int w, h, argb;
			/** x' = m0·x + m2·y + m4, y' = m1·x + m3·y + m5 (Texel → Fensterpixel). */
			final double[] m;
			/** Wie gezeichnet: Ausschnitt in den Einheiten der Textur (Arme: 64tel-Texel, etwas nach innen genommen). */
			final float rawU, rawV;
			final int rawW, rawH;

			Img(TextureRef texture, float u, float v, int w, int h, int argb, double[] m) {
				this.texture = texture;
				this.rawU = u;
				this.rawV = v;
				this.rawW = w;
				this.rawH = h;
				this.argb = argb;
				// Der Skin ist 64×64; ist er feiner adressiert (Arme), hier zurück in ganze Texel – die Fläche bleibt gleich.
				int sub = "skin".equals(texture.id) ? texture.width / 64 : 1;
				if (sub > 1) {
					this.u = (float) Math.floor(u / sub);
					this.v = (float) Math.floor(v / sub);
					this.w = Math.max(1, Math.round(w / (float) sub));
					this.h = Math.max(1, Math.round(h / (float) sub));
					m[0] *= w / (double) this.w;
					m[1] *= w / (double) this.w;
					m[2] *= h / (double) this.h;
					m[3] *= h / (double) this.h;
				} else {
					this.u = u;
					this.v = v;
					this.w = w;
					this.h = h;
				}
				this.m = m;
			}

			/** Breite des Blits quer (Fensterpixel). */
			double across() {
				return Math.hypot(m[0], m[1]) * w;
			}

			double[] corner(int i) {
				double x = (i & 1) == 0 ? 0 : w, y = (i & 2) == 0 ? 0 : h;
				return new double[]{m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]};
			}

			double minX() {
				double r = Double.MAX_VALUE;
				for (int i = 0; i < 4; i++) r = Math.min(r, corner(i)[0]);
				return r;
			}

			double maxX() {
				double r = -Double.MAX_VALUE;
				for (int i = 0; i < 4; i++) r = Math.max(r, corner(i)[0]);
				return r;
			}

			double minY() {
				double r = Double.MAX_VALUE;
				for (int i = 0; i < 4; i++) r = Math.min(r, corner(i)[1]);
				return r;
			}

			double maxY() {
				double r = -Double.MAX_VALUE;
				for (int i = 0; i < 4; i++) r = Math.max(r, corner(i)[1]);
				return r;
			}

			@Override
			public String toString() {
				return "Img(" + texture.id + " uv " + u + "," + v + " " + w + "x" + h + " → " + minX() + "," + minY() + " … " + maxX()
						+ "," + maxY() + ")";
			}
		}

		final List<Fill> fills = new ArrayList<Fill>();
		final List<Text> texts = new ArrayList<Text>();
		final List<Img> images = new ArrayList<Img>();
		/** Alles in Zeichenreihenfolge (Fill, Text oder Img). */
		final List<Object> order = new ArrayList<Object>();
		boolean canImages = true;
		int underflow;
		private double[] m;
		private final Deque<double[]> stack = new ArrayDeque<double[]>();

		RecCanvas(int guiScale) {
			m = new double[]{guiScale, 0, 0, guiScale, 0, 0};
		}

		int depth() {
			return stack.size();
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int argb) {
			if (x2 <= x1 || y2 <= y1 || (argb >>> 24) == 0) return;
			Fill f = new Fill(m[0] * x1 + m[4], m[3] * y1 + m[5], m[0] * x2 + m[4], m[3] * y2 + m[5], argb);
			fills.add(f);
			order.add(f);
		}

		@Override
		public void text(String text, int x, int y, int argb, boolean shadow) {
			if (text == null || text.isEmpty()) return;
			Text t = new Text(text, m[0] * x + m[4], m[3] * y + m[5], textWidth(text) * m[0], 8 * m[3], m[0], argb);
			texts.add(t);
			order.add(t);
		}

		@Override
		public int textWidth(String text) {
			return text == null ? 0 : text.codePointCount(0, text.length()) * 6;
		}

		@Override
		public int lineHeight() {
			return 9;
		}

		@Override
		public String clip(String text, int maxWidth) {
			int n = Math.max(0, maxWidth / 6);
			return text.length() <= n ? text : text.substring(0, n);
		}

		@Override
		public void flush() {
		}

		@Override
		public void scissor(int x1, int y1, int x2, int y2) {
		}

		@Override
		public void noScissor() {
		}

		@Override
		public void raise(float z) {
		}

		@Override
		public void push() {
			stack.push(m.clone());
		}

		@Override
		public void pop() {
			if (stack.isEmpty()) {
				underflow++;
				return;
			}
			m = stack.pop();
		}

		@Override
		public void translate(float x, float y) {
			m[4] += m[0] * x + m[2] * y;
			m[5] += m[1] * x + m[3] * y;
		}

		@Override
		public void scale(float factor) {
			scale(factor, factor);
		}

		@Override
		public void scale(float sx, float sy) {
			m[0] *= sx;
			m[1] *= sx;
			m[2] *= sy;
			m[3] *= sy;
		}

		@Override
		public void rotate(float radians) {
			double cos = Math.cos(radians), sin = Math.sin(radians);
			double a = m[0] * cos + m[2] * sin, b = m[1] * cos + m[3] * sin;
			double c = -m[0] * sin + m[2] * cos, d = -m[1] * sin + m[3] * cos;
			m[0] = a;
			m[1] = b;
			m[2] = c;
			m[3] = d;
		}

		@Override
		public boolean images() {
			return canImages;
		}

		@Override
		public void image(TextureRef texture, float u, float v, int w, int h, int argb) {
			Img i = new Img(texture, u, v, w, h, argb, m.clone());
			images.add(i);
			order.add(i);
		}

		boolean shows(String part) {
			for (Text t : texts) if (t.text.contains(part)) return true;
			return false;
		}

		List<String> strings() {
			List<String> out = new ArrayList<String>();
			for (Text t : texts) out.add(t.text);
			return out;
		}

		List<Img> imagesOf(Object textureId) {
			List<Img> out = new ArrayList<Img>();
			for (Img i : images) if (textureId.equals(i.texture.id)) out.add(i);
			return out;
		}
	}

	/** Das „Spiel“ hinter dem Handy. */
	static final class FakeHost implements PhoneUi.Host {
		int w = 1920, h = 1080, gui = 2;
		boolean slim;
		boolean sleeveLeft = true, sleeveRight = true;
		String name = "PixelDEV_CAM";
		String clipboard;
		final Set<String> down = new HashSet<String>();
		int clicks;
		int closes;
		TextureRef skin = new TextureRef("skin", 64, 64);

		FakeHost size(int w, int h, int gui) {
			this.w = w;
			this.h = h;
			this.gui = gui;
			return this;
		}

		@Override
		public int windowWidth() {
			return w;
		}

		@Override
		public int windowHeight() {
			return h;
		}

		@Override
		public int guiScale() {
			return gui;
		}

		@Override
		public TextureRef skin() {
			return skin;
		}

		@Override
		public boolean slimSkin() {
			return slim;
		}

		@Override
		public boolean sleeve(boolean left) {
			return left ? sleeveLeft : sleeveRight;
		}

		@Override
		public String playerName() {
			return name;
		}

		@Override
		public String keyName(int rawKey) {
			return rawKey == 'K' ? "key.keyboard.k" : rawKey == 'R' ? "key.keyboard.r" : rawKey == 'X' ? "key.keyboard.x"
					: rawKey == 'F' ? "key.keyboard.f" : rawKey == ' ' ? "key.keyboard.space" : null;
		}

		@Override
		public boolean keyDown(String keyName) {
			return down.contains(keyName);
		}

		@Override
		public String keyLabel(String keyName) {
			return keyName.substring(keyName.lastIndexOf('.') + 1).toUpperCase(java.util.Locale.ROOT);
		}

		@Override
		public String paste() {
			return clipboard;
		}

		@Override
		public void click() {
			clicks++;
		}

		// --- Kamera: Bilder der „Welt“ ---
		/** Größe der gelieferten Bilder (0 = Fenstergröße). */
		int frameW, frameH;
		/** Liefert das „Spiel“ überhaupt Bilder? */
		boolean frames = true;
		final List<int[]> requests = new ArrayList<int[]>();
		final Deque<PhoneUi.Frame> queue = new ArrayDeque<PhoneUi.Frame>();
		float yaw, pitch;
		int turns;

		@Override
		public void turn(float yawDeg, float pitchDeg) {
			yaw += yawDeg;
			pitch += pitchDeg;
			turns++;
		}

		@Override
		public void requestFrame(boolean photo, int needW, int needH) {
			requests.add(new int[]{photo ? 1 : 0, needW, needH});
			if (!frames) return;
			int fw = frameW > 0 ? frameW : w, fh = frameH > 0 ? frameH : h;
			int[] argb = new int[fw * fh];
			// Erkennbares Muster: rot wächst nach rechts, grün nach unten.
			for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++) argb[y * fw + x] = 0xFF000000 | (x * 255 / fw) << 16 | (y * 255 / fh) << 8;
			queue.add(new PhoneUi.Frame(argb, fw, fh, photo));
		}

		@Override
		public PhoneUi.Frame pollFrame() {
			return queue.poll();
		}

		int requests(boolean photo) {
			int n = 0;
			for (int[] r : requests) if ((r[0] == 1) == photo) n++;
			return n;
		}

		@Override
		public void close() {
			closes++;
		}

		// --- Bild speichern, Marken, Filter: nichts davon erreicht das echte System ---
		/** Screenshots-Ordner des „Spiels“ (null: der Baum kennt keinen). */
		Path screenshots;
		/** Ordner, die „im Dateimanager gezeigt“ wurden, und ob das klappt. */
		final List<Path> opened = new ArrayList<Path>();
		boolean openOk = true;
		/** Bilder, die „in die Zwischenablage kopiert“ wurden, und was das „System“ dazu sagt. */
		final List<Path> copied = new ArrayList<Path>();
		ImageClipboard.Result copyResult = ImageClipboard.Result.OK;
		/** Womit das „Spiel“ läuft (null: unbekannt). */
		ReelsMeta meta;
		/** Namen der Freunde (null: unbekannt) und wie oft danach gefragt wurde. */
		Collection<String> friends;
		int friendCalls;
		/** Spieler auf dem „Server“ und wie oft danach gefragt wurde. */
		List<String> online = new ArrayList<String>();
		int onlineCalls;

		@Override
		public Path screenshotsDir() {
			return screenshots;
		}

		@Override
		public boolean openFolder(Path dir) {
			opened.add(dir);
			return openOk;
		}

		@Override
		public ImageClipboard.Result copyImage(Path png) {
			copied.add(png);
			return copyResult;
		}

		@Override
		public ReelsMeta photoMeta() {
			return meta;
		}

		@Override
		public Collection<String> friendNames() {
			friendCalls++;
			return friends;
		}

		@Override
		public List<String> onlinePlayers() {
			onlineCalls++;
			return online;
		}
	}

	/** Texturen-Ablage, die Hochladen und Freigeben zählt. */
	static final class FakeStore implements Textures.Store {
		final List<String> live = new ArrayList<String>();
		final List<String> released = new ArrayList<String>();
		int uploads;

		@Override
		public TextureRef upload(String name, int width, int height, int[] argb) {
			assertTrue(Textures.validName(name), "Texturname: " + name);
			assertEquals(width * height, argb.length, "Pixel von " + name);
			uploads++;
			if (!live.contains(name)) live.add(name);
			return new TextureRef(name, width, height);
		}

		@Override
		public void release(TextureRef texture) {
			released.add(String.valueOf(texture.id));
			live.remove(String.valueOf(texture.id));
		}

		@Override
		public TextureRef game(String location, int width, int height) {
			return new TextureRef(location, width, height);
		}

		@Override
		public Textures.DefaultSkin defaultSkin(UUID uuid) {
			return new Textures.DefaultSkin(new TextureRef("skin", 64, 64), false);
		}
	}

	@TempDir
	Path tmp;

	private final FakeHost host = new FakeHost();
	private final FakeStore store = new FakeStore();
	private Textures.Store storeBefore;
	private TrsModules modules;
	private LocalReelsBackend backend;
	private Reels reels;
	private int salt;

	@BeforeEach
	void setUp() {
		I18n.use("en");
		storeBefore = Textures.store();
		Textures.replaceForTests(store);
		modules = new TrsModules();
		backend = new LocalReelsBackend(tmp, UTC);
		reels = Reels.forTest(modules, backend, DIRECT, UTC);
		host.screenshots = tmp.resolve("screenshots");
	}

	@AfterEach
	void reset() {
		Textures.replaceForTests(storeBefore);
		Reels.clearForTest();
		I18n.use("en");
	}

	private PhoneUi ui(PhoneHold hold) {
		modules.reels.hold.set(hold);
		PhoneUi ui = new PhoneUi(reels, host);
		ui.testNow = NOON;
		return ui;
	}

	/** Ein Bild zeichnen (Handy ganz draußen), wie der Bildschirm der Version es tut: in GUI-Einheiten. */
	private RecCanvas frame(PhoneUi ui) {
		RecCanvas c = new RecCanvas(host.gui);
		ui.testOpenFully();
		ui.render(c, host.w / host.gui, host.h / host.gui, -1, -1);
		assertEquals(0, c.depth(), "push/pop nicht ausgeglichen");
		assertEquals(0, c.underflow, "pop ohne push");
		return c;
	}

	/** Post ohne Foto-Datei, {@code hoursAgo} Stunden vor {@link #NOON}. */
	private ReelsPost post(String caption, String author, int hoursAgo, boolean portrait) {
		long at = NOON - hoursAgo * 3600000L;
		ReelsPost p = backend.post(ReelsPost.newId(at, UTC, ++salt) + ".png", portrait ? 608 : 1280, portrait ? 1080 : 720, portrait,
				false, caption, author, at);
		assertNotNull(p);
		return p;
	}

	/** Post mit echter Foto-Datei ({@code w}×{@code h}). */
	private ReelsPost postWithPhoto(int w, int h, int hoursAgo) throws IOException {
		long at = NOON - hoursAgo * 3600000L;
		String id = ReelsPost.newId(at, UTC, ++salt);
		Path file = backend.newPhotoFile(id);
		Files.createDirectories(file.getParent());
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, (x * 255 / w) << 16 | (y * 255 / h) << 8 | 0x40);
		assertTrue(ImageIO.write(img, "png", file.toFile()));
		ReelsPost p = backend.post(id + ".png", w, h, h > w, false, "Foto " + w + "x" + h, host.name, at);
		assertNotNull(p);
		return p;
	}

	private void click(PhoneUi ui, String spot) {
		int[] r = ui.testSpot(spot);
		assertNotNull(r, "Stelle fehlt: " + spot);
		// Eingaben kommen in GUI-Einheiten.
		assertTrue(ui.inputClick((r[0] + r[2] / 2.0) / host.gui, (r[1] + r[3] / 2.0) / host.gui, 0), "Klick auf " + spot);
	}

	private static RecCanvas.Fill fillOf(RecCanvas c, int argb, boolean largest) {
		RecCanvas.Fill best = null;
		for (RecCanvas.Fill f : c.fills) {
			if (f.argb != argb) continue;
			if (best == null || (largest && (f.x2 - f.x1) * (f.y2 - f.y1) > (best.x2 - best.x1) * (best.y2 - best.y1))) best = f;
		}
		return best;
	}

	// --- Fensterpixel, GUI-Skalierung ---

	@Test
	void phoneAndArmsDependOnTheWindowOnlyNeverOnTheGuiScale() {
		post("Schaut mal, wer bei mir eingezogen ist!", host.name, 2, false);
		int[][] windows = {{1920, 1080}, {854, 480}};
		for (PhoneHold hold : PhoneHold.values()) {
			for (int[] win : windows) {
				PhoneLayout l = PhoneLayout.of(win[0], win[1], hold);
				String first = null;
				for (int gui : new int[]{1, 2, 4}) {
					host.size(win[0], win[1], gui);
					PhoneUi ui = ui(hold);
					RecCanvas c = frame(ui);
					String tag = hold + " " + win[0] + "x" + win[1] + " GUI " + gui;

					// Gehäuse: das größte Rechteck in der Gehäusefarbe reicht über die ganze Breite des Handys.
					RecCanvas.Fill body = fillOf(c, PhoneBody.BODY, true);
					assertNotNull(body, tag);
					assertEquals(l.phoneX, body.x1, 1e-3, tag);
					assertEquals(l.phoneX + l.phoneW, body.x2, 1e-3, tag);
					RecCanvas.Fill screen = fillOf(c, PhoneBody.SCREEN, true);
					assertEquals(l.screenX, screen.x1, 1e-3, tag);
					assertEquals(l.screenY, screen.y1, 1e-3, tag);
					assertEquals(l.screenX + l.screenW, screen.x2, 1e-3, tag);
					assertEquals(l.screenY + l.screenH, screen.y2, 1e-3, tag);
					assertEquals(l.phoneW, ui.testSpot("phone")[2], tag);

					// Alles zusammen (Arme, Gehäuse, Texte) liegt bei jeder GUI-Skalierung auf denselben Fensterpixeln.
					StringBuilder sb = new StringBuilder();
					for (RecCanvas.Fill f : c.fills) {
						sb.append(Math.round(f.x1)).append(',').append(Math.round(f.y1)).append(',').append(Math.round(f.x2))
								.append(',').append(Math.round(f.y2)).append(';');
					}
					for (RecCanvas.Text t : c.texts) {
						sb.append(t.text).append('@').append(Math.round(t.x)).append(',').append(Math.round(t.y)).append('x')
								.append(Math.round(t.scale)).append(';');
					}
					for (RecCanvas.Img i : c.images) {
						sb.append(Math.round(i.minX())).append(',').append(Math.round(i.maxY())).append(';');
					}
					if (first == null) first = sb.toString();
					else assertEquals(first, sb.toString(), tag + ": anders als bei GUI 1");
					ui.dispose();
				}
			}
		}
	}

	@Test
	void everySizeAndLanguageDrawsAndKeepsTextOnThePhoneScreen() {
		host.name = "PixelDEV_CAM_16x";
		// Kleine Bilder der „Welt“, damit die vielen Fotos dieses Tests schnell gespeichert sind.
		host.frameW = 320;
		host.frameH = 180;
		// Beide Posts tragen alle Angaben, mit den längsten Namen, die gespeichert werden (40 und 64 Zeichen).
		host.meta = new ReelsMeta("26.2", "fabric", null, null);
		long oldAt = NOON - 30 * 3600000L;
		ReelsPost old = backend.post(ReelsPost.newId(oldAt, UTC, ++salt) + ".png", 608, 1080, true, false,
				"Aussicht hinter meiner Basis – und ein ziemlich langer Text dazu, der umbrechen muss!", "Rotkaeppchen_2010", oldAt,
				new ReelsMeta("1.21.4", "neoforge", chars(40), chars(64)));
		ReelsPost top = postMeta("Schaut mal, wer bei mir eingezogen ist! Der Panda bleibt jetzt für immer hier bei mir.", host.name, 2,
				new ReelsMeta("26.2", "fabric", chars(40), chars(64)));
		reels.comment(top.id, "Rotkaeppchen_2010", "Wie süß! Den will ich auch haben, wo hast du den denn gefunden?", NOON - 1000);
		reels.comment(top.id, host.name, "Im Bambuswald hinter dem Dorf.", NOON - 500);
		reels.comment(old.id, "Du", "Schön.", NOON - 100);
		int[][] windows = {{1920, 1080}, {1280, 720}, {854, 480}, {2560, 1080}, {3840, 2160}, {1024, 768}, {1280, 1024},
				{800, 1000}, {640, 480}, {320, 240}};
		for (String lang : LANGUAGES) {
			I18n.use(lang);
			for (int[] win : windows) {
				for (PhoneHold hold : PhoneHold.values()) {
					host.size(win[0], win[1], 2);
					String tag = lang + " " + hold + " " + win[0] + "x" + win[1];
					PhoneUi ui = ui(hold);
					onScreen(frame(ui), ui, tag + " Feed");
					ui.keyPressed(0, UiKey.ENTER, false);
					assertEquals(PhoneUi.View.COMMENTS, ui.testView(), tag);
					for (char ch : "Ein Kommentar, der länger ist als das Eingabefeld breit".toCharArray()) ui.inputChar(ch);
					onScreen(frame(ui), ui, tag + " Kommentare");
					ui.keyPressed(0, UiKey.ESCAPE, false);
					ui.mouseScrolled(0, 0, -1);
					assertEquals(1, ui.testPage(), tag);
					onScreen(frame(ui), ui, tag + " Seite 2");
					// Die Blätter „Bild“ und „Feed“, eine der neuen Meldungen, und der Feed, wenn der Filter nichts zeigt.
					ui.openSheet(PhoneUi.Sheet.SAVE);
					assertEquals(PhoneUi.Sheet.SAVE, ui.testSheet(), tag);
					onScreen(frame(ui), ui, tag + " Blatt Bild");
					ui.toast(I18n.tr("reels.toast.copied"));
					onScreen(frame(ui), ui, tag + " Blatt Bild mit Meldung");
					ui.openSheet(PhoneUi.Sheet.FILTER);
					onScreen(frame(ui), ui, tag + " Blatt Feed");
					ui.setFilter(FeedFilter.FRIENDS);
					ui.setSort(FeedSort.POPULAR);
					onScreen(frame(ui), ui, tag + " Blatt Feed, Freunde");
					ui.closeSheet();
					onScreen(frame(ui), ui, tag + " Freunde leer");
					String me = host.name;
					host.name = "Niemand";
					ui.setFilter(FeedFilter.MINE);
					onScreen(frame(ui), ui, tag + " Ich leer");
					host.name = me;
					ui.setFilter(FeedFilter.ALL);
					ui.setSort(FeedSort.NEWEST);
					ui.openSheet(PhoneUi.Sheet.STREAK);
					onScreen(frame(ui), ui, tag + " Blatt Streak");
					ui.closeSheet();
					// Kamera (Foto und Selfie) und „Neuer Post“ mit langem Text.
					ui.testTicker = 5000;
					ui.openCamera();
					assertEquals(PhoneUi.View.CAMERA, ui.testView(), tag);
					onScreen(frame(ui), ui, tag + " Kamera");
					ui.keyPressed(0, UiKey.TAB, false);
					assertTrue(ui.testSelfie(), tag);
					onScreen(frame(ui), ui, tag + " Kamera Selfie");
					ui.keyPressed(0, UiKey.ENTER, false);
					onScreen(frame(ui), ui, tag + " Kamera löst aus");
					onScreen(frame(ui), ui, tag + " Neuer Post");
					assertEquals(PhoneUi.View.COMPOSE, ui.testView(), tag);
					for (char ch : "Ein Text zum Foto, der länger ist als das Feld breit".toCharArray()) ui.inputChar(ch);
					onScreen(frame(ui), ui, tag + " Neuer Post mit Text");
					ui.toast(I18n.tr("reels.toast.failed"));
					onScreen(frame(ui), ui, tag + " Meldung");
					ui.dispose();
				}
			}
		}
		noneOffScreen();
		// Leerer Feed (mit dem Hinweis auf die Tasten).
		Reels.forTest(modules, new LocalReelsBackend(tmp.resolve("leer"), UTC), DIRECT, UTC);
		reels = Reels.get();
		for (String lang : LANGUAGES) {
			I18n.use(lang);
			for (int[] win : windows) {
				for (PhoneHold hold : PhoneHold.values()) {
					host.size(win[0], win[1], 2);
					PhoneUi ui = ui(hold);
					onScreen(frame(ui), ui, lang + " " + hold + " " + win[0] + "x" + win[1] + " leer");
					ui.dispose();
				}
			}
		}
		noneOffScreen();
		// Streak-Schutz: beide Knöpfe (gestern lässt sich retten), die Nachfrage, die Meldung beim Herausholen und die
		// zurückgestellte Uhr.
		backend = new LocalReelsBackend(tmp.resolve("retten"), UTC);
		reels = Reels.forTest(modules, backend, DIRECT, UTC);
		for (int hours : new int[]{98, 74, 50, 2}) post("Tag", host.name, hours, false);
		for (String lang : LANGUAGES) {
			I18n.use(lang);
			for (int[] win : windows) {
				for (PhoneHold hold : PhoneHold.values()) {
					host.size(win[0], win[1], 2);
					String tag = lang + " " + hold + " " + win[0] + "x" + win[1];
					PhoneUi ui = ui(hold);
					ui.testTicker = 5000;
					ui.toast(I18n.tr("reels.toast.streak.rescuable"));
					onScreen(frame(ui), ui, tag + " Meldung Retten");
					ui.openStreakSheet();
					assertTrue(ui.testStreakGuard().canRescueYesterday, tag);
					onScreen(frame(ui), ui, tag + " Streak retten");
					ui.useProtection(true, false);
					assertEquals(2, ui.streakArmed(), tag);
					onScreen(frame(ui), ui, tag + " Streak Nachfrage");
					ui.dispose();
					PhoneUi back = ui(hold);
					back.testNow = NOON - 5 * 86400000L;
					back.testTicker = 5000;
					frame(back);
					back.openStreakSheet();
					assertTrue(back.testStreakGuard().clockBehind, tag);
					onScreen(frame(back), back, tag + " Streak Uhr");
					back.dispose();
				}
			}
		}
		noneOffScreen();
		// Gestern fehlt, aber der Monat ist aufgebraucht.
		backend = new LocalReelsBackend(tmp.resolve("verpasst"), UTC);
		reels = Reels.forTest(modules, backend, DIRECT, UTC);
		useBothProtections();
		for (String lang : LANGUAGES) {
			I18n.use(lang);
			for (int[] win : windows) {
				for (PhoneHold hold : PhoneHold.values()) {
					host.size(win[0], win[1], 2);
					PhoneUi ui = ui(hold);
					frame(ui);
					ui.openStreakSheet();
					StreakGuard g = ui.testStreakGuard();
					assertTrue(g.rescueOffered && g.leftForYesterday == 0, lang + " " + hold);
					onScreen(frame(ui), ui, lang + " " + hold + " " + win[0] + "x" + win[1] + " Streak verpasst");
					ui.dispose();
				}
			}
		}
		noneOffScreen();
	}

	private void noneOffScreen() {
		if (offScreen.isEmpty()) return;
		StringBuilder sb = new StringBuilder(offScreen.size() + " Stellen außerhalb des Handy-Bildschirms:");
		for (int i = 0; i < offScreen.size() && i < 40; i++) sb.append("\n").append(offScreen.get(i));
		throw new AssertionError(sb.toString());
	}

	/** Was {@link #onScreen} außerhalb des Bildschirms gefunden hat. */
	private final List<String> offScreen = new ArrayList<String>();

	/** Alles, was nach dem schwarzen Bildschirm gezeichnet wird (der Inhalt), liegt innerhalb des Bildschirms. */
	private void onScreen(RecCanvas c, PhoneUi ui, String tag) {
		int[] s = ui.testSpot("screen");
		assertNotNull(s, tag);
		double x1 = s[0] - 0.01, y1 = s[1] - 0.01, x2 = s[0] + s[2] + 0.01, y2 = s[1] + s[3] + 0.01;
		boolean inside = false;
		int content = 0;
		for (Object o : c.order) {
			if (!inside) {
				if (o instanceof RecCanvas.Fill) {
					RecCanvas.Fill f = (RecCanvas.Fill) o;
					inside = f.argb == PhoneBody.SCREEN && Math.abs(f.x1 - s[0]) < 0.01 && Math.abs(f.y1 - s[1]) < 0.01
							&& Math.abs(f.x2 - s[0] - s[2]) < 0.01 && Math.abs(f.y2 - s[1] - s[3]) < 0.01;
				}
				if (o instanceof RecCanvas.Text) offScreen.add(tag + ": Text außerhalb des Handys: " + o);
				continue;
			}
			content++;
			if (o instanceof RecCanvas.Fill) {
				RecCanvas.Fill f = (RecCanvas.Fill) o;
				if (!(f.x1 >= x1 && f.y1 >= y1 && f.x2 <= x2 && f.y2 <= y2)) offScreen.add(tag + ": Fläche ragt über den Bildschirm: " + f);
			} else if (o instanceof RecCanvas.Text) {
				RecCanvas.Text t = (RecCanvas.Text) o;
				assertTrue((t.argb >>> 24) == 0xFF, tag + ": Text ohne volle Deckkraft: " + t);
				assertEquals(Math.rint(t.scale), t.scale, 1e-6, tag + ": Schrift nicht in ganzen Fensterpixeln: " + t);
				if (!(t.x >= x1 && t.y >= y1 && t.x + t.w <= x2 && t.y + t.h <= y2)) {
					offScreen.add(tag + ": Text ragt über den Bildschirm " + s[0] + "," + s[1] + " " + s[2] + "x" + s[3] + ": " + t);
				}
			} else {
				RecCanvas.Img i = (RecCanvas.Img) o;
				if (!(i.minX() >= x1 - 0.5 && i.minY() >= y1 - 0.5 && i.maxX() <= x2 + 0.5 && i.maxY() <= y2 + 0.5)) {
					offScreen.add(tag + ": Bild ragt über den Bildschirm: " + i);
				}
			}
		}
		assertTrue(inside, tag + ": der Bildschirm wurde nicht gezeichnet");
		assertTrue(content > 0, tag + ": der Bildschirm ist leer");
	}

	@Test
	void drawsWithoutTexturesToo() {
		post("Hallo", host.name, 1, false);
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = new RecCanvas(host.gui);
		c.canImages = false;
		ui.testOpenFully();
		ui.render(c, host.w / host.gui, host.h / host.gui, -1, -1);
		assertEquals(0, c.depth());
		assertTrue(c.images.isEmpty(), "ohne Texturen keine Bilder");
		assertEquals(0, ui.testArmBlits());
		assertEquals(0, store.uploads, "ohne Texturen wird nichts hochgeladen");
		assertTrue(c.shows("Hallo"));
		// Schleier aus Rahmen: der erste deckt das ganze Fenster.
		RecCanvas.Fill veil = c.fills.get(0);
		assertEquals(host.w, veil.x2 - veil.x1, 1e-3);
		assertEquals(host.h, veil.y2 - veil.y1, 1e-3);
	}

	// --- Arme ---

	private int expectedArmBlits(PhoneLayout l, boolean slim, boolean sleeveLeft, boolean sleeveRight) {
		int perRow = ArmRig.stripsPerRow(l.k);
		int n = 0;
		for (int arm = 0; arm < 2; arm++) {
			boolean left = arm == 0;
			ArmRig rig = ArmRig.of(l, left);
			int layers = (left ? sleeveLeft : sleeveRight) ? 2 : 1;
			for (ArmRig.Face face : ArmRig.Face.values()) {
				n += rig.strips(face, perRow).size() * ArmSkin.spans(face, slim).length * layers;
			}
		}
		return n;
	}

	/** Die Blits der Vorderseite eines Arms (Grundschicht), Streifen für Streifen. */
	private static List<RecCanvas.Img> front(RecCanvas c, boolean left, boolean overlay, boolean slim) {
		int[] uv = ArmSkin.uv(left, ArmRig.Face.FRONT, overlay, slim);
		List<RecCanvas.Img> out = new ArrayList<RecCanvas.Img>();
		for (RecCanvas.Img i : c.imagesOf("skin")) {
			if (i.h == 1 && i.u >= uv[0] && i.u < uv[0] + uv[2] && i.v >= uv[1] && i.v < uv[1] + uv[3]
					&& (i.argb & 0xFFFFFF) == (ArmSkin.TINT_FRONT & 0xFFFFFF) && i.u + i.w <= uv[0] + uv[2]) {
				out.add(i);
			}
		}
		return out;
	}

	private static List<RecCanvas.Img> inner(RecCanvas c, boolean left, boolean overlay, boolean slim) {
		int[] uv = ArmSkin.uv(left, ArmRig.Face.INNER, overlay, slim);
		List<RecCanvas.Img> out = new ArrayList<RecCanvas.Img>();
		for (RecCanvas.Img i : c.imagesOf("skin")) {
			if (i.h == 1 && i.u >= uv[0] && i.u < uv[0] + uv[2] && i.v >= uv[1] && i.v < uv[1] + uv[3]
					&& (i.argb & 0xFFFFFF) == (ArmSkin.TINT_INNER & 0xFFFFFF)) {
				out.add(i);
			}
		}
		return out;
	}

	@Test
	void wideSkinDrawsOneBlitPerFrontStripAndBothLayers() {
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = ui(hold);
			RecCanvas c = frame(ui);
			PhoneLayout l = PhoneLayout.of(host.w, host.h, hold);
			assertEquals(expectedArmBlits(l, false, true, true), ui.testArmBlits(), hold.toString());
			int strips = ArmRig.of(l, true).strips(ArmRig.Face.FRONT, ArmRig.stripsPerRow(l.k)).size();
			for (int arm = 0; arm < 2; arm++) {
				boolean left = arm == 0;
				List<RecCanvas.Img> base = front(c, left, false, false);
				assertEquals(strips, base.size(), hold + " Grundschicht " + (left ? "links" : "rechts"));
				assertEquals(strips, front(c, left, true, false).size(), hold + " Ärmel " + (left ? "links" : "rechts"));
				for (RecCanvas.Img i : base) {
					assertEquals(4, i.w, "breiter Skin: vier Texel je Streifen");
					assertEquals(0xFFFFFFFF, i.argb);
				}
			}
			ui.dispose();
		}
	}

	@Test
	void slimSkinKeepsTheArmFourPixelsWideByDoublingTheMiddleColumn() {
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi wide = ui(hold);
			RecCanvas cw = frame(wide);
			PhoneUi ui = ui(hold);
			ui.testForceSlim(Boolean.TRUE);
			RecCanvas c = frame(ui);
			PhoneLayout l = PhoneLayout.of(host.w, host.h, hold);
			assertEquals(expectedArmBlits(l, true, true, true), ui.testArmBlits(), hold.toString());
			for (int arm = 0; arm < 2; arm++) {
				boolean left = arm == 0;
				List<RecCanvas.Img> whole = front(cw, left, false, false);
				List<RecCanvas.Img> parts = front(c, left, false, true);
				assertEquals(3 * whole.size(), parts.size(), hold + ": drei Blits je Streifen der Vorderseite");
				int[] uv = ArmSkin.uv(left, ArmRig.Face.FRONT, false, true);
				for (int s = 0; s < whole.size(); s++) {
					RecCanvas.Img full = whole.get(s);
					RecCanvas.Img a = parts.get(3 * s), b = parts.get(3 * s + 1), d = parts.get(3 * s + 2);
					String tag = hold + (left ? " links" : " rechts") + " Streifen " + s;
					// Spalten 0, 1, 2 – je ein Texel.
					assertEquals(uv[0], a.u, 1e-6, tag);
					assertEquals(uv[0] + 1, b.u, 1e-6, tag);
					assertEquals(uv[0] + 2, d.u, 1e-6, tag);
					assertEquals(1, a.w, tag);
					assertEquals(1, b.w, tag);
					assertEquals(1, d.w, tag);
					// Breiten 1u, 2u, 1u (u = ein Viertel der Armbreite); die hinteren überlappen den Vorgänger um < 1 px.
					double u = full.across() / 4.0;
					assertEquals(u, a.across(), 0.01, tag);
					assertEquals(2 * u, b.across(), ArmRig.OVERLAP_PX + 0.01, tag);
					assertEquals(u, d.across(), ArmRig.OVERLAP_PX + 0.01, tag);
					assertTrue(b.across() >= 2 * u - 0.01 && d.across() >= u - 0.01, tag + ": keine Lücke");
					// Zusammen genau so breit wie der Arm des breiten Skins: gleiche Außenkanten.
					assertEquals(full.minX(), Math.min(a.minX(), d.minX()), 0.02, tag);
					assertEquals(full.maxX(), Math.max(a.maxX(), d.maxX()), 0.02, tag);
				}
			}
			wide.dispose();
			ui.dispose();
		}
	}

	@Test
	void innerFaceIsOneDarkerColourPerRow() {
		PhoneUi ui = ui(PhoneHold.PORTRAIT);
		RecCanvas c = frame(ui);
		PhoneLayout l = PhoneLayout.of(host.w, host.h, PhoneHold.PORTRAIT);
		int strips = ArmRig.of(l, true).strips(ArmRig.Face.INNER, ArmRig.stripsPerRow(l.k)).size();
		for (int arm = 0; arm < 2; arm++) {
			boolean left = arm == 0;
			List<RecCanvas.Img> blits = inner(c, left, false, false);
			assertEquals(4 * strips, blits.size(), "vier Blits je Streifen der Innenseite");
			int[] uv = ArmSkin.uv(left, ArmRig.Face.INNER, false, false);
			int[] alphas = {255, 128, 85, 64};
			for (int s = 0; s < strips; s++) {
				RecCanvas.Img firstOfStrip = blits.get(4 * s);
				for (int k = 0; k < 4; k++) {
					RecCanvas.Img i = blits.get(4 * s + k);
					// Jedes der vier Texel der Zeile über die GANZE Breite, mit Deckkraft 1, 1/2, 1/3, 1/4 = ihr Mittel.
					assertEquals(uv[0] + k, i.u, 1e-6);
					assertEquals(1, i.w);
					assertEquals(firstOfStrip.v, i.v, 1e-6);
					assertEquals(alphas[k], i.argb >>> 24, "Deckkraft Blit " + k);
					assertEquals(0xC7C7C7, i.argb & 0xFFFFFF, "auf 78 % abgedunkelt");
					assertEquals(firstOfStrip.across(), i.across(), 1e-6);
					assertEquals(firstOfStrip.minX(), i.minX(), 1e-6);
				}
			}
		}
		// Die Vorderseite liegt über der Innenseite: je Arm kommt erst die ganze Innenseite.
		List<RecCanvas.Img> skin = c.imagesOf("skin");
		int half = skin.size() / 2;
		assertEquals(0xC7C7C7, skin.get(0).argb & 0xFFFFFF);
		assertEquals(0xFFFFFF, skin.get(half - 1).argb & 0xFFFFFF);
		assertEquals(0xC7C7C7, skin.get(half).argb & 0xFFFFFF);
		ui.dispose();
	}

	@Test
	void armBlitsReadInsideTheirTexelsAndTheInnerFaceReachesUnderTheFront() {
		for (boolean slim : new boolean[]{false, true}) {
			PhoneUi ui = ui(PhoneHold.LANDSCAPE);
			ui.testForceSlim(Boolean.valueOf(slim));
			RecCanvas c = frame(ui);
			int arms = 0;
			for (RecCanvas.Img i : c.imagesOf("skin")) {
				if (i.texture.width == 64) continue; // Kopf im Feed
				arms++;
				assertEquals(64 * ArmDraw.SUB, i.texture.width);
				assertEquals(64 * ArmDraw.SUB, i.texture.height);
				// Ringsum ein Stück innerhalb der Texel: kein Pixel an der Kante trifft die Nachbarzeile oder -spalte.
				assertEquals(ArmDraw.INSET_U, Math.round(i.rawU) % ArmDraw.SUB, "quer");
				assertEquals(ArmDraw.INSET_V, Math.round(i.rawV) % ArmDraw.SUB, "längs");
				assertEquals(i.w * ArmDraw.SUB - 2 * ArmDraw.INSET_U, i.rawW);
				assertEquals(ArmDraw.SUB - 2 * ArmDraw.INSET_V, i.rawH);
				assertEquals(1, i.h, "eine Texel-Zeile je Streifen");
			}
			assertEquals(ui.testArmBlits(), arms);
			// Innenseite: an der Kante zur Vorderseite so viel breiter als die Fläche des Arms (damit kein Loch bleibt).
			PhoneLayout l = PhoneLayout.of(host.w, host.h, PhoneHold.LANDSCAPE);
			for (int arm = 0; arm < 2; arm++) {
				boolean left = arm == 0;
				List<ArmRig.Strip> strips = ArmRig.of(l, left).strips(ArmRig.Face.INNER, ArmRig.stripsPerRow(l.k));
				List<RecCanvas.Img> blits = inner(c, left, false, slim);
				assertEquals(4 * strips.size(), blits.size());
				for (int k = 0; k < strips.size(); k++) {
					ArmRig.Strip strip = strips.get(k);
					double face = Math.hypot(strip.a, strip.b), along = Math.hypot(strip.c, strip.d);
					// Senkrecht zur gemeinsamen Kante gemessen (die Fläche liegt schräg): so weit unter der Vorderseite.
					double sin = Math.abs(strip.a * (double) strip.d - strip.b * (double) strip.c) / (face * along);
					double reach = (blits.get(4 * k).across() - face) * sin;
					assertTrue(blits.get(4 * k).across() <= 1.5 * face + 0.01, "höchstens die halbe Fläche breiter");
					if (blits.get(4 * k).across() < 1.5 * face - 0.01) {
						assertEquals(ArmDraw.EDGE_OVERLAP_PX, reach, 0.01, "Streifen " + k);
					}
					// Die äußere Kante (weg von der Vorderseite) bleibt, wo sie war.
					RecCanvas.Img i = blits.get(4 * k);
					double[] outer = left ? i.corner(0) : i.corner(1);
					assertEquals(left ? strip.e : strip.e + strip.a, outer[0], 0.01, "Streifen " + k);
				}
			}
			ui.dispose();
		}
	}

	@Test
	void sleeveLayerFollowsTheSkinSettingsPerArm() {
		host.sleeveLeft = false;
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		PhoneLayout l = PhoneLayout.of(host.w, host.h, PhoneHold.LANDSCAPE);
		assertEquals(expectedArmBlits(l, false, false, true), ui.testArmBlits());
		assertTrue(front(c, true, true, false).isEmpty(), "linker Ärmel ist aus");
		assertTrue(inner(c, true, true, false).isEmpty(), "linker Ärmel ist aus");
		assertFalse(front(c, false, true, false).isEmpty(), "rechter Ärmel ist an");
		assertFalse(front(c, true, false, false).isEmpty(), "die Grundschicht bleibt");
		ui.dispose();

		host.sleeveLeft = true;
		host.sleeveRight = false;
		ui = ui(PhoneHold.LANDSCAPE);
		c = frame(ui);
		assertFalse(front(c, true, true, false).isEmpty());
		assertTrue(front(c, false, true, false).isEmpty());
		ui.dispose();
	}

	@Test
	void armsAlwaysRunPastTheBottomWindowEdge() {
		int[][] windows = {{1920, 1080}, {854, 480}, {1280, 1024}, {800, 1000}, {400, 900}, {3440, 1440}, {320, 240}};
		for (int[] win : windows) {
			for (PhoneHold hold : PhoneHold.values()) {
				for (int gui : new int[]{1, 3}) {
					host.size(win[0], win[1], gui);
					PhoneUi ui = ui(hold);
					RecCanvas c = frame(ui);
					String tag = hold + " " + win[0] + "x" + win[1] + " GUI " + gui;
					for (int arm = 0; arm < 2; arm++) {
						double lowest = -1;
						for (RecCanvas.Img i : front(c, arm == 0, false, false)) lowest = Math.max(lowest, i.maxY());
						assertTrue(lowest >= win[1] + 1, tag + ": Arm endet bei y=" + lowest + ", das Fenster bei " + win[1]);
					}
					ui.dispose();
				}
			}
		}
	}

	@Test
	void noSkinMeansNoArmsButThePhoneStillWorks() {
		host.skin = null;
		post("Hallo", host.name, 1, false);
		PhoneUi ui = ui(PhoneHold.PORTRAIT);
		RecCanvas c = frame(ui);
		assertEquals(0, ui.testArmBlits());
		assertTrue(c.imagesOf("skin").isEmpty());
		assertTrue(c.shows("Hallo"));
		assertTrue(c.strings().contains("P"), "ohne Skin: Anfangsbuchstabe statt Kopf: " + c.strings());
	}

	// --- Feed ---

	@Test
	void emptyFeedExplainsItselfWithTheRealKeys() {
		modules.reels.rotateKey.set("key.keyboard.x");
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		assertTrue(c.shows(I18n.tr("reels.empty")), "Texte: " + c.strings());
		StringBuilder all = new StringBuilder();
		for (String s : c.strings()) all.append(s).append(' ');
		assertTrue(all.toString().contains("X:"), "die belegte Dreh-Taste steht im Hinweis: " + all);
		assertTrue(all.toString().contains("K:"), "die Handy-Taste steht im Hinweis: " + all);
		assertNull(ui.testSpot("heart"), "ohne Post kein Herz");
		assertNotNull(ui.testSpot("rotate"));
		assertNotNull(ui.testSpot("camera"));
	}

	@Test
	void feedShowsPostAndPagesWithWheelAndArrows() {
		post("Drittes", host.name, 50, false);
		post("Zweites", "Rotkaeppchen", 26, false);
		post("Schaut mal!", host.name, 2, false);
		PhoneUi ui = ui(PhoneHold.PORTRAIT);
		RecCanvas c = frame(ui);
		assertEquals(0, ui.testPage());
		assertTrue(c.shows("Schaut mal!"), "Texte: " + c.strings());
		assertTrue(c.shows("1 / 3"));
		assertTrue(c.shows(host.name));
		assertTrue(c.shows("2 h ago"), "Texte: " + c.strings());
		String clock = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(NOON));
		assertTrue(c.strings().contains(clock), "hochkant steht die Uhr oben (" + clock + "): " + c.strings());
		assertTrue(c.shows(I18n.tr("reels.tab")));
		// Eigener Post: Kopf aus dem Skin (Gesicht + Hut-Schicht).
		int heads = 0;
		for (RecCanvas.Img i : c.imagesOf("skin")) if (i.w == 8 && i.h == 8) heads++;
		assertEquals(2, heads);

		assertTrue(ui.inputScroll(0, 0, -1), "das Mausrad gehört dem Handy");
		assertEquals(1, ui.testPage(), "Rad nach unten: nächstes Foto");
		c = frame(ui);
		assertTrue(c.shows("Zweites"));
		assertTrue(c.shows("2 / 3"));
		assertTrue(c.shows("1 d ago"), "Texte: " + c.strings());
		assertTrue(c.shows("Rotkaeppchen"));
		ui.inputScroll(0, 0, -1);
		ui.inputScroll(0, 0, -1);
		assertEquals(2, ui.testPage(), "am Ende ist Schluss");
		ui.inputScroll(0, 0, 1);
		assertEquals(1, ui.testPage());
		assertTrue(ui.inputKey(0, UiKey.UP, false));
		assertEquals(0, ui.testPage());
		assertTrue(ui.inputKey(0, UiKey.DOWN, false));
		assertEquals(1, ui.testPage());
		ui.dispose();

		// Quer: keine Uhr, keine Leiste, Kamera und Drehen in der rechten Spalte.
		ui = ui(PhoneHold.LANDSCAPE);
		c = frame(ui);
		assertFalse(c.strings().contains(clock), "quer steht keine Uhr da");
		assertNull(ui.testSpot("home"));
		assertNotNull(ui.testSpot("camera"));
		assertNotNull(ui.testSpot("rotate"));
		assertNotNull(ui.testSpot("heart"));
		ui.dispose();
	}

	@Test
	void metaLineStaysReadableAndPutsItsSecondPartOnALineOfItsOwnWhenItDoesNotFit() {
		// Hochkant bei 1920×1080: „2 h ago · with the phone in the game“ passt in seiner Größe nicht in die Spalte.
		post("Schaut mal!", host.name, 2, false);
		PhoneUi ui = ui(PhoneHold.PORTRAIT);
		RecCanvas c = frame(ui);
		RecCanvas.Text meta = null;
		for (RecCanvas.Text t : c.texts) if (t.text.contains("2 h ago")) meta = t;
		assertNotNull(meta, "Texte: " + c.strings());
		assertEquals("2 h ago", meta.text, "lieber lesbar als klein: die Zeit steht allein in der ersten Zeile");
		assertEquals(PhoneText.scale(21, PhoneLayout.of(host.w, host.h, PhoneHold.PORTRAIT).q), meta.scale, 1e-6, "volle Größe");
		assertTrue(meta.scale >= 2, "bei 1080p mindestens doppelt so groß wie die Spielschrift");
		// Der zweite Teil fällt nicht weg: er steht linksbündig darunter, in derselben Größe (hier auf zwei Zeilen).
		StringBuilder below = new StringBuilder();
		double bottom = meta.y + meta.h;
		for (RecCanvas.Text t : c.texts) {
			if (t.y < meta.y + meta.h || Math.abs(t.x - meta.x) > 0.01) continue;
			assertEquals(meta.scale, t.scale, 1e-6, "„" + t.text + "“ in derselben Größe");
			assertTrue(t.y >= bottom, "„" + t.text + "“ unter der Zeile davor");
			bottom = t.y + t.h;
			if (below.length() > 0) below.append(' ');
			below.append(t.text);
		}
		assertEquals(I18n.tr("reels.meta.phone"), below.toString(), "Texte: " + c.strings());
		int[] home = ui.testSpot("home");
		assertTrue(bottom <= home[1], "über der Leiste unten");
		ui.dispose();

		// Ein kurzer Zusatz (Selfie) passt und bleibt.
		long at = NOON - 3600000L;
		assertNotNull(backend.post(ReelsPost.newId(at, UTC, ++salt) + ".png", 608, 1080, true, true, "Ich", host.name, at));
		ui = ui(PhoneHold.PORTRAIT);
		c = frame(ui);
		assertTrue(c.strings().contains("1 h ago · " + I18n.tr("reels.meta.selfie")), "Texte: " + c.strings());
		ui.dispose();
	}

	@Test
	void streakShowsInTheHeaderAndBesideTheOwnName() {
		post("Gestern", host.name, 26, false);
		post("Heute", host.name, 2, false);
		assertEquals(2, backend.streak(NOON));
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		assertNotNull(ui.testSpot("streak"));
		assertNotNull(ui.testSpot("streakChip"), "eigener Post: Streak-Marke hinter dem Namen");
		int twos = 0;
		for (String s : c.strings()) if (s.equals("2")) twos++;
		assertEquals(2, twos, "Streak oben und hinter dem Namen: " + c.strings());
		ui.dispose();
	}

	@Test
	void heartLikesAndUnlikesAtEveryGuiScale() {
		ReelsPost p = post("Schaut mal!", host.name, 2, false);
		for (int gui : new int[]{1, 2, 4}) {
			host.size(1920, 1080, gui);
			for (PhoneHold hold : PhoneHold.values()) {
				PhoneUi ui = ui(hold);
				frame(ui);
				int clicks = host.clicks;
				click(ui, "heart");
				ReelsPost now = backend.feed().get(0);
				assertEquals(p.id, now.id);
				assertTrue(now.likedByMe, "GUI " + gui + " " + hold);
				assertEquals(1, now.likes);
				assertEquals(clicks + 1, host.clicks, "Klick-Ton");
				RecCanvas c = frame(ui);
				assertTrue(c.shows("1"));
				click(ui, "heart");
				assertFalse(backend.feed().get(0).likedByMe);
				assertEquals(0, backend.feed().get(0).likes);
				ui.dispose();
			}
		}
		assertTrue(Files.isRegularFile(tmp.resolve("feed.json")), "nach dem Like gleich gesichert");
	}

	@Test
	void photoFillsTheScreenWhenItsOrientationMatchesTheHold() throws IOException {
		postWithPhoto(160, 90, 1);
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		frame(ui);
		RecCanvas c = frame(ui);
		c = frame(ui);
		String id = photoTexture(c);
		List<RecCanvas.Img> imgs = c.imagesOf(id);
		assertEquals(1, imgs.size(), "passt die Ausrichtung, füllt das Foto den Bildschirm – ein Blit");
		int[] s = ui.testSpot("screen");
		RecCanvas.Img i = imgs.get(0);
		assertEquals(0xFFFFFFFF, i.argb);
		assertEquals(s[0], i.minX(), 0.01);
		assertEquals(s[1], i.minY(), 0.01);
		assertEquals(s[0] + s[2], i.maxX(), 0.01);
		assertEquals(s[1] + s[3], i.maxY(), 0.01);
		// Mittig beschnitten über den Texturausschnitt, nicht verzerrt.
		assertEquals(s[2] / (double) s[3], i.w / (double) i.h, 0.03);
		assertEquals((160 - i.w) / 2f, i.u, 0.51f);
		assertEquals((90 - i.h) / 2f, i.v, 0.51f);
		ui.dispose();
	}

	@Test
	void photoIsShownWholeWhenItsOrientationDiffersFromTheHold() throws IOException {
		postWithPhoto(90, 160, 1);
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = ui(hold);
			frame(ui);
			frame(ui);
			RecCanvas c = frame(ui);
			List<RecCanvas.Img> imgs = c.imagesOf(photoTexture(c));
			int[] s = ui.testSpot("screen");
			if (hold == PhoneHold.PORTRAIT) {
				assertEquals(1, imgs.size(), "Hochkant-Foto, hochkant gehalten: füllt den Bildschirm");
				ui.dispose();
				continue;
			}
			// Hochkant-Foto, quer gehalten: erst abgedunkelt als Füllung, dann das GANZE Foto mittig eingepasst.
			assertEquals(2, imgs.size(), "Füllung + eingepasstes Foto");
			RecCanvas.Img fill = imgs.get(0), whole = imgs.get(1);
			assertEquals(FeedView.DIMMED, fill.argb);
			assertEquals(s[0], fill.minX(), 0.01);
			assertEquals(s[0] + s[2], fill.maxX(), 0.01);
			assertEquals(0xFFFFFFFF, whole.argb);
			assertEquals(0f, whole.u, 1e-6);
			assertEquals(0f, whole.v, 1e-6);
			assertEquals(90, whole.w, "das ganze Foto");
			assertEquals(160, whole.h, "das ganze Foto");
			assertEquals(s[1], whole.minY(), 0.01, "so hoch wie der Bildschirm");
			assertEquals(s[1] + s[3], whole.maxY(), 0.01);
			double w = whole.maxX() - whole.minX();
			assertEquals(s[3] * 90.0 / 160.0, w, 1.0, "nicht verzerrt");
			assertEquals(s[0] + s[2] / 2.0, (whole.minX() + whole.maxX()) / 2.0, 1.0, "mittig");
			ui.dispose();
		}
		assertEquals(4, FeedView.containRect(1440, 710, 0, 0, 384, 806).length);
		int[] r = FeedView.containRect(1440, 710, 10, 20, 384, 806);
		assertEquals(10, r[0]);
		assertEquals(384, r[2]);
		assertEquals(189, r[3]);
		assertEquals(20 + (806 - 189) / 2, r[1]);
	}

	/** Die hochgeladene Foto-Textur (die einzige außer Skin und Schleier). */
	private static String photoTexture(RecCanvas c) {
		for (RecCanvas.Img i : c.images) {
			String id = String.valueOf(i.texture.id);
			if (id.startsWith("trs_thumbs/")) return id;
		}
		throw new AssertionError("kein Foto gezeichnet: " + c.images.size() + " Bilder");
	}

	// --- Kommentare ---

	@Test
	void commentsOpenWithEnterTypeSendAndCloseWithEscape() {
		ReelsPost p = post("Schaut mal!", "Rotkaeppchen", 2, false);
		reels.comment(p.id, "Rotkaeppchen", "Erster", NOON - 5000);
		PhoneUi ui = ui(PhoneHold.PORTRAIT);
		RecCanvas c = frame(ui);
		assertFalse(c.shows(I18n.tr("reels.comments")));
		assertFalse(ui.inputChar('x'), "im Feed tippt man nicht");

		assertTrue(ui.inputKey(0, UiKey.ENTER, false));
		assertEquals(PhoneUi.View.COMMENTS, ui.testView());
		c = frame(ui);
		assertTrue(c.shows(I18n.tr("reels.comments")), "Texte: " + c.strings());
		assertTrue(c.shows("Erster"));
		assertTrue(c.shows(I18n.tr("reels.comment.hint")), "leeres Feld zeigt den Hinweis");
		assertNotNull(ui.testSpot("commentInput"));
		assertEquals(1, ui.commentsShown);
		assertNotNull(ui.testSpot("commentRow0"));
		assertEquals("NEW", ui.testCommentMode());

		// Buchstaben sind im Textfeld nie Kurztasten: R dreht nicht, K steckt nicht weg.
		assertTrue(ui.inputKey('R', UiKey.NONE, false));
		assertTrue(ui.inputKey('K', UiKey.NONE, false));
		assertEquals(PhoneHold.PORTRAIT, ui.testHold());
		assertFalse(ui.isClosing());
		for (char ch : "Hrr süß".toCharArray()) assertTrue(ui.inputChar(ch));
		ui.inputKey(0, UiKey.BACKSPACE, false);
		ui.inputChar('s');
		host.clipboard = "!\nja";
		ui.inputKey(0, UiKey.PASTE, false);
		c = frame(ui);
		assertTrue(c.shows("Hrr süs! ja"), "Texte: " + c.strings());
		// Blättern geht mit offenem Blatt nicht.
		ui.inputScroll(0, 0, -1);
		assertEquals(0, ui.testPage());

		assertTrue(ui.inputKey(0, UiKey.ENTER, false));
		ReelsPost now = backend.feed().get(0);
		assertEquals(2, now.comments.size());
		assertEquals("Hrr süs! ja", now.comments.get(1).text);
		assertEquals(host.name, now.comments.get(1).author);
		assertEquals(NOON, now.comments.get(1).at);
		assertEquals(PhoneUi.View.COMMENTS, ui.testView(), "das Blatt bleibt offen");
		c = frame(ui);
		assertTrue(c.shows("Hrr süs! ja"));
		assertEquals(2, ui.commentsShown);
		assertEquals("", ui.testCommentText(), "das Feld ist wieder leer");
		// Der neue steht unten, direkt über dem Feld; der ältere darüber.
		assertTrue(spot(ui, "commentRow1")[1] > spot(ui, "commentRow0")[1]);
		assertTrue(spot(ui, "commentRow1")[1] + spot(ui, "commentRow1")[3] <= spot(ui, "commentInput")[1]);
		// Leerer Text wird nicht gesendet.
		ui.inputKey(0, UiKey.ENTER, false);
		assertEquals(2, backend.feed().get(0).comments.size());

		assertTrue(ui.inputKey(0, UiKey.ESCAPE, false), "Esc schließt nur das Blatt");
		assertEquals(PhoneUi.View.FEED, ui.testView());
		assertFalse(ui.isClosing());
		assertFalse(ui.inputKey(0, UiKey.ESCAPE, false), "Esc im Feed gehört dem Bildschirm der Version (schließt)");
		ui.dispose();
	}

	@Test
	void commentSheetOpensAndClosesByClick() {
		ReelsPost p = post("Schaut mal!", host.name, 2, false);
		for (int n = 0; n < 7; n++) reels.comment(p.id, "Gast" + n, "Kommentar " + n, NOON - 7000 + n);
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = ui(hold);
			frame(ui);
			click(ui, "comment");
			assertEquals(PhoneUi.View.COMMENTS, ui.testView(), hold.toString());
			RecCanvas c = frame(ui);
			// Einzeiler samt Link-Zeile: quer passen fünf, hochkant sechs – eine Zeile, die nicht ganz passt, fällt weg.
			assertEquals(hold == PhoneHold.LANDSCAPE ? 5 : 6, ui.commentsShown, hold.toString());
			assertTrue(c.shows("Kommentar 6"), "der neueste steht da: " + c.strings());
			assertFalse(c.shows("Kommentar 0"), "nur die neuesten – die älteren kommen beim Blättern");
			int[] sheetNow = spot(ui, "commentSheet");
			for (int i = 7 - ui.commentsShown; i < 7; i++) {
				assertTrue(inside(spot(ui, "commentRow" + i), sheetNow), hold + " Zeile " + i + " liegt im Blatt");
			}
			assertNull(ui.testSpot("commentRow" + (6 - ui.commentsShown)), "was nicht gezeichnet ist, hat keine Stelle");
			// Ein Klick auf das Blatt selbst trifft nicht das Herz darunter.
			int[] sheet = ui.testSpot("commentSheet");
			assertNotNull(sheet);
			ui.inputClick((sheet[0] + 2.0) / host.gui, (sheet[1] + sheet[3] / 2.0) / host.gui, 0);
			assertFalse(backend.feed().get(0).likedByMe);
			assertEquals(PhoneUi.View.COMMENTS, ui.testView());
			click(ui, "commentsClose");
			assertEquals(PhoneUi.View.FEED, ui.testView());
			frame(ui);
			click(ui, "comment");
			frame(ui);
			click(ui, "commentsBack");
			assertEquals(PhoneUi.View.FEED, ui.testView(), "Klick aufs Foto über dem Blatt schließt es");
			ui.dispose();
		}
	}

	@Test
	void noCommentsOnAnEmptyFeed() {
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		frame(ui);
		ui.inputKey(0, UiKey.ENTER, false);
		assertEquals(PhoneUi.View.FEED, ui.testView());
	}

	// --- Drehen, Schließen, Aufräumen ---

	@Test
	void rotateKeyAndButtonSwitchTheHoldAndRememberIt() {
		post("Schaut mal!", host.name, 2, false);
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		int[] land = ui.testSpot("phone");
		assertTrue(land[2] > land[3], "quer: breiter als hoch");
		assertTrue(ui.inputKey('R', UiKey.NONE, false));
		assertEquals(PhoneHold.PORTRAIT, ui.testHold());
		assertEquals(PhoneHold.PORTRAIT, modules.reels.hold.get(), "die Haltung wird gemerkt");
		c = frame(ui);
		int[] port = ui.testSpot("phone");
		assertTrue(port[3] > port[2], "hochkant: höher als breit");
		assertTrue(c.shows(I18n.tr("reels.nav.rotate")), "hochkant: Leiste mit „Drehen“: " + c.strings());
		assertTrue(c.shows(I18n.tr("reels.nav.feed")));
		click(ui, "rotate");
		assertEquals(PhoneHold.LANDSCAPE, ui.testHold());
		assertEquals(PhoneHold.LANDSCAPE, modules.reels.hold.get());
		frame(ui);
		click(ui, "rotate");
		assertEquals(PhoneHold.PORTRAIT, ui.testHold());
		// Ein neues Handy startet in der gemerkten Haltung.
		assertEquals(PhoneHold.PORTRAIT, new PhoneUi(reels, host).testHold());
		// Eine andere belegte Taste gilt sofort.
		modules.reels.rotateKey.set("key.keyboard.x");
		assertFalse(ui.inputKey('R', UiKey.NONE, false), "R ist nicht mehr belegt");
		assertTrue(ui.inputKey('X', UiKey.NONE, false));
		assertEquals(PhoneHold.LANDSCAPE, ui.testHold());
		ui.dispose();
	}

	@Test
	void openKeyClosesOnlyAfterItWasReleasedOnce() throws InterruptedException {
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		// K ist beim Öffnen noch unten (Tastenwiederholung): das Handy bleibt draußen.
		host.down.add("key.keyboard.k");
		frame(ui);
		assertTrue(ui.inputKey('K', UiKey.NONE, false), "die Taste gehört trotzdem dem Handy");
		assertFalse(ui.isClosing(), "gehaltene Taste steckt das Handy nicht gleich wieder weg");
		frame(ui);
		ui.inputKey('K', UiKey.NONE, false);
		assertFalse(ui.isClosing());
		// Losgelassen, neu gedrückt: jetzt weg damit.
		host.down.clear();
		frame(ui);
		host.down.add("key.keyboard.k");
		assertTrue(ui.inputKey('K', UiKey.NONE, false));
		assertTrue(ui.isClosing());
		// Während es verschwindet, zählen keine Tasten und keine Klicks mehr.
		assertTrue(ui.inputKey('R', UiKey.NONE, false));
		assertEquals(PhoneHold.LANDSCAPE, ui.testHold());
		click(ui, "rotate");
		assertEquals(PhoneHold.LANDSCAPE, ui.testHold());
		ui.inputScroll(0, 0, -1);
		assertEquals(0, ui.testPage());

		// Nach der Animation: aufgeräumt, Bildschirm wirklich zu, die Taste ist bis zum Loslassen gesperrt.
		for (int i = 0; i < 400 && host.closes == 0; i++) {
			Thread.sleep(5);
			RecCanvas c = new RecCanvas(host.gui);
			ui.render(c, host.w / host.gui, host.h / host.gui, -1, -1);
			assertEquals(0, c.depth());
		}
		assertEquals(1, host.closes, "nach der Animation wird der Bildschirm geschlossen");
		assertTrue(ui.disposed());
		assertTrue(reels.keyGate().blocked());
		assertFalse(reels.keyGate().shouldOpen(true, true), "die noch gehaltene Taste öffnet nicht sofort wieder");
		assertFalse(reels.keyGate().shouldOpen(false, false));
		assertTrue(reels.keyGate().shouldOpen(true, true), "nach dem Loslassen wieder normal");
	}

	@Test
	void slideInMovesPhoneAndArmsDownOnlyAndFadesIn() {
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		PhoneLayout l = PhoneLayout.of(host.w, host.h, PhoneHold.LANDSCAPE);
		// Erstes Bild: fast noch zu.
		RecCanvas c = new RecCanvas(host.gui);
		ui.render(c, host.w / host.gui, host.h / host.gui, -1, -1);
		assertEquals(0, c.depth());
		RecCanvas.Fill body = null;
		for (RecCanvas.Fill f : c.fills) {
			if ((f.argb & 0xFFFFFF) == (PhoneBody.BODY & 0xFFFFFF) && (body == null || f.x2 - f.x1 > body.x2 - body.x1)) body = f;
		}
		assertNotNull(body);
		assertTrue((body.argb >>> 24) < 0xFF, "blendet ein");
		assertEquals(l.phoneX, body.x1, 1e-3, "seitlich steht es schon richtig");
		assertTrue(body.y1 > l.phoneY + 2 * l.px(PhoneBody.STEP), "kommt von unten: " + body.y1 + " statt " + l.phoneY);
		double lowest = -1;
		for (RecCanvas.Img i : c.imagesOf("skin")) lowest = Math.max(lowest, i.maxY());
		assertTrue(lowest > host.h, "auch beim Hereinkommen laufen die Arme unten aus dem Bild");
		ui.dispose();
	}

	@Test
	void disposeReleasesTexturesBlocksTheKeySavesAndIsIdempotent() throws IOException {
		postWithPhoto(160, 90, 3);
		ReelsPost p = postWithPhoto(90, 160, 1);
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		for (int i = 0; i < 4; i++) frame(ui);
		assertTrue(store.live.contains(PhoneVeil.TEXTURE), "Schleier: " + store.live);
		assertTrue(store.live.contains(PhoneShadow.TEXTURE), "Schatten: " + store.live);
		assertEquals(4, store.live.size(), "Schleier + Schatten + beide Fotos (das Nachbarfoto wird mitgeladen): " + store.live);
		backend.toggleLike(p.id);
		assertTrue(backend.dirty());
		assertFalse(reels.keyGate().blocked());

		ui.dispose();
		assertTrue(ui.disposed());
		assertTrue(store.live.isEmpty(), "alle Texturen frei: " + store.live);
		assertEquals(4, store.released.size());
		assertTrue(reels.keyGate().blocked(), "die Handy-Taste ist bis zum Loslassen gesperrt");
		assertFalse(backend.dirty(), "der Feed ist gesichert");
		assertTrue(new LocalReelsBackend(tmp, UTC).feed().get(0).likedByMe);

		// Noch einmal (Schließ-Animation UND removed() des Bildschirms): nichts passiert doppelt.
		reels.keyGate().shouldOpen(false, false);
		assertFalse(reels.keyGate().blocked());
		int uploads = store.uploads;
		ui.dispose();
		assertEquals(4, store.released.size());
		assertFalse(reels.keyGate().blocked(), "das zweite Aufräumen sperrt die Taste nicht noch einmal");
		// Ein verirrtes Bild nach dem Aufräumen lädt nichts mehr hoch.
		RecCanvas c = new RecCanvas(host.gui);
		ui.render(c, host.w / host.gui, host.h / host.gui, -1, -1);
		assertEquals(0, c.depth());
		assertEquals(uploads, store.uploads);
		assertTrue(store.live.isEmpty());
		assertEquals(0, host.closes, "dispose() schließt den Bildschirm nicht selbst");
	}

	@Test
	void veilIsSoftAndDarkerTowardsTheCorners() {
		assertEquals(PhoneVeil.ALPHA_CENTRE, PhoneVeil.alphaAt(PhoneVeil.CENTRE_X, PhoneVeil.CENTRE_Y), 1e-4);
		assertEquals(PhoneVeil.ALPHA_EDGE, PhoneVeil.alphaAt(0f, 0f), 1e-3);
		assertEquals(PhoneVeil.ALPHA_EDGE, PhoneVeil.alphaAt(1f, 0f), 1e-3);
		assertTrue(PhoneVeil.alphaAt(0f, 1f) < PhoneVeil.ALPHA_EDGE);
		int[] px = PhoneVeil.pixels(PhoneVeil.TEX_W, PhoneVeil.TEX_H);
		assertEquals(PhoneVeil.TEX_W * PhoneVeil.TEX_H, px.length);
		int min = 255, max = 0, step = 0;
		for (int y = 0; y < PhoneVeil.TEX_H; y++) {
			for (int x = 0; x < PhoneVeil.TEX_W; x++) {
				int p = px[y * PhoneVeil.TEX_W + x];
				assertEquals(PhoneVeil.RGB, p & 0xFFFFFF);
				int a = p >>> 24;
				min = Math.min(min, a);
				max = Math.max(max, a);
				if (x > 0) step = Math.max(step, Math.abs(a - (px[y * PhoneVeil.TEX_W + x - 1] >>> 24)));
				if (y > 0) step = Math.max(step, Math.abs(a - (px[(y - 1) * PhoneVeil.TEX_W + x] >>> 24)));
			}
		}
		assertEquals(Math.round(0.34f * 255), min, 1);
		assertEquals(Math.round(0.66f * 255), max, 1);
		assertTrue(step <= 1, "keine sichtbaren Bänder: größter Sprung " + step);

		// Mit Texturen: ein Bild über das ganze Fenster, vor allem anderen.
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		assertTrue(c.order.get(0) instanceof RecCanvas.Img);
		RecCanvas.Img veil = (RecCanvas.Img) c.order.get(0);
		assertEquals(PhoneVeil.TEXTURE, veil.texture.id);
		assertEquals(0, veil.minX(), 1e-3);
		assertEquals(0, veil.minY(), 1e-3);
		assertEquals(host.w, veil.maxX(), 1e-3);
		assertEquals(host.h, veil.maxY(), 1e-3);
		ui.dispose();

		// Ersatz ohne Texturen: an den Ecken zusammen etwa die Rand-Deckkraft.
		RecCanvas plain = new RecCanvas(1);
		PhoneVeil.bands(plain, 1920, 1080);
		double keep = 1;
		for (RecCanvas.Fill f : plain.fills) {
			if (f.x1 <= 0 && f.y1 <= 0) keep *= 1 - (f.argb >>> 24) / 255.0;
		}
		assertEquals(PhoneVeil.ALPHA_EDGE, 1 - keep, 0.06);
	}

	@Test
	void shadowIsSoftRoundAndFallsOnTheArms() {
		// Die Ecken-Textur: außen nichts, innen die volle Deckkraft, dazwischen ohne sichtbare Stufen.
		assertEquals(0f, PhoneShadow.profile(0f), 1e-6);
		assertEquals(0.5f, PhoneShadow.profile(0.5f), 1e-4);
		assertEquals(1f, PhoneShadow.profile(1f), 1e-6);
		int n = 190;
		int[] px = PhoneShadow.pixels(n);
		int full = Math.round(PhoneShadow.ALPHA * 255f);
		assertEquals(0, px[0] >>> 24, "außen an der Ecke");
		assertEquals(full, px[n * n - 1] >>> 24, "innen");
		int step = 0;
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				int p = px[y * n + x];
				assertEquals(0, p & 0xFFFFFF, "schwarz");
				assertEquals(p, px[x * n + y], "an der Diagonale spiegelgleich (wird gedreht wiederverwendet)");
				if (x > 0) {
					int d = (p >>> 24) - (px[y * n + x - 1] >>> 24);
					assertTrue(d >= 0, "wird nach innen nie heller");
					step = Math.max(step, d);
				}
			}
			assertEquals(Math.round(PhoneShadow.ALPHA * 255f * PhoneShadow.profile(y / (float) (n - 1))), px[y * n + n - 1] >>> 24,
					"letzte Spalte = reiner Kantenverlauf");
		}
		assertTrue(step <= 2, "keine sichtbaren Bänder: größter Sprung " + step);

		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = ui(hold);
			RecCanvas c = frame(ui);
			PhoneLayout l = PhoneLayout.of(host.w, host.h, hold);
			List<RecCanvas.Img> parts = c.imagesOf(PhoneShadow.TEXTURE);
			assertEquals(8, parts.size(), "vier Ecken + vier Kanten");
			int s = PhoneShadow.edge(l);
			double x0 = l.phoneX + Math.round(PhoneShadow.OFFSET_X * l.q) - s / 2, y0 = l.phoneY + Math.round(PhoneShadow.OFFSET_Y * l.q) - s / 2;
			double x1 = x0 + l.phoneW + s, y1 = y0 + l.phoneH + s;
			// Zusammen mit dem Rechteck in der Mitte decken die Teile genau die Schattenfläche – ohne Lücke, ohne Überlappung.
			RecCanvas.Fill mid = fillOf(c, full << 24, true);
			assertNotNull(mid, "Mitte des Schattens");
			double area = (mid.x2 - mid.x1) * (mid.y2 - mid.y1);
			double minX = mid.x1, minY = mid.y1, maxX = mid.x2, maxY = mid.y2;
			for (RecCanvas.Img i : parts) {
				assertEquals(0xFFFFFFFF, i.argb);
				area += (i.maxX() - i.minX()) * (i.maxY() - i.minY());
				minX = Math.min(minX, i.minX());
				minY = Math.min(minY, i.minY());
				maxX = Math.max(maxX, i.maxX());
				maxY = Math.max(maxY, i.maxY());
				// Ganze Fensterpixel: sonst bliebe zwischen den Teilen eine Haarlinie.
				for (double v : new double[]{i.minX(), i.minY(), i.maxX(), i.maxY()}) assertEquals(Math.rint(v), v, 0.01, hold + ": " + v);
			}
			assertEquals(x0, minX, 0.01, hold.toString());
			assertEquals(y0, minY, 0.01, hold.toString());
			assertEquals(x1, maxX, 0.01, hold.toString());
			assertEquals(y1, maxY, 0.01, hold.toString());
			assertEquals((x1 - x0) * (y1 - y0), area, 0.0005 * (x1 - x0) * (y1 - y0), hold + ": Fläche");
			assertEquals(x0 + s, mid.x1, 1e-3);
			assertEquals(y1 - s, mid.y2, 1e-3);
			// Nach den Armen (er fällt auf sie), vor dem Gehäuse.
			List<RecCanvas.Img> skin = c.imagesOf("skin");
			int lastArm = -1;
			for (int i = 0; i < c.order.size(); i++) {
				Object o = c.order.get(i);
				if (o instanceof RecCanvas.Img && ((RecCanvas.Img) o).w != 8 && skin.contains(o)) lastArm = i;
			}
			int first = c.order.indexOf(parts.get(0));
			assertTrue(lastArm >= 0 && lastArm < first, hold + ": Schatten nach den Armen");
			assertTrue(first < c.order.indexOf(fillOf(c, PhoneBody.BODY, true)), hold + ": Schatten vor dem Gehäuse");
			ui.dispose();
			assertFalse(store.live.contains(PhoneShadow.TEXTURE), "Schatten-Textur freigegeben");
		}

		// Ersatz ohne Texturen: Schichten, die in der Mitte zusammen dieselbe Deckkraft ergeben.
		PhoneLayout l = PhoneLayout.of(1920, 1080, PhoneHold.LANDSCAPE);
		RecCanvas plain = new RecCanvas(1);
		PhoneShadow.bands(plain, l);
		assertEquals(PhoneShadow.BAND_LAYERS, plain.fills.size());
		double keep = 1;
		for (RecCanvas.Fill f : plain.fills) keep *= 1 - (f.argb >>> 24) / 255.0;
		assertEquals(PhoneShadow.ALPHA, 1 - keep, 0.05);
	}

	// --- Bausteine ---

	@Test
	void textScaleFitClipWrapAndTail() {
		RecCanvas c = new RecCanvas(1);
		float q = 1.26f; // quer bei 1920×1080
		assertEquals(3, PhoneText.scale(32, q), "Titel");
		assertEquals(3, PhoneText.scale(29, q), "Name");
		assertEquals(3, PhoneText.scale(27, q), "Text");
		assertEquals(2, PhoneText.scale(22, q), "Seitenzahl");
		assertEquals(1, PhoneText.scale(22, 0.1f), "nie unter 1");
		assertEquals(60, PhoneText.width(c, "Hallo", 2));
		assertEquals(0, PhoneText.width(c, null, 2));

		// Passt: volle Größe. Passt nicht: erst eine Stufe kleiner, dann kürzen.
		assertEquals(3, PhoneText.fit(c, "Hallo", 3, 90));
		assertEquals(2, PhoneText.fit(c, "Hallo", 3, 89));
		assertEquals(2, PhoneText.fit(c, "Hallo", 3, 10), "höchstens eine Stufe");
		assertEquals(1, PhoneText.fit(c, "Hallo", 1, 10));
		assertEquals("Hallo", PhoneText.clip(c, "Hallo", 1, 30));
		assertEquals("Hal…", PhoneText.clip(c, "Hallo", 1, 29));
		assertEquals("Ha…", PhoneText.clip(c, "Ha lo", 1, 24), "kein Leerzeichen vor den Punkten");
		assertEquals("", PhoneText.clip(c, "Hallo", 1, 5));
		assertEquals("", PhoneText.clip(c, "Hallo", 2, 0));
		assertEquals(60, PhoneText.line(c, "Hallo", 10, 10, 3, 60, 0xFFFFFFFF, false));
		RecCanvas.Text t = c.texts.get(0);
		assertEquals(2.0, t.scale, 1e-6);
		assertEquals(10 + Math.round(7 * 3 / 2f - 7 * 2 / 2f), t.y, 1e-6, "kleiner, aber auf derselben Mittellinie");
		assertEquals(0, PhoneText.line(c, "Hallo", 0, 0, 1, 5, 0xFFFFFFFF, false));
		PhoneText.line(c, "Hallo", 0, 0, 1, 100, 0x00FFFFFF, false);
		assertEquals(1, c.texts.size(), "ohne Deckkraft gibt es keinen Text");

		List<String> lines = PhoneText.wrap(c, "eins zwei drei vier fünf sechs", 2, 120, 2);
		assertEquals(2, lines.size());
		assertEquals("eins zwei", lines.get(0));
		assertTrue(lines.get(1).endsWith("…"), "was nicht mehr passt, endet mit …: " + lines);
		for (String line : lines) assertTrue(PhoneText.width(c, line, 2) <= 120, line);
		for (String line : PhoneText.wrap(c, "Donaudampfschifffahrtsgesellschaftskapitän", 1, 60, 3)) {
			assertTrue(PhoneText.width(c, line, 1) <= 60, "ein zu langes Wort wird gekürzt: " + line);
		}
		assertTrue(PhoneText.wrap(c, "", 1, 60, 3).isEmpty());
		assertTrue(PhoneText.wrap(c, "Hallo", 1, 60, 0).isEmpty());

		assertEquals("Hallo", PhoneText.tail(c, "Hallo", 1, 30));
		assertEquals("llo", PhoneText.tail(c, "Hallo", 1, 20), "Eingabefelder zeigen den Schluss");
		assertEquals("", PhoneText.tail(c, "Hallo", 1, 0));
	}

	@Test
	void iconsAreRectangularBitmapsDrawnAsRuns() {
		String[][] all = {PhoneIcons.HEART, PhoneIcons.TALK, PhoneIcons.FLAME, PhoneIcons.CAM, PhoneIcons.HOME, PhoneIcons.FLIP,
				PhoneIcons.X};
		int[][] sizes = {{9, 8}, {9, 8}, {7, 9}, {11, 7}, {9, 8}, {9, 9}, {7, 7}};
		for (int n = 0; n < all.length; n++) {
			String[] rows = all[n];
			assertEquals(sizes[n][1], rows.length, "Symbol " + n);
			int on = 0;
			for (String row : rows) {
				assertEquals(sizes[n][0], row.length(), "Symbol " + n + ": alle Zeilen gleich lang");
				for (char ch : row.toCharArray()) {
					assertTrue(ch == '#' || ch == '.', "Symbol " + n);
					if (ch == '#') on++;
				}
			}
			RecCanvas c = new RecCanvas(1);
			PhoneIcons.draw(c, rows, 100, 50, 3, 0xFFFFFFFF, false);
			double area = 0;
			for (RecCanvas.Fill f : c.fills) {
				area += (f.x2 - f.x1) * (f.y2 - f.y1);
				assertTrue(f.x1 >= 100 && f.y1 >= 50 && f.x2 <= 100 + PhoneIcons.width(rows, 3) && f.y2 <= 50 + PhoneIcons.height(rows, 3));
			}
			assertEquals(on * 9.0, area, 1e-6, "Symbol " + n + ": jedes Pixel genau einmal");
			assertTrue(c.fills.size() <= on && (n == 6 || c.fills.size() < on), "Symbol " + n + ": Läufe statt einzelner Pixel");
			RecCanvas shadowed = new RecCanvas(1);
			PhoneIcons.draw(shadowed, rows, 0, 0, 4, 0xFFFFFFFF, true);
			assertEquals(2 * c.fills.size(), shadowed.fills.size(), "Schatten = dieselbe Form noch einmal");
			assertEquals(PhoneIcons.SHADOW, shadowed.fills.get(0).argb);
			assertEquals(2.0, shadowed.fills.get(0).y1, 1e-6, "Schatten um eine halbe Pixelgröße versetzt");
		}
	}

	@Test
	void steppedShapeHasTwoStepsPerCornerAndNeverOverlaps() {
		RecCanvas c = new RecCanvas(1);
		PhoneBody.stepped(c, 10, 20, 200, 100, 8, 0x80FFFFFF);
		assertEquals(5, c.fills.size());
		double area = 0;
		for (int a = 0; a < c.fills.size(); a++) {
			RecCanvas.Fill f = c.fills.get(a);
			area += (f.x2 - f.x1) * (f.y2 - f.y1);
			assertTrue(f.x1 >= 10 && f.y1 >= 20 && f.x2 <= 210 && f.y2 <= 120);
			for (int b = a + 1; b < c.fills.size(); b++) {
				RecCanvas.Fill g = c.fills.get(b);
				boolean apart = f.x2 <= g.x1 || g.x2 <= f.x1 || f.y2 <= g.y1 || g.y2 <= f.y1;
				assertTrue(apart, "überlappt (halb durchsichtig würde es dort doppelt decken): " + f + " / " + g);
			}
		}
		// Je Ecke fehlen drei Stufen-Quadrate.
		assertEquals(200 * 100 - 12 * 8 * 8, area, 1e-6);
		// Zu klein für Stufen: ein schlichtes Rechteck.
		RecCanvas tiny = new RecCanvas(1);
		PhoneBody.stepped(tiny, 0, 0, 3, 3, 8, 0xFFFFFFFF);
		assertEquals(1, tiny.fills.size());
	}

	// --- Kamera, Foto, Neuer Post ---

	/** Öffnet die Kamera über den Knopf im Feed; danach ist ein Bild gezeichnet. */
	private PhoneUi camera(PhoneHold hold) {
		PhoneUi ui = ui(hold);
		ui.testTicker = 1000;
		frame(ui);
		click(ui, "camera");
		assertEquals(PhoneUi.View.CAMERA, ui.testView());
		frame(ui);
		return ui;
	}

	/** Auslösen und warten, bis „Neuer Post“ offen ist. */
	private Reels.Shot shoot(PhoneUi ui) {
		click(ui, "camShutter");
		assertTrue(ui.testBusy(), "nach dem Auslösen läuft die Aufnahme");
		frame(ui);
		frame(ui);
		assertEquals(PhoneUi.View.COMPOSE, ui.testView());
		Reels.Shot shot = ui.testShot();
		assertNotNull(shot);
		assertFalse(shot.failed);
		assertTrue(Files.isRegularFile(shot.file), "Foto gespeichert: " + shot.file);
		return shot;
	}

	/** „Neuer Post“ ist kurz offen und Enter war losgelassen: ab jetzt gelten „Posten“ und Enter. */
	private void armPost(PhoneUi ui) {
		assertEquals(PhoneUi.View.COMPOSE, ui.testView());
		host.down.clear();
		ui.testTicker += PhoneUi.POST_GUARD_MS;
		frame(ui);
	}

	@Test
	void cameraButtonWorksInBothHoldsAndEscapeGoesBackToTheFeed() {
		post("Hallo", host.name, 1, false);
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = camera(hold);
			RecCanvas c = frame(ui);
			assertTrue(c.shows(I18n.tr("reels.camera.back")), hold + ": " + c.strings());
			assertTrue(c.shows(I18n.tr("reels.camera.photo")) && c.shows(I18n.tr("reels.camera.selfie")), hold + ": " + c.strings());
			assertFalse(c.shows("Hallo"), "der Feed ist nicht mehr zu sehen");
			for (String spot : new String[]{"viewfinder", "camBack", "camPhoto", "camSelfie", "camShutter", "camFlip", "camThumb"}) {
				assertNotNull(ui.testSpot(spot), hold + ": " + spot);
			}
			// Esc: zurück zum Feed, das Handy bleibt offen.
			assertTrue(ui.keyPressed(0, UiKey.ESCAPE, false), "Esc in der Kamera ist verbraucht");
			assertEquals(PhoneUi.View.FEED, ui.testView());
			assertFalse(ui.isClosing());
			assertTrue(frame(ui).shows("Hallo"));
			// „Zurück“ und das kleine Foto führen auch zum Feed.
			for (String back : new String[]{"camBack", "camThumb"}) {
				click(ui, "camera");
				frame(ui);
				click(ui, back);
				assertEquals(PhoneUi.View.FEED, ui.testView(), back);
				frame(ui);
			}
			ui.dispose();
		}
	}

	@Test
	void viewfinderAsksForFramesSixTimesASecondAndIsNeverBlownUp() {
		for (PhoneHold hold : PhoneHold.values()) {
			host.requests.clear();
			host.queue.clear();
			host.frameW = 0;
			host.frameH = 0;
			PhoneUi ui = ui(hold);
			ui.testTicker = 1000;
			frame(ui);
			assertEquals(0, host.requests.size(), "im Feed wird kein Bild angefordert");
			click(ui, "camera");
			RecCanvas first = frame(ui);
			PhoneLayout l = PhoneLayout.of(host.w, host.h, hold);
			assertEquals(1, host.requests(false), "das erste Bild sofort");
			assertEquals(l.screenW, host.requests.get(0)[1], "der Sucher ist so breit wie der Bildschirm des Handys");
			assertEquals(l.screenH, host.requests.get(0)[2]);
			assertNull(ui.testViewfinder(), "noch kein Bild da");
			assertTrue(first.imagesOf(PhoneUi.FINDER_TEXTURE).isEmpty());

			// Das Bild ist da: zugeschnitten und verkleinert auf genau die Größe des Suchers.
			RecCanvas c = frame(ui);
			int[] tex = ui.testViewfinder();
			assertNotNull(tex, hold.toString());
			assertEquals(l.screenW, tex[0], hold + ": 1920×1080 reicht für den Sucher – die Textur ist so groß wie er");
			assertEquals(l.screenH, tex[1]);
			assertTrue(store.live.contains(PhoneUi.FINDER_TEXTURE));
			List<RecCanvas.Img> drawn = c.imagesOf(PhoneUi.FINDER_TEXTURE);
			assertEquals(1, drawn.size());
			assertEquals(l.screenX, drawn.get(0).minX(), 0.01);
			assertEquals(l.screenY, drawn.get(0).minY(), 0.01);
			assertEquals(l.screenX + l.screenW, drawn.get(0).maxX(), 0.01);
			assertEquals(l.screenY + l.screenH, drawn.get(0).maxY(), 0.01);

			// Takt: erst nach 160 ms das nächste.
			assertEquals(1, host.requests(false));
			ui.testTicker += 100;
			frame(ui);
			assertEquals(1, host.requests(false), "nach 100 ms noch keines");
			ui.testTicker += 60;
			frame(ui);
			assertEquals(2, host.requests(false), "nach 160 ms das nächste");
			frame(ui);
			ui.testTicker += 160;
			frame(ui);
			assertEquals(3, host.requests(false));
			frame(ui);

			// Ein kleines Bild der Welt wird nie vergrößert: die Textur ist dann nur so groß wie der Ausschnitt.
			host.frameW = 480;
			host.frameH = 270;
			ui.testTicker += 160;
			frame(ui);
			frame(ui);
			int[] small = ui.testViewfinder();
			int[] crop = dev.theredstonee.trsclient.core.reels.PhotoFrame.cropRect(480, 270, l.screenW, l.screenH);
			assertEquals(crop[2], small[0], hold + ": nie größer als die Quelle");
			assertEquals(crop[3], small[1]);
			assertTrue(small[0] < l.screenW);

			// Liefert das Spiel kein Bild, wird nach zwei Sekunden neu gefragt (statt für immer zu warten).
			host.frames = false;
			int before = host.requests(false);
			ui.testTicker += 160;
			frame(ui);
			assertEquals(before + 1, host.requests(false));
			ui.testTicker += 1000;
			frame(ui);
			assertEquals(before + 1, host.requests(false));
			ui.testTicker += 1100;
			frame(ui);
			assertEquals(before + 2, host.requests(false));
			host.frames = true;

			// Kamera zu: Sucher-Textur frei, keine Anforderungen mehr.
			ui.keyPressed(0, UiKey.ESCAPE, false);
			assertFalse(store.live.contains(PhoneUi.FINDER_TEXTURE), "Sucher freigegeben");
			int after = host.requests.size();
			ui.testTicker += 1000;
			frame(ui);
			assertEquals(after, host.requests.size());
			ui.dispose();
		}
	}

	@Test
	void shutterSavesThePhotoUncroppedInItsHoldAndPostingPutsItFirst() throws IOException {
		post("Älterer Post von gestern", host.name, 30, false);
		assertEquals(1, backend.streak(NOON), "gestern gepostet: die Streak läuft noch");
		int[][] expect = {{1280, 720}, {608, 1080}};
		PhoneHold[] holds = {PhoneHold.LANDSCAPE, PhoneHold.PORTRAIT};
		for (int i = 0; i < holds.length; i++) {
			PhoneUi ui = camera(holds[i]);
			int photosBefore = host.requests(true);
			int feedBefore = backend.feed().size();
			int[] change = ui.streakChange();
			Reels.Shot shot = shoot(ui);
			assertEquals(photosBefore + 1, host.requests(true), "genau ein Bild in voller Auflösung");
			assertEquals(expect[i][0], shot.width, holds[i] + ": 1920×1080 → mittiger Ausschnitt, nie vergrößert");
			assertEquals(expect[i][1], shot.height);
			assertEquals(holds[i] == PhoneHold.PORTRAIT, shot.portrait);
			assertFalse(shot.selfie);
			BufferedImage saved = ImageIO.read(shot.file.toFile());
			assertEquals(expect[i][0], saved.getWidth());
			assertEquals(expect[i][1], saved.getHeight());
			assertEquals(feedBefore, backend.feed().size(), "noch nicht gepostet");

			// „Neuer Post“: Vorschau, Feld, Knöpfe, Hinweise.
			RecCanvas c = frame(ui);
			assertTrue(store.live.contains(PhoneUi.PREVIEW_TEXTURE));
			assertFalse(store.live.contains(PhoneUi.FINDER_TEXTURE), "der Sucher ruht");
			assertEquals(1, c.imagesOf(PhoneUi.PREVIEW_TEXTURE).size(), "das Foto passt zur Haltung: einmal, füllend");
			assertTrue(c.shows(I18n.tr("reels.compose.title")), c.strings().toString());
			assertTrue(c.shows(I18n.tr("reels.compose.hint")), "leeres Feld zeigt den Hinweis");
			assertTrue(c.shows(I18n.tr("reels.compose.retry")) && c.shows(I18n.tr("reels.compose.post")));
			assertTrue(c.shows(I18n.tr("reels.compose.local")));
			String streakLine = i == 0 ? I18n.tr("reels.compose.streak", 1, 2) : I18n.tr("reels.compose.streak.same", 2);
			assertTrue(c.shows(streakLine), "Streak-Hinweis „" + streakLine + "“: " + c.strings());
			assertEquals(i == 0 ? 2 : 2, change[1]);

			// Tippen: Buchstaben sind hier nie Kurztasten (K steckt nicht weg, R dreht nicht).
			String caption = "Mein Haus " + (i + 1);
			for (char ch : caption.toCharArray()) {
				ui.keyPressed(Character.toUpperCase(ch), UiKey.NONE, false);
				ui.inputChar(ch);
			}
			assertEquals(caption, ui.testCaption());
			assertEquals(holds[i], ui.testHold());
			assertFalse(ui.isClosing());
			assertTrue(frame(ui).shows(caption));

			// Enter postet: vorn im Feed, Streak 2, Meldung auf dem Handy.
			armPost(ui);
			ui.keyPressed(0, UiKey.ENTER, false);
			assertEquals(PhoneUi.View.FEED, ui.testView());
			assertEquals(0, ui.testPage(), "der neue Post steht vorn");
			assertEquals(feedBefore + 1, backend.feed().size());
			ReelsPost top = backend.feed().get(0);
			assertEquals(caption, top.caption);
			assertEquals(host.name, top.author);
			assertEquals(shot.fileName, top.photo);
			assertEquals(holds[i] == PhoneHold.PORTRAIT, top.portrait);
			assertEquals(2, backend.streak(NOON));
			assertEquals(I18n.tr("reels.toast.posted", 2), ui.testToast());
			assertNull(ui.testShot());
			assertFalse(store.live.contains(PhoneUi.PREVIEW_TEXTURE), "Vorschau freigegeben");
			c = frame(ui);
			assertTrue(c.shows(caption), "der Feed zeigt den neuen Post");
			assertNotNull(ui.testSpot("toast"));
			assertTrue(c.shows("Posted!"), c.strings().toString());
			// Die Meldung verschwindet von selbst.
			ui.testTicker += PhoneUi.TOAST_MS + 1;
			c = frame(ui);
			assertNull(ui.testToast());
			assertFalse(c.shows("Posted!"));
			assertTrue(Files.isRegularFile(shot.file), "das gepostete Foto bleibt");
			ui.dispose();
			assertTrue(Files.isRegularFile(shot.file), "auch nach dem Wegstecken");
			assertEquals(feedBefore + 1, new LocalReelsBackend(tmp, UTC).feed().size(), "der Post ist gesichert");
		}
	}

	@Test
	void firstPostEverStartsTheStreakAtOne() {
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		assertEquals(0, backend.streak(NOON));
		shoot(ui);
		// Ein Tag ist „1 day“, nicht „1 days“ – in jeder Sprache mit eigener Fassung.
		assertEquals("Streak: 0 → 1 day", I18n.tr("reels.compose.streak.one", 0, 1));
		assertTrue(frame(ui).shows(I18n.tr("reels.compose.streak.one", 0, 1)));
		armPost(ui);
		click(ui, "composePost");
		assertEquals(1, backend.feed().size());
		assertEquals(1, backend.streak(NOON));
		assertEquals("", backend.feed().get(0).caption, "ohne Text geht auch");
		assertEquals(I18n.tr("reels.toast.posted.one", 1), ui.testToast());
		assertEquals("Posted! Streak: 1 day", ui.testToast());
		// Am selben Tag noch ein Foto: die Streak bleibt bei einem Tag.
		frame(ui);
		click(ui, "camera");
		frame(ui);
		shoot(ui);
		assertTrue(frame(ui).shows(I18n.tr("reels.compose.streak.same.one", 1)), "„Streak: 1 day“");
		// Die Meldung steht unter Kopfzeile und Titel, nicht darauf.
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		RecCanvas c = frame(ui);
		int[] toast = ui.testSpot("toast");
		int[] streak = ui.testSpot("streak");
		assertNotNull(toast);
		assertTrue(toast[1] >= streak[1] + streak[3], "Meldung unter der Kopfzeile");
		for (RecCanvas.Text t : c.texts) {
			if (t.text.equals(I18n.tr("reels.tab"))) assertTrue(t.y + t.h <= toast[1], "Meldung unter dem Titel „Reels“");
		}
		ui.dispose();
	}

	@Test
	void halfAnEmojiFromTheClipboardNeverReachesTheFeed() throws IOException {
		// Ein Emoji sind zwei Zeichen (ein Paar). Eine Hälfte allein ließe sich nicht speichern – und hielte dann jede
		// weitere Sicherung des Feeds auf.
		String pair = new String(Character.toChars(0x1F642));
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		shoot(ui);
		host.clipboard = "nice " + pair;
		ui.inputKey(0, UiKey.PASTE, false);
		assertEquals("nice " + pair, ui.testCaption());
		ui.inputKey(0, UiKey.BACKSPACE, false);
		assertEquals("nice ", ui.testCaption(), "die Rücktaste nimmt das Paar als Ganzes");
		ui.inputChar('!');
		// Eine einzelne Hälfte in der Zwischenablage kommt gar nicht erst ins Feld.
		host.clipboard = pair.substring(0, 1) + "!" + pair.substring(1);
		ui.inputKey(0, UiKey.PASTE, false);
		assertEquals("nice !!", ui.testCaption());
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		assertEquals("nice !!", backend.feed().get(0).caption);
		backend.flush();
		assertFalse(backend.dirty());

		// An der Längengrenze: das Paar passt nur halb – dann gar nicht, und dahinter kommt nichts mehr.
		frame(ui);
		click(ui, "camera");
		frame(ui);
		shoot(ui);
		StringBuilder x = new StringBuilder();
		for (int i = 0; i < ReelsPost.MAX_CAPTION - 1; i++) x.append('x');
		host.clipboard = x + pair + "y";
		ui.inputKey(0, UiKey.PASTE, false);
		assertEquals(x.toString(), ui.testCaption());
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		assertEquals(x.toString(), backend.feed().get(0).caption);

		// Dasselbe im Kommentar.
		frame(ui);
		ui.inputKey(0, UiKey.ENTER, false);
		assertEquals(PhoneUi.View.COMMENTS, ui.testView());
		host.clipboard = "ok " + pair + pair;
		ui.inputKey(0, UiKey.PASTE, false);
		ui.inputKey(0, UiKey.BACKSPACE, false);
		ui.inputKey(0, UiKey.ENTER, false);
		assertEquals("ok " + pair, backend.feed().get(0).comments.get(0).text);

		backend.flush();
		assertFalse(backend.dirty(), "alles gesichert");
		assertFalse(Files.exists(tmp.resolve("feed.json.tmp")));
		LocalReelsBackend read = new LocalReelsBackend(tmp, UTC);
		assertEquals(2, read.feed().size(), "beide Posts sind nach dem Neustart da");
		assertEquals(x.toString(), read.feed().get(0).caption);
		assertEquals("ok " + pair, read.feed().get(0).comments.get(0).text);
		assertEquals("nice !!", read.feed().get(1).caption);
		ui.dispose();
	}

	@Test
	void afterPostingTheFeedShowsTheNewPostEvenWhenOlderPostsCarryALaterTime() {
		// Die Uhr des PCs wurde zurückgestellt: zwei Posts tragen eine Zeit, die jetzt in der Zukunft liegt. Der Feed ist
		// nach der Zeit sortiert, der neue Post steht deshalb hinter ihnen.
		post("Von morgen", host.name, -24, false);
		post("Von übermorgen", host.name, -48, false);
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		shoot(ui);
		for (char ch : "Jetzt".toCharArray()) ui.inputChar(ch);
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		assertEquals(3, backend.feed().size());
		assertEquals("Jetzt", backend.feed().get(2).caption);
		assertEquals(PhoneUi.View.FEED, ui.testView());
		assertEquals(2, ui.testPage(), "der Feed zeigt trotzdem den neuen Post, nicht Seite 1");
		frame(ui);
		frame(ui);
		RecCanvas c = frame(ui);
		assertTrue(c.shows("Jetzt"), c.strings().toString());
		assertFalse(c.shows("Von übermorgen"));
		String photo = photoTexture(c);
		// Auch das kleine Bild neben dem Auslöser ist das eben gepostete Foto.
		click(ui, "camera");
		frame(ui);
		c = frame(ui);
		assertEquals(photo, photoTexture(c));
		ui.dispose();
	}

	@Test
	void puttingThePhoneAwayOnSelfieTurnsTheGameCameraBackAtOnce() {
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		click(ui, "camSelfie");
		frame(ui);
		assertTrue(ui.selfieActive());
		// Handy-Taste in der Kamera: das Handy verschwindet. Die Kamera des Spiels schaut sofort wieder normal – im
		// letzten Bild der Animation ist der Bildschirm schon weg, ein umgedrehtes Bild stünde dann groß im Fenster.
		ui.keyPressed('K', UiKey.NONE, false);
		assertTrue(ui.isClosing());
		assertTrue(ui.testSelfie(), "die Wahl „Selfie“ selbst bleibt");
		assertFalse(ui.selfieActive(), "beim Wegstecken dreht das Spiel die Kamera nicht mehr um");
		// Und der Sucher holt kein neues Bild mehr (er spränge sonst auf die normale Sicht um).
		int before = host.requests(false);
		ui.testTicker += 1000;
		frame(ui);
		ui.testTicker += 1000;
		frame(ui);
		assertEquals(before, host.requests(false));
		ui.dispose();
		assertFalse(ui.selfieActive());
	}

	@Test
	void selfieSwitchesByLabelFlipButtonTabAndFAndMarksThePost() {
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		assertFalse(ui.selfieActive(), "die Kamera startet auf „Foto“");
		assertFalse(ui.testSelfie());
		click(ui, "camSelfie");
		assertTrue(ui.selfieActive());
		// Die gewählte Art ist gelb hinterlegt.
		RecCanvas c = frame(ui);
		int[] on = ui.testSpot("camSelfie");
		RecCanvas.Fill amber = fillOf(c, CameraView.MODE_ON, true);
		assertNotNull(amber);
		assertEquals(on[0], amber.x1, 0.01);
		assertEquals(on[1], amber.y1, 0.01);
		click(ui, "camPhoto");
		assertFalse(ui.selfieActive());
		click(ui, "camFlip");
		assertTrue(ui.selfieActive(), "„Kamera wechseln“ schaltet um");
		frame(ui);
		ui.keyPressed(0, UiKey.TAB, false);
		assertFalse(ui.selfieActive(), "Tab schaltet um");
		host.down.clear();
		ui.keyPressed('F', UiKey.NONE, false);
		assertTrue(ui.selfieActive(), "F schaltet um");
		frame(ui);

		// Nur in der Kamera dreht das Spiel die Kamera um: nicht in „Neuer Post“, nicht im Feed, nicht nach dem Wegstecken.
		Reels.Shot shot = shoot(ui);
		assertTrue(shot.selfie);
		assertFalse(ui.selfieActive(), "„Neuer Post“: die Kamera des Spiels schaut wieder normal");
		assertTrue(frame(ui).shows(I18n.tr("reels.camera.selfie")), "der Kopf nennt die Art des Fotos");
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		assertTrue(backend.feed().get(0).selfie);
		assertFalse(ui.selfieActive());
		frame(ui);
		click(ui, "camera");
		assertTrue(ui.selfieActive(), "die Kamera merkt sich „Selfie“, solange das Handy draußen ist");
		ui.keyPressed(0, UiKey.ESCAPE, false);
		assertFalse(ui.selfieActive());
		frame(ui);
		click(ui, "camera");
		ui.dispose();
		assertFalse(ui.selfieActive(), "weggesteckt: nie Selfie");
	}

	@Test
	void retryAndEscapeDeleteThePhotoAndGoBackToTheCamera() {
		PhoneUi ui = camera(PhoneHold.LANDSCAPE);
		Reels.Shot first = shoot(ui);
		frame(ui);
		click(ui, "composeRetry");
		assertEquals(PhoneUi.View.CAMERA, ui.testView());
		assertFalse(Files.exists(first.file), "„Nochmal“ löscht das Foto");
		assertNull(ui.testShot());
		assertFalse(store.live.contains(PhoneUi.PREVIEW_TEXTURE));
		frame(ui);

		Reels.Shot second = shoot(ui);
		for (char ch : "weg damit".toCharArray()) ui.inputChar(ch);
		assertTrue(ui.keyPressed(0, UiKey.ESCAPE, false));
		assertEquals(PhoneUi.View.CAMERA, ui.testView(), "Esc: zurück zur Kamera, nicht aus dem Handy");
		assertFalse(Files.exists(second.file));
		frame(ui);

		Reels.Shot third = shoot(ui);
		assertEquals("", ui.testCaption(), "der alte Text ist weg");
		ui.keyPressed(0, UiKey.TAB, false);
		assertEquals(PhoneUi.View.CAMERA, ui.testView(), "Tab = Nochmal");
		assertFalse(Files.exists(third.file));
		assertTrue(backend.feed().isEmpty(), "nichts wurde gepostet");
		ui.dispose();
	}

	@Test
	void puttingThePhoneAwayDeletesAnUnpostedPhotoAndFreesTheCameraTextures() {
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		frame(ui);
		assertTrue(store.live.contains(PhoneUi.FINDER_TEXTURE));
		Reels.Shot shot = shoot(ui);
		frame(ui);
		assertTrue(store.live.contains(PhoneUi.PREVIEW_TEXTURE));
		ui.dispose();
		assertFalse(Files.exists(shot.file), "nicht gepostet: das Foto bleibt nicht liegen");
		assertTrue(store.live.isEmpty(), "alle Texturen frei: " + store.live);
		assertTrue(backend.feed().isEmpty());

		// Weggesteckt, als das Foto gerade fertig, aber noch nicht abgeholt war: es bleibt nicht liegen.
		PhoneUi busy = camera(PhoneHold.PORTRAIT);
		click(busy, "camShutter");
		frame(busy);
		assertTrue(busy.testBusy());
		assertEquals(1, photoFiles().size(), "das Foto ist schon gespeichert");
		busy.dispose();
		assertNull(reels.pollShot(), "nichts bleibt in der Warteschlange");
		assertTrue(photoFiles().isEmpty(), "und nichts auf der Platte: " + photoFiles());
		// Ein Foto, das trotzdem liegen blieb (fremder Aufrufer), holt das nächste Handy ab und löscht es.
		reels.savePhoto(new int[16 * 9], 16, 9, PhoneHold.LANDSCAPE, false, null);
		assertEquals(1, photoFiles().size());
		PhoneUi next = ui(PhoneHold.PORTRAIT);
		assertNull(reels.pollShot(), "das neue Handy hat liegengebliebene Fotos abgeholt");
		assertTrue(photoFiles().isEmpty());
		assertEquals(PhoneUi.View.FEED, next.testView());
		next.dispose();
	}

	/** Die Foto-Dateien, die gerade im Ordner liegen. */
	private List<String> photoFiles() {
		List<String> names = new ArrayList<String>();
		java.io.File[] files = tmp.resolve("photos").toFile().listFiles();
		if (files != null) for (java.io.File f : files) names.add(f.getName());
		return names;
	}

	/** Hintergrund-Thread von Hand: Aufträge sammeln und einzeln ausführen. */
	private static final class Manual implements Executor {
		final Deque<Runnable> jobs = new ArrayDeque<Runnable>();

		@Override
		public void execute(Runnable r) {
			jobs.add(r);
		}

		void runAll() {
			Runnable r;
			while ((r = jobs.poll()) != null) r.run();
		}
	}

	@Test
	void photoWhoseSavingWasLeftIsNeverTakenForTheNextOneAndNeverStaysOnDisk() {
		Manual worker = new Manual();
		reels = Reels.forTest(modules, backend, worker, UTC);
		PhoneUi ui = camera(PhoneHold.LANDSCAPE);
		worker.runAll();
		frame(ui);
		// Auslösen (rotes Bild), dann Esc, bevor das Foto gespeichert ist.
		host.frameW = 320;
		host.frameH = 180;
		click(ui, "camShutter");
		frame(ui);
		assertTrue(ui.testBusy(), "das Foto wartet im Hintergrund");
		Runnable first = worker.jobs.pollLast();
		assertNotNull(first);
		ui.keyPressed(0, UiKey.ESCAPE, false);
		assertEquals(PhoneUi.View.FEED, ui.testView());
		frame(ui);
		// Kamera wieder auf, zweites Foto in anderer Größe – auch das wartet noch.
		click(ui, "camera");
		frame(ui);
		worker.runAll();
		frame(ui);
		host.frameW = 640;
		host.frameH = 360;
		click(ui, "camShutter");
		frame(ui);
		Runnable second = worker.jobs.pollLast();
		assertNotNull(second);
		// Jetzt wird das erste fertig: es gehört zu keinem Auslösen mehr und wird gleich gelöscht.
		first.run();
		worker.runAll();
		assertTrue(photoFiles().isEmpty(), "das verlassene Foto bleibt nicht liegen: " + photoFiles());
		frame(ui);
		assertEquals(PhoneUi.View.CAMERA, ui.testView(), "das alte Foto öffnet „Neuer Post“ nicht");
		assertTrue(ui.testBusy());
		second.run();
		frame(ui);
		assertEquals(PhoneUi.View.COMPOSE, ui.testView());
		assertEquals(640, ui.testShot().width, "„Neuer Post“ zeigt das Foto dieses Auslösens");
		assertEquals(1, photoFiles().size());
		// Weggesteckt, während ein drittes Foto noch gespeichert wird: der Hintergrund löscht es, sobald es fertig ist.
		ui.keyPressed(0, UiKey.ESCAPE, false);
		worker.runAll();
		frame(ui);
		click(ui, "camShutter");
		frame(ui);
		Runnable third = worker.jobs.pollLast();
		assertNotNull(third);
		ui.dispose();
		third.run();
		worker.runAll();
		assertNull(reels.pollShot());
		assertTrue(photoFiles().isEmpty(), "nach dem Wegstecken liegt kein Foto ohne Post herum: " + photoFiles());
	}

	@Test
	void thePressThatTakesThePhotoNeverPostsIt() {
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		// Enter löst aus und bleibt unten (bzw. wiederholt sich), während „Neuer Post“ aufgeht.
		host.down.add("key.keyboard.enter");
		ui.keyPressed(0, UiKey.ENTER, false);
		frame(ui);
		frame(ui);
		assertEquals(PhoneUi.View.COMPOSE, ui.testView());
		ui.keyPressed(0, UiKey.ENTER, false);
		ui.testTicker += 2 * PhoneUi.POST_GUARD_MS;
		frame(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		assertEquals(PhoneUi.View.COMPOSE, ui.testView(), "gehaltenes Enter postet nie – auch nicht nach der Wartezeit");
		assertTrue(backend.feed().isEmpty());
		// Losgelassen: jetzt postet Enter.
		host.down.clear();
		frame(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		assertEquals(PhoneUi.View.FEED, ui.testView());
		assertEquals(1, backend.feed().size());
		frame(ui);

		// Zweites ungeduldiges Enter bzw. Doppelklick gleich nach dem Auslösen: innerhalb der Wartezeit passiert nichts.
		click(ui, "camera");
		frame(ui);
		shoot(ui);
		host.down.clear();
		frame(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		click(ui, "composePost");
		assertEquals(PhoneUi.View.COMPOSE, ui.testView(), "zu früh: nichts gepostet");
		assertEquals(1, backend.feed().size());
		ui.testTicker += PhoneUi.POST_GUARD_MS - 1;
		frame(ui);
		click(ui, "composePost");
		assertEquals(1, backend.feed().size(), "1 ms vor Ablauf noch nicht");
		ui.testTicker += 1;
		frame(ui);
		click(ui, "composePost");
		assertEquals(2, backend.feed().size(), "danach gilt „Posten“");
		ui.dispose();
	}

	@Test
	void arrowKeysNeverMoveTheCursorAwayFromTheCaretShown() {
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		shoot(ui);
		for (char ch : "Hallo".toCharArray()) ui.inputChar(ch);
		for (UiKey k : new UiKey[]{UiKey.LEFT, UiKey.LEFT, UiKey.HOME, UiKey.RIGHT, UiKey.END, UiKey.LEFT}) {
			assertTrue(ui.keyPressed(0, k, false));
		}
		ui.inputChar('X');
		assertEquals("HalloX", ui.testCaption(), "der Cursor steht dort, wo er gezeichnet wird: am Ende");
		ui.keyPressed(0, UiKey.LEFT, false);
		ui.keyPressed(0, UiKey.BACKSPACE, false);
		assertEquals("Hallo", ui.testCaption(), "Rücktaste löscht das letzte Zeichen");
		ui.keyPressed(0, UiKey.DELETE, false);
		assertEquals("Hallo", ui.testCaption());
		ui.dispose();
		// Das Kommentarfeld dagegen hat eine bewegliche Schreibmarke – und zeigt sie dort, wo sie steht.
		post("Hallo", host.name, 1, false);
		PhoneUi feed = ui(PhoneHold.LANDSCAPE);
		frame(feed);
		feed.keyPressed(0, UiKey.ENTER, false);
		assertEquals(PhoneUi.View.COMMENTS, feed.testView());
		for (char ch : "abc".toCharArray()) feed.inputChar(ch);
		feed.keyPressed(0, UiKey.LEFT, false);
		feed.keyPressed(0, UiKey.HOME, false);
		feed.inputChar('d');
		assertEquals("dabc", feed.commentInput.text());
		assertEquals(1, feed.testCommentCaret());
		feed.dispose();
	}

	@Test
	void secondGameWindowTakesNoPhotoAndSaysWhy() {
		final ReelsBackend real = backend;
		ReelsBackend second = new ReelsBackend() {
			@Override public List<ReelsPost> feed() { return real.feed(); }
			@Override public ReelsPost post(String f, int w, int h, boolean p, boolean s, String c, String a, long n) { return real.post(f, w, h, p, s, c, a, n); }
			@Override public boolean toggleLike(String id) { return real.toggleLike(id); }
			@Override public ReelsComment comment(String id, String a, String t, long n) { return real.comment(id, a, t, n); }
			@Override public int streak(long n) { return real.streak(n); }
			@Override public int streakAfterPost(long n) { return real.streakAfterPost(n); }
			@Override public java.nio.file.Path photoFile(ReelsPost p) { return real.photoFile(p); }
			@Override public java.nio.file.Path newPhotoFile(String id) { return real.newPhotoFile(id); }
			@Override public void flush() { }
			@Override public boolean readOnly() { return true; }
		};
		reels = Reels.forTest(modules, second, DIRECT, UTC);
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		click(ui, "camShutter");
		assertFalse(ui.testBusy(), "im Zweitfenster wird kein Foto aufgenommen");
		assertEquals(0, host.requests(true));
		assertEquals(I18n.tr("reels.toast.readonly"), ui.testToast());
		frame(ui);
		frame(ui);
		assertEquals(PhoneUi.View.CAMERA, ui.testView());
		assertTrue(photoFiles().isEmpty(), "kein Foto ohne Post");
		assertFalse(backend.readOnly(), "der normale Speicher nimmt Fotos an");
		ui.dispose();
	}

	@Test
	void viewfinderSlowsDownAboveFullHdBecauseEveryFrameIsReadAtWindowSize() {
		assertEquals(160, PhoneUi.finderInterval(1920, 1080));
		assertEquals(160, PhoneUi.finderInterval(854, 480));
		assertEquals(160, PhoneUi.finderInterval(0, 0));
		assertEquals(284, PhoneUi.finderInterval(2560, 1440));
		assertEquals(320, PhoneUi.finderInterval(3840, 2160), "höchstens doppelt so lang");
		assertEquals(213, PhoneUi.finderInterval(2560, 1080));
		// Im Spiel: bei 4K kommt das nächste Sucherbild erst nach 320 ms.
		host.size(3840, 2160, 2);
		PhoneUi ui = camera(PhoneHold.LANDSCAPE);
		frame(ui);
		int before = host.requests(false);
		ui.testTicker += 300;
		frame(ui);
		assertEquals(before, host.requests(false), "nach 300 ms noch keines");
		ui.testTicker += 20;
		frame(ui);
		assertEquals(before + 1, host.requests(false));
		ui.dispose();
	}

	@Test
	void cameraStaysUsableWhenNoFrameArrivesOrSavingFails() {
		PhoneUi ui = camera(PhoneHold.LANDSCAPE);
		host.frames = false;
		click(ui, "camShutter");
		assertTrue(ui.testBusy());
		int clicks = host.clicks;
		click(ui, "camShutter");
		assertEquals(clicks, host.clicks, "während der Aufnahme löst der Knopf nicht noch einmal aus");
		assertEquals(1, host.requests(true));
		ui.testTicker += PhoneUi.FRAME_TIMEOUT_MS - 100;
		frame(ui);
		assertTrue(ui.testBusy());
		assertNull(ui.testToast());
		ui.testTicker += 200;
		RecCanvas c = frame(ui);
		assertFalse(ui.testBusy(), "nach zwei Sekunden ohne Bild ist die Kamera wieder frei");
		assertEquals(I18n.tr("reels.toast.failed"), ui.testToast());
		assertEquals(PhoneUi.View.CAMERA, ui.testView());
		assertNotNull(ui.testSpot("toast"));
		assertTrue(c.shows("could not be saved") || c.shows("photo"), c.strings().toString());

		// Speichern schlägt fehl (kein Foto-Ordner): Meldung, Kamera bleibt.
		host.frames = true;
		Reels.forTest(modules, new LocalReelsBackend(null, UTC), DIRECT, UTC);
		PhoneUi broken = new PhoneUi(Reels.get(), host);
		broken.testNow = NOON;
		broken.testTicker = 1000;
		frame(broken);
		broken.openCamera();
		frame(broken);
		broken.shutter();
		frame(broken);
		frame(broken);
		assertEquals(PhoneUi.View.CAMERA, broken.testView());
		assertEquals(I18n.tr("reels.toast.failed"), broken.testToast());
		assertFalse(broken.testBusy());
		broken.dispose();
		ui.dispose();
	}

	@Test
	void draggingTheViewfinderAndArrowKeysTurnTheView() {
		PhoneUi ui = camera(PhoneHold.LANDSCAPE);
		int[] v = ui.testSpot("viewfinder");
		double gx = (v[0] + v[2] * 0.3) / host.gui, gy = (v[1] + v[3] * 0.5) / host.gui;
		assertTrue(ui.inputClick(gx, gy, 0));
		assertEquals(0, host.turns, "der Druck allein dreht nichts");
		assertTrue(ui.inputDrag(gx + 50, gy + 10, 0));
		// 50 GUI-Einheiten = 100 Fensterpixel bei GUI 2.
		assertEquals(100 * PhoneUi.LOOK_DEG_PER_PX, host.yaw, 1e-3);
		assertEquals(20 * PhoneUi.LOOK_DEG_PER_PX, host.pitch, 1e-3);
		ui.inputDrag(gx + 50, gy - 10, 0);
		assertEquals(-20 * PhoneUi.LOOK_DEG_PER_PX, host.pitch, 1e-3);
		ui.inputRelease(gx + 50, gy - 10, 0);
		// Neuer Druck an anderer Stelle: kein Sprung.
		float yaw = host.yaw;
		ui.inputClick(gx + 200, gy, 0);
		assertEquals(yaw, host.yaw, 1e-6);
		ui.inputRelease(gx + 200, gy, 0);
		// Ein Klick auf einen Knopf dreht nichts.
		int turns = host.turns;
		click(ui, "camFlip");
		assertEquals(turns, host.turns);

		host.yaw = 0;
		host.pitch = 0;
		ui.keyPressed(0, UiKey.RIGHT, false);
		ui.keyPressed(0, UiKey.RIGHT, false);
		ui.keyPressed(0, UiKey.LEFT, false);
		ui.keyPressed(0, UiKey.DOWN, false);
		assertEquals(PhoneUi.LOOK_DEG_PER_KEY, host.yaw, 1e-6);
		assertEquals(PhoneUi.LOOK_DEG_PER_KEY, host.pitch, 1e-6);
		assertEquals(0, ui.testPage(), "in der Kamera blättern die Pfeile nicht");
		// Die Handy-Taste steckt auch aus der Kamera weg, die Drehen-Taste dreht.
		ui.keyPressed('R', UiKey.NONE, false);
		assertEquals(PhoneHold.PORTRAIT, ui.testHold());
		assertEquals(PhoneUi.View.CAMERA, ui.testView());
		frame(ui);
		ui.keyPressed('K', UiKey.NONE, false);
		assertTrue(ui.isClosing());
		ui.dispose();
	}

	/** Ist die Mitte des Auslösers gelb (Aufnahme läuft)? */
	private static boolean busyShutter(RecCanvas c, PhoneUi ui) {
		int[] r = ui.testSpot("camShutter");
		for (RecCanvas.Fill f : c.fills) {
			if (f.argb == CameraView.SHUTTER_BUSY && f.x1 >= r[0] && f.y1 >= r[1] && f.x2 <= r[0] + r[2] && f.y2 <= r[1] + r[3]) return true;
		}
		return false;
	}

	@Test
	void shutterFlashesAndTheButtonShowsThatItIsBusy() {
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		host.frames = false;
		RecCanvas idle = frame(ui);
		assertFalse(busyShutter(idle, ui), "Auslöser in Ruhe: weiß");
		click(ui, "camShutter");
		RecCanvas c = frame(ui);
		assertTrue(busyShutter(c, ui), "Auslöser während der Aufnahme: gelb");
		// Auch die Leertaste löst aus – aber nicht noch einmal, solange die Aufnahme läuft.
		ui.keyPressed(' ', UiKey.NONE, false);
		assertEquals(1, host.requests(true));
		int[] s = ui.testSpot("screen");
		boolean flash = false;
		for (RecCanvas.Fill f : c.fills) {
			flash |= (f.argb & 0xFFFFFF) == 0xFFFFFF && (f.argb >>> 24) > 100 && (f.argb >>> 24) < 255 && f.x2 - f.x1 == s[2]
					&& f.y2 - f.y1 == s[3];
		}
		assertTrue(flash, "weißer Blitz über dem Sucher");
		ui.testTicker += PhoneUi.FLASH_MS;
		c = frame(ui);
		for (RecCanvas.Fill f : c.fills) {
			assertFalse((f.argb & 0xFFFFFF) == 0xFFFFFF && (f.argb >>> 24) < 255 && (f.argb >>> 24) > 100 && f.x2 - f.x1 == s[2]
					&& f.y2 - f.y1 == s[3], "der Blitz ist vorbei");
		}
		// Gestufte Scheibe: nichts überlappt, oben und unten gespiegelt.
		RecCanvas disc = new RecCanvas(1);
		CameraView.disc(disc, 10, 20, 96, new int[]{24, 16, 8}, 8, 0xFFFFFFFF);
		assertEquals(7, disc.fills.size());
		double area = 0;
		for (RecCanvas.Fill f : disc.fills) area += (f.x2 - f.x1) * (f.y2 - f.y1);
		assertEquals(96 * 96 - 4 * (24 * 8 + 16 * 8 + 8 * 8), area, 1e-6);
		assertEquals(disc.fills.get(0).x1, disc.fills.get(6).x1, 1e-6);
		assertEquals(20 + 96, disc.fills.get(6).y2, 1e-6);
		ui.dispose();
	}

	@Test
	void bodyHasSideButtonsAndTheLampOnlyInPortrait() {
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneLayout l = PhoneLayout.of(1920, 1080, hold);
			RecCanvas c = new RecCanvas(1);
			PhoneBody.draw(c, l);
			RecCanvas.Fill red = fillOf(c, PhoneBody.BUTTON_RED, true);
			assertNotNull(red, hold.toString());
			if (hold == PhoneHold.LANDSCAPE) {
				assertEquals(l.phoneY, red.y2, 1e-6, "quer: Tasten auf der Oberkante");
				assertNull(fillOf(c, PhoneBody.LAMP_ON, true), "quer: keine Lampe");
			} else {
				assertEquals(l.phoneX + l.phoneW, red.x1, 1e-6, "hochkant: Tasten an der rechten Kante");
				RecCanvas.Fill lamp = fillOf(c, PhoneBody.LAMP_ON, true);
				assertNotNull(lamp, "hochkant: Lampe");
				assertTrue(lamp.y2 <= l.screenY, "die Lampe sitzt über dem Bildschirm");
			}
			// Der schwarze Bildschirm kommt zuletzt.
			RecCanvas.Fill last = c.fills.get(c.fills.size() - 1);
			assertEquals(PhoneBody.SCREEN, last.argb);
			assertEquals(l.screenW, last.x2 - last.x1, 1e-6);
		}
	}

	// --- Hilfen für die Blätter (Bild, Feed, Streak) und die Marken ---

	/** Handy im Feed: erstes Bild gezeichnet, fester Taktgeber. */
	private PhoneUi feed(PhoneHold hold) {
		PhoneUi ui = ui(hold);
		ui.testTicker = 5000;
		frame(ui);
		return ui;
	}

	private int[] spot(PhoneUi ui, String name) {
		int[] r = ui.testSpot(name);
		assertNotNull(r, "Stelle fehlt: " + name);
		return r;
	}

	/** Stelle relativ zum Bildschirm des Handys: {x, y, Breite, Höhe}. */
	private int[] onPhone(PhoneUi ui, String name) {
		int[] r = spot(ui, name);
		int[] s = spot(ui, "screen");
		return new int[]{r[0] - s[0], r[1] - s[1], r[2], r[3]};
	}

	private static boolean overlap(int[] a, int[] b) {
		return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
	}

	private static boolean inside(int[] inner, int[] outer) {
		return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[0] + inner[2] <= outer[0] + outer[2]
				&& inner[1] + inner[3] <= outer[1] + outer[3];
	}

	/** Alle Texte des Bildes hintereinander (umbrochene Sätze stehen so wieder in einem Stück da). */
	private static String all(RecCanvas c) {
		StringBuilder sb = new StringBuilder();
		for (String s : c.strings()) sb.append(s).append(' ');
		return sb.toString();
	}

	private static RecCanvas.Text textOf(RecCanvas c, String text) {
		for (RecCanvas.Text t : c.texts) if (t.text.equals(text)) return t;
		return null;
	}

	/** Die Dateien, die im Screenshots-Ordner liegen. */
	private List<String> screenshots() {
		List<String> names = new ArrayList<String>();
		java.io.File[] files = host.screenshots.toFile().listFiles();
		if (files != null) for (java.io.File f : files) names.add(f.getName());
		return names;
	}

	/** Post ohne Foto-Datei, mit Angaben, womit er aufgenommen wurde. */
	private ReelsPost postMeta(String caption, String author, int hoursAgo, ReelsMeta meta) {
		long at = NOON - hoursAgo * 3600000L;
		ReelsPost p = backend.post(ReelsPost.newId(at, UTC, ++salt) + ".png", 1280, 720, false, false, caption, author, at, meta);
		assertNotNull(p);
		return p;
	}

	private static String chars(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) sb.append((char) ('a' + i % 26));
		return sb.toString();
	}

	/** Zweites Spielfenster: derselbe Feed, aber nichts wird gesichert. */
	private ReelsBackend secondWindow(final LocalReelsBackend real) {
		return new ReelsBackend() {
			@Override public List<ReelsPost> feed() { return real.feed(); }
			@Override public ReelsPost post(String f, int w, int h, boolean p, boolean s, String c, String a, long n) { return null; }
			@Override public boolean toggleLike(String id) { return real.toggleLike(id); }
			@Override public ReelsComment comment(String id, String a, String t, long n) { return real.comment(id, a, t, n); }
			@Override public int streak(long n) { return real.streak(n); }
			@Override public int streakAfterPost(long n) { return real.streakAfterPost(n); }
			@Override public Path photoFile(ReelsPost p) { return real.photoFile(p); }
			@Override public Path newPhotoFile(String id) { return real.newPhotoFile(id); }
			@Override public void flush() { }
			@Override public boolean readOnly() { return true; }
			@Override public StreakGuard streakGuard(long n) {
				Set<Long> days = new HashSet<Long>();
				for (ReelsPost p : real.feed()) days.add(Long.valueOf(ReelsTime.epochDay(p.createdAt, 0)));
				long today = ReelsTime.epochDay(n, 0);
				return StreakGuard.of(days, null, today, today, true);
			}
			@Override public StreakGuard.Result protect(boolean yesterday, long n) { return StreakGuard.Result.READ_ONLY; }
		};
	}

	/** Hintergrund-Thread, der auf Wunsch nichts mehr annimmt. */
	private static final class Gate implements Executor {
		boolean reject;

		@Override
		public void execute(Runnable r) {
			if (reject) throw new RejectedExecutionException("zu");
			r.run();
		}
	}

	// --- A: Bild speichern, kopieren, Ordner öffnen ---

	@Test
	void saveButtonSitsUnderCommentInBothHoldsAndIsAtLeastFortyPixels() {
		post("Schaut mal!", host.name, 2, false);
		// Quer: Herz, Kommentar, Speichern, Kamera, Drehen – genau die Rechtecke des Bauplans.
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		assertArrayEquals(new int[]{938, 45, 101, 103}, onPhone(ui, "heart"));
		assertArrayEquals(new int[]{938, 163, 101, 103}, onPhone(ui, "comment"));
		assertArrayEquals(new int[]{938, 281, 101, 50}, onPhone(ui, "save"));
		assertArrayEquals(new int[]{948, 351, 81, 55}, onPhone(ui, "camera"));
		assertArrayEquals(new int[]{938, 426, 101, 55}, onPhone(ui, "rotate"));
		int[] save = spot(ui, "save");
		assertTrue(save[1] + save[3] <= spot(ui, "camera")[1], "quer: über dem Kamera-Knopf");
		ui.dispose();

		ui = feed(PhoneHold.PORTRAIT);
		assertArrayEquals(new int[]{311, 489, 70, 76}, onPhone(ui, "heart"));
		assertArrayEquals(new int[]{311, 578, 70, 76}, onPhone(ui, "comment"));
		assertArrayEquals(new int[]{311, 667, 70, 56}, onPhone(ui, "save"));
		save = spot(ui, "save");
		assertTrue(save[1] + save[3] <= spot(ui, "home")[1], "hochkant: über der Leiste");
		ui.dispose();

		for (PhoneHold hold : PhoneHold.values()) {
			ui = feed(hold);
			int[] comment = spot(ui, "comment");
			save = spot(ui, "save");
			assertTrue(save[1] >= comment[1] + comment[3], hold + ": unter den Kommentaren");
			assertTrue(save[2] >= 40 && save[3] >= 40, hold + ": mindestens 40 Pixel: " + save[2] + "x" + save[3]);
			assertTrue(inside(save, spot(ui, "screen")), hold + ": auf dem Bildschirm");
			assertFalse(overlap(save, spot(ui, "heart")) || overlap(save, comment), hold + ": überlappt nichts");
			ui.dispose();
		}
	}

	@Test
	void saveIconIsSmallInLandscapeAndHeartSizedInPortrait() {
		post("Schaut mal!", host.name, 2, false);
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneLayout l = PhoneLayout.of(host.w, host.h, hold);
			int size = hold == PhoneHold.LANDSCAPE ? l.px(4) : l.px(6);
			PhoneUi ui = ui(hold);
			RecCanvas c = frame(ui);
			int[] save = spot(ui, "save");
			int pad = Math.max(1, Math.round(8 * l.q));
			assertEquals(PhoneIcons.height(PhoneIcons.SAVE, size) + pad, save[3], hold + ": nur das Symbol, keine Zahl");
			// Die oberste Zeile des Symbols: drei Symbol-Pixel breit, mittig in der Spalte.
			double cx = save[0] + save[2] / 2.0;
			RecCanvas.Fill top = null;
			for (RecCanvas.Fill f : c.fills) {
				if (f.argb == FeedView.WHITE && Math.abs(f.y1 - (save[1] + pad / 2)) < 0.01 && Math.abs((f.x1 + f.x2) / 2 - cx) <= 1.0) top = f;
			}
			assertNotNull(top, hold + ": das Symbol steht in seiner Klickfläche");
			assertEquals(3 * size, top.x2 - top.x1, 0.01, hold.toString());
			if (hold == PhoneHold.LANDSCAPE) assertEquals(PhoneIcons.height(PhoneIcons.FLIP, l.px(4)) / 9, size, "quer klein wie „Drehen“");
			else assertEquals(l.px(6), size, "hochkant so groß wie das Herz");
			ui.dispose();
		}
	}

	@Test
	void noSaveButtonWithoutAPost() {
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		assertNull(ui.testSpot("save"), "leerer Feed: kein Speichern-Knopf");
		assertNotNull(ui.testSpot("camera"));
		ui.openSheet(PhoneUi.Sheet.SAVE);
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), "ohne Post kein Blatt „Bild“");
		ui.dispose();
		// Der Filter zeigt nichts: auch dann nicht.
		post("Von mir", host.name, 2, false);
		modules.reels.feedFilter.set(FeedFilter.FRIENDS);
		for (PhoneHold hold : PhoneHold.values()) {
			ui = feed(hold);
			assertNull(ui.testSpot("save"), hold.toString());
			assertNull(ui.testSpot("heart"), hold.toString());
			ui.dispose();
		}
	}

	@Test
	void saveSheetOpensSavesToastsAndCloses() throws IOException {
		ReelsPost p = postWithPhoto(160, 90, 1);
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = feed(hold);
			int clicks = host.clicks;
			click(ui, "save");
			assertEquals(PhoneUi.Sheet.SAVE, ui.testSheet(), hold.toString());
			assertEquals(clicks + 1, host.clicks, "Klick-Ton");
			RecCanvas c = frame(ui);
			for (String s : new String[]{"saveSheet", "saveBack", "saveClose", "saveDo", "saveCopy", "saveFolder"}) spot(ui, s);
			assertTrue(c.strings().contains(I18n.tr("reels.save.title")), c.strings().toString());
			assertTrue(c.shows(I18n.tr("reels.save.do")) && c.shows(I18n.tr("reels.save.copy")) && c.shows(I18n.tr("reels.save.folder")));
			assertTrue(all(c).contains(I18n.tr("reels.save.hint")), "der Hinweis steht ganz da: " + c.strings());
			for (String s : new String[]{"saveDo", "saveCopy", "saveFolder"}) {
				assertTrue(spot(ui, s)[3] >= 40, s + " mindestens 40 Pixel hoch");
				assertTrue(inside(spot(ui, s), spot(ui, "saveSheet")), s + " liegt auf dem Blatt");
			}
			// Das Blatt ist niedriger als das Kommentar-Blatt.
			PhoneLayout l = PhoneLayout.of(host.w, host.h, hold);
			assertTrue(spot(ui, "saveSheet")[3] < Math.round((hold == PhoneHold.LANDSCAPE ? 290 : 400) * l.q), hold.toString());
			// Ein Klick auf das Blatt selbst trifft nichts darunter.
			int[] sheet = spot(ui, "saveSheet");
			ui.inputClick((sheet[0] + 2.0) / host.gui, (sheet[1] + sheet[3] - 2.0) / host.gui, 0);
			assertEquals(PhoneUi.Sheet.SAVE, ui.testSheet());

			click(ui, "saveDo");
			frame(ui);
			String name = ReelsExport.fileName(p.id, 1);
			assertEquals(java.util.Collections.singletonList(name), screenshots(), hold + ": genau eine Datei");
			assertTrue(java.util.Arrays.equals(Files.readAllBytes(backend.photoFile(p)), Files.readAllBytes(host.screenshots.resolve(name))),
					"dieselben Bytes wie das Foto");
			assertEquals(hold == PhoneHold.LANDSCAPE ? I18n.tr("reels.toast.saved") : I18n.tr("reels.toast.saved.already"), ui.testToast());
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), "nach dem Speichern ist das Blatt zu");
			assertFalse(ui.testSheetBusy());
			// X und ein Klick auf das abgedunkelte Foto schließen auch.
			click(ui, "save");
			frame(ui);
			click(ui, "saveClose");
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
			frame(ui);
			click(ui, "save");
			frame(ui);
			click(ui, "saveBack");
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
			assertFalse(backend.feed().get(0).likedByMe, "der Klick ging nicht ans Herz");
			ui.dispose();
		}
		assertTrue(host.copied.isEmpty() && host.opened.isEmpty(), "nichts kopiert, kein Ordner geöffnet");
	}

	@Test
	void secondSaveSaysAlreadyThere() throws IOException {
		ReelsPost p = postWithPhoto(160, 90, 1);
		PhoneUi ui = feed(PhoneHold.PORTRAIT);
		click(ui, "save");
		frame(ui);
		// Enter ist „Bild speichern“.
		assertTrue(ui.inputKey(0, UiKey.ENTER, false));
		frame(ui);
		assertEquals(I18n.tr("reels.toast.saved"), ui.testToast());
		assertEquals(PhoneUi.View.FEED, ui.testView(), "Enter öffnet hier nicht die Kommentare");
		click(ui, "save");
		frame(ui);
		click(ui, "saveDo");
		frame(ui);
		assertEquals(I18n.tr("reels.toast.saved.already"), ui.testToast());
		assertEquals(1, screenshots().size(), "keine zweite Datei: " + screenshots());
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
		// Liegt unter dem Namen etwas anderes, wird nie überschrieben: die Kopie bekommt „_2“.
		Path first = host.screenshots.resolve(ReelsExport.fileName(p.id, 1));
		Files.write(first, new byte[]{1, 2, 3});
		click(ui, "save");
		frame(ui);
		click(ui, "saveDo");
		frame(ui);
		assertEquals(I18n.tr("reels.toast.saved"), ui.testToast());
		assertEquals(3, Files.size(first), "nichts überschrieben");
		assertTrue(Files.isRegularFile(host.screenshots.resolve(ReelsExport.fileName(p.id, 2))));
		ui.dispose();
	}

	@Test
	void twoClicksBeforeTheWorkerRunsGiveOneFileAndTheSavedToast() throws IOException {
		Manual worker = new Manual();
		reels = Reels.forTest(modules, backend, worker, UTC);
		postWithPhoto(160, 90, 1);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		worker.runAll();
		click(ui, "save");
		frame(ui);
		worker.runAll();
		click(ui, "saveDo");
		assertTrue(ui.testSheetBusy(), "der Auftrag wartet im Hintergrund");
		int jobs = worker.jobs.size();
		// Doppelklick, bevor das nächste Bild gezeichnet ist – und noch einer danach.
		click(ui, "saveDo");
		RecCanvas c = frame(ui);
		click(ui, "saveDo");
		click(ui, "saveCopy");
		click(ui, "saveFolder");
		assertTrue(ui.inputKey(0, UiKey.ENTER, false));
		assertEquals(jobs, worker.jobs.size(), "kein zweiter Auftrag");
		// So lange sind alle drei grau.
		for (String key : new String[]{"reels.save.do", "reels.save.copy", "reels.save.folder"}) {
			RecCanvas.Text t = textOf(c, I18n.tr(key));
			assertNotNull(t, key);
			assertEquals(FeedView.TEXT_DIM, t.argb, key + " ist blass");
		}
		assertEquals(ComposeView.BUTTON_GREY, fillAt(c, spot(ui, "saveDo")).argb, "auch „Bild speichern“ ist grau, solange es läuft");
		assertTrue(screenshots().isEmpty());
		assertEquals(PhoneUi.Sheet.SAVE, ui.testSheet());

		worker.runAll();
		frame(ui);
		assertEquals(1, screenshots().size(), "genau eine Datei: " + screenshots());
		assertEquals(I18n.tr("reels.toast.saved"), ui.testToast());
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
		assertFalse(ui.testSheetBusy());
		assertTrue(host.copied.isEmpty() && host.opened.isEmpty());

		// Weggesteckt, bevor der Auftrag fertig ist: die Datei bleibt, eine Meldung gibt es nicht mehr.
		Files.delete(host.screenshots.resolve(screenshots().get(0)));
		ui.testTicker += PhoneUi.TOAST_MS + 1;
		frame(ui);
		click(ui, "save");
		frame(ui);
		click(ui, "saveDo");
		ui.dispose();
		worker.runAll();
		assertEquals(1, screenshots().size());
		assertNull(ui.testToast());
	}

	/** Die erste Fläche, die genau dieses Rechteck füllt (der Grund eines Knopfs), oder null. */
	private static RecCanvas.Fill fillAt(RecCanvas c, int[] r) {
		for (RecCanvas.Fill f : c.fills) {
			if (Math.abs(f.x1 - r[0]) < 0.01 && Math.abs(f.y1 - r[1]) < 0.01 && Math.abs(f.x2 - r[0] - r[2]) < 0.01
					&& Math.abs(f.y2 - r[1] - r[3]) < 0.01) return f;
		}
		return null;
	}

	@Test
	void rejectedWorkerShowsTheFailedToastAndFreesTheSheet() throws IOException {
		Gate worker = new Gate();
		reels = Reels.forTest(modules, backend, worker, UTC);
		postWithPhoto(160, 90, 1);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		click(ui, "save");
		frame(ui);
		String[][] cases = {{"saveDo", "reels.toast.save.failed"}, {"saveCopy", "reels.toast.copy.failed"},
				{"saveFolder", "reels.toast.folder.failed"}};
		for (String[] one : cases) {
			worker.reject = true;
			click(ui, one[0]);
			worker.reject = false;
			assertEquals(I18n.tr(one[1]), ui.testToast(), one[0]);
			assertFalse(ui.testSheetBusy(), one[0] + ": das Blatt ist wieder frei");
			assertEquals(PhoneUi.Sheet.SAVE, ui.testSheet());
			frame(ui);
		}
		assertTrue(screenshots().isEmpty() && host.copied.isEmpty() && host.opened.isEmpty());
		// Danach geht es wieder.
		click(ui, "saveDo");
		frame(ui);
		assertEquals(I18n.tr("reels.toast.saved"), ui.testToast());
		ui.dispose();
	}

	@Test
	void missingPhotoSaysSo() {
		post("Ohne Datei", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		click(ui, "save");
		frame(ui);
		click(ui, "saveDo");
		frame(ui);
		assertEquals(I18n.tr("reels.toast.save.missing"), ui.testToast());
		assertFalse(Files.exists(host.screenshots), "nichts angelegt");
		assertFalse(ui.testSheetBusy());
		assertEquals(PhoneUi.Sheet.SAVE, ui.testSheet(), "das Blatt bleibt offen");
		// Ohne Screenshots-Ordner (der Baum kennt keinen): ehrlich „nicht gespeichert“.
		host.screenshots = null;
		click(ui, "saveDo");
		assertEquals(I18n.tr("reels.toast.save.failed"), ui.testToast());
		assertFalse(ui.testSheetBusy());
		click(ui, "saveFolder");
		assertEquals(I18n.tr("reels.toast.folder.failed"), ui.testToast());
		assertTrue(host.opened.isEmpty());
		ui.dispose();
	}

	@Test
	void copyPictureReportsEachResult() throws IOException {
		ReelsPost p = postWithPhoto(160, 90, 1);
		PhoneUi ui = feed(PhoneHold.PORTRAIT);
		click(ui, "save");
		frame(ui);
		ImageClipboard.Result[] results = {ImageClipboard.Result.OK, ImageClipboard.Result.UNSUPPORTED, ImageClipboard.Result.FAILED};
		String[] keys = {"reels.toast.copied", "reels.toast.copy.unsupported", "reels.toast.copy.failed"};
		for (int i = 0; i < results.length; i++) {
			host.copyResult = results[i];
			click(ui, "saveCopy");
			frame(ui);
			assertEquals(I18n.tr(keys[i]), ui.testToast(), results[i].toString());
			assertEquals(i + 1, host.copied.size(), "über den Baum, nie direkt in die Zwischenablage");
			assertEquals(backend.photoFile(p), host.copied.get(i));
			assertEquals(PhoneUi.Sheet.SAVE, ui.testSheet(), "das Blatt bleibt offen");
			assertFalse(ui.testSheetBusy());
		}
		assertTrue(screenshots().isEmpty(), "Kopieren legt keine Datei an");
		ui.dispose();
	}

	@Test
	void openFolderGoesThroughTheHostAndReportsFailure() throws IOException {
		postWithPhoto(160, 90, 1);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		click(ui, "save");
		frame(ui);
		assertFalse(Files.exists(host.screenshots));
		click(ui, "saveFolder");
		frame(ui);
		assertEquals(java.util.Collections.singletonList(host.screenshots), host.opened);
		assertTrue(Files.isDirectory(host.screenshots), "der Ordner wird angelegt, falls nötig");
		assertNull(ui.testToast(), "geklappt: keine Meldung");
		assertEquals(PhoneUi.Sheet.SAVE, ui.testSheet());
		assertFalse(ui.testSheetBusy());
		host.openOk = false;
		click(ui, "saveFolder");
		frame(ui);
		assertEquals(2, host.opened.size());
		assertEquals(I18n.tr("reels.toast.folder.failed"), ui.testToast());
		// Liegt dort eine Datei statt eines Ordners, lässt er sich nicht anlegen.
		host.openOk = true;
		host.screenshots = tmp.resolve("feed-datei");
		Files.write(host.screenshots, new byte[]{1});
		ui.testTicker += PhoneUi.TOAST_MS + 1;
		frame(ui);
		click(ui, "saveFolder");
		frame(ui);
		assertEquals(2, host.opened.size(), "der Baum wird gar nicht erst gefragt");
		assertEquals(I18n.tr("reels.toast.folder.failed"), ui.testToast());
		ui.dispose();
	}

	@Test
	void aRelativeScreenshotsPathIsMadeAbsolute() throws IOException {
		ReelsPost p = postWithPhoto(160, 90, 1);
		Path target = tmp.resolve("spiel").resolve("screenshots").toAbsolutePath().normalize();
		Path here = java.nio.file.Paths.get("").toAbsolutePath();
		// Wie im Entwickler-Start: der Spielordner ist nur „.“ – der Pfad zum Ordner also relativ.
		host.screenshots = here.relativize(target);
		assertFalse(host.screenshots.isAbsolute());
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		click(ui, "save");
		frame(ui);
		click(ui, "saveFolder");
		frame(ui);
		assertEquals(1, host.opened.size());
		assertTrue(host.opened.get(0).isAbsolute(), "der Baum bekommt einen festen Pfad: " + host.opened.get(0));
		assertEquals(target, host.opened.get(0));
		click(ui, "saveDo");
		frame(ui);
		assertEquals(I18n.tr("reels.toast.saved"), ui.testToast());
		assertTrue(Files.isRegularFile(target.resolve(ReelsExport.fileName(p.id, 1))));
		ui.dispose();
	}

	@Test
	void escClosesTheSheetButNotThePhone() {
		post("Schaut mal!", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		click(ui, "save");
		frame(ui);
		// Die Dreh-Taste lässt das Blatt offen.
		assertTrue(ui.inputKey('R', UiKey.NONE, false));
		assertEquals(PhoneHold.PORTRAIT, ui.testHold());
		assertEquals(PhoneUi.Sheet.SAVE, ui.testSheet());
		frame(ui);
		spot(ui, "saveDo");
		assertTrue(ui.inputKey(0, UiKey.ESCAPE, false), "Esc ist verbraucht");
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
		assertFalse(ui.isClosing(), "das Handy bleibt draußen");
		assertEquals(PhoneUi.View.FEED, ui.testView());
		assertFalse(ui.inputKey(0, UiKey.ESCAPE, false), "erst das nächste Esc gehört dem Bildschirm der Version");
		// Die Handy-Taste steckt das Handy auch mit offenem Blatt weg.
		frame(ui);
		click(ui, "save");
		assertTrue(ui.inputKey('K', UiKey.NONE, false));
		assertTrue(ui.isClosing());
		ui.dispose();
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), "weggesteckt: kein Blatt mehr");
	}

	@Test
	void wheelAndArrowsDoNotPageWhileASheetIsOpen() {
		post("Älter", host.name, 5, false);
		post("Neuer", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		for (PhoneUi.Sheet sheet : new PhoneUi.Sheet[]{PhoneUi.Sheet.SAVE, PhoneUi.Sheet.FILTER, PhoneUi.Sheet.STREAK}) {
			ui.openSheet(sheet);
			assertEquals(sheet, ui.testSheet());
			frame(ui);
			assertTrue(ui.inputScroll(0, 0, -1), "das Rad gehört trotzdem dem Handy");
			ui.inputScroll(0, 0, -3);
			if (sheet != PhoneUi.Sheet.FILTER) {
				assertTrue(ui.inputKey(0, UiKey.DOWN, false));
				assertTrue(ui.inputKey(0, UiKey.RIGHT, false));
			}
			assertTrue(ui.inputKey('X', UiKey.NONE, false), "auch andere Tasten sind verbraucht");
			assertEquals(0, ui.testPage(), sheet + ": nichts blättert");
			assertEquals(sheet, ui.testSheet());
			ui.closeSheet();
			frame(ui);
		}
		ui.inputScroll(0, 0, -1);
		assertEquals(1, ui.testPage(), "ohne Blatt blättert das Rad wieder");
		ui.dispose();
	}

	@Test
	void cameraHomeAndCommentsCloseTheSheet() {
		post("Schaut mal!", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.PORTRAIT);
		ui.openSheet(PhoneUi.Sheet.SAVE);
		ui.home();
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), "„Feed“ schließt das Blatt");
		ui.openSheet(PhoneUi.Sheet.FILTER);
		ui.openComments();
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
		assertEquals(PhoneUi.View.COMMENTS, ui.testView());
		// Über den Kommentaren geht kein Blatt auf.
		ui.openSheet(PhoneUi.Sheet.SAVE);
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
		ui.closeComments();
		ui.openSheet(PhoneUi.Sheet.STREAK);
		assertEquals(PhoneUi.Sheet.STREAK, ui.testSheet());
		ui.openCamera();
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
		assertEquals(PhoneUi.View.CAMERA, ui.testView());
		ui.openSheet(PhoneUi.Sheet.SAVE);
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), "in der Kamera gibt es keine Blätter");
		ui.dispose();
	}

	@Test
	void savingWorksInASecondGameWindow() throws IOException {
		ReelsPost p = postWithPhoto(160, 90, 1);
		backend.flush();
		byte[] before = Files.readAllBytes(tmp.resolve("feed.json"));
		reels = Reels.forTest(modules, secondWindow(backend), DIRECT, UTC);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		click(ui, "save");
		frame(ui);
		click(ui, "saveDo");
		frame(ui);
		assertEquals(I18n.tr("reels.toast.saved"), ui.testToast());
		assertTrue(Files.isRegularFile(host.screenshots.resolve(ReelsExport.fileName(p.id, 1))));
		ui.dispose();
		assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(tmp.resolve("feed.json"))), "feed.json bleibt, wie sie war");
	}

	// --- B: Marken am Post (Shader, Instanz, Version) ---

	@Test
	void shutterCapturesMetaAndThePostCarriesIt() {
		host.meta = new ReelsMeta("26.2", "fabric", "BSL_v8.2.09", "All the Mods 10");
		PhoneUi ui = camera(PhoneHold.LANDSCAPE);
		shoot(ui);
		// Was nach dem Auslösen gilt, zählt nicht mehr: festgehalten wird beim Auslösen.
		host.meta = new ReelsMeta("1.21.4", "quilt", "Anderer", null);
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		ReelsPost top = backend.feed().get(0);
		assertNotNull(top.meta, "der Post trägt die Angaben");
		assertEquals("26.2", top.meta.mc);
		assertEquals("fabric", top.meta.loader);
		assertEquals("BSL_v8.2.09", top.meta.shader);
		assertEquals("All the Mods 10", top.meta.pack);
		ui.dispose();
		ReelsPost read = new LocalReelsBackend(tmp, UTC).feed().get(0);
		assertEquals("BSL_v8.2.09", read.meta.shader, "und sie sind gesichert");
		assertEquals("All the Mods 10", read.meta.pack);
	}

	@Test
	void retryTakesFreshMeta() {
		host.meta = new ReelsMeta("26.2", "fabric", "Erster Shader", null);
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		shoot(ui);
		frame(ui);
		click(ui, "composeRetry");
		frame(ui);
		// „Nochmal“ nimmt ein neues Foto auf – und mit ihm neue Angaben (der Shader ist jetzt aus).
		host.meta = new ReelsMeta("26.2", "fabric", null, "Mein Pack");
		shoot(ui);
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		ReelsPost top = backend.feed().get(0);
		assertNull(top.meta.shader);
		assertEquals("Mein Pack", top.meta.pack);
		// Ohne Angaben vom Baum hat der Post kein „meta“.
		host.meta = null;
		frame(ui);
		click(ui, "camera");
		frame(ui);
		shoot(ui);
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		assertNull(backend.feed().get(0).meta);
		ui.dispose();
	}

	@Test
	void chipsShowOnlyKnownValues() {
		host.meta = new ReelsMeta("26.2", "fabric", null, null);
		postMeta("Nur Shader", host.name, 5, new ReelsMeta("26.2", "fabric", "BSL", null));
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		spot(ui, "metaShader");
		assertNull(ui.testSpot("metaPack"), "keine Instanz bekannt: keine Marke");
		spot(ui, "metaVersion");
		assertTrue(c.strings().contains("BSL") && c.strings().contains("26.2 · Fabric"), c.strings().toString());
		ui.dispose();

		postMeta("Nur Instanz", host.name, 4, new ReelsMeta(null, null, null, "Mein Pack"));
		ui = ui(PhoneHold.LANDSCAPE);
		c = frame(ui);
		assertNull(ui.testSpot("metaShader"));
		spot(ui, "metaPack");
		assertNull(ui.testSpot("metaVersion"), "Version unbekannt: keine Marke");
		assertTrue(c.strings().contains("Mein Pack"));
		ui.dispose();

		postMeta("Nur der Loader", host.name, 3, new ReelsMeta(null, "neoforge", "BSL", null));
		ui = ui(PhoneHold.LANDSCAPE);
		c = frame(ui);
		assertTrue(c.strings().contains("NeoForge"), "nur der Teil, den es gibt: " + c.strings());
		ui.dispose();

		// Alte Posts ohne Angaben: keine Marke.
		post("Alt", host.name, 2, false);
		for (PhoneHold hold : PhoneHold.values()) {
			ui = ui(hold);
			frame(ui);
			for (String s : new String[]{"metaShader", "metaPack", "metaVersion"}) assertNull(ui.testSpot(s), hold + " " + s);
			ui.dispose();
		}
	}

	@Test
	void versionChipAloneIsNotDrawn() {
		host.meta = new ReelsMeta("26.2", "fabric", null, null);
		post("Derselbe Text", host.name, 5, false);
		postMeta("Derselbe Text", host.name, 2, new ReelsMeta("26.2", "fabric", null, null));
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = ui(hold);
			RecCanvas with = frame(ui);
			assertNotNull(backend.feed().get(ui.testPage()).meta);
			for (String s : new String[]{"metaShader", "metaPack", "metaVersion"}) assertNull(ui.testSpot(s), hold + " " + s);
			assertFalse(with.shows("26.2"), "allein stünde auf jedem Post dasselbe: " + with.strings());
			// Der Block sieht aus wie bei einem Post ohne Angaben: der Name steht auf derselben Höhe.
			ui.inputScroll(0, 0, -1);
			RecCanvas without = frame(ui);
			assertNull(backend.feed().get(ui.testPage()).meta);
			assertEquals(textOf(without, host.name).y, textOf(with, host.name).y, 1e-6, hold + ": gleiche Höhe");
			assertEquals(textOf(without, "Derselbe Text").y, textOf(with, "Derselbe Text").y, 1e-6);
			ui.dispose();
		}
	}

	@Test
	void versionChipShowsWhenItDiffersFromTheRunningGame() {
		host.meta = new ReelsMeta("26.2", "fabric", null, null);
		postMeta("Andere Version", host.name, 5, new ReelsMeta("1.21.4", "fabric", null, null));
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		spot(ui, "metaVersion");
		assertTrue(c.strings().contains("1.21.4 · Fabric"), c.strings().toString());
		ui.dispose();
		postMeta("Anderer Loader", host.name, 4, new ReelsMeta("26.2", "quilt", null, null));
		ui = ui(PhoneHold.PORTRAIT);
		c = frame(ui);
		spot(ui, "metaVersion");
		assertTrue(c.strings().contains("26.2 · Quilt"), c.strings().toString());
		ui.dispose();
		// Kennt das Spiel die eigene Version nicht, gilt nichts als abweichend.
		host.meta = null;
		ui = ui(PhoneHold.LANDSCAPE);
		frame(ui);
		assertNull(ui.testSpot("metaVersion"));
		ui.dispose();
	}

	@Test
	void chipsRunShaderInstanceVersion() {
		postMeta("Alle drei", host.name, 2, new ReelsMeta("26.2", "fabric", "BSL", "Mein Pack"));
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		PhoneLayout l = PhoneLayout.of(host.w, host.h, PhoneHold.LANDSCAPE);
		int[] shader = spot(ui, "metaShader"), pack = spot(ui, "metaPack"), version = spot(ui, "metaVersion");
		assertEquals(l.screenX + Math.round(16 * l.q), shader[0], "die erste Marke steht am linken Rand des Textblocks");
		int gap = Math.round(6 * l.q);
		assertEquals(shader[0] + shader[2] + gap, pack[0], "Shader, dann Instanz");
		assertEquals(pack[0] + pack[2] + gap, version[0], "dann die Version");
		assertEquals(shader[1], pack[1]);
		assertEquals(shader[1], version[1]);
		// Die Marken stehen unter dem Text und enden an der Kante, an der der Block immer endet.
		RecCanvas.Text caption = textOf(c, "Alle drei");
		assertTrue(shader[1] >= caption.y + caption.h, "unter dem Text");
		assertEquals(l.screenY + l.screenH - Math.round(14 * l.q), shader[1] + shader[3], "untere Kante wie bisher");
		assertEquals(174, version[2], "„26.2 · Fabric“ ist 174 Pixel breit");
		assertEquals(24, version[3]);
		// Dunkles Feld, blasser Text, Symbol vor dem Namen.
		assertEquals(FeedView.CHIP, fillAt(c, shader).argb);
		RecCanvas.Text name = textOf(c, "BSL");
		assertEquals(FeedView.TEXT_SOFT, name.argb);
		assertTrue(name.x >= shader[0] + PhoneIcons.width(PhoneIcons.SUN, (int) name.scale), "Platz für das Symbol vor dem Namen");
		ui.dispose();
	}

	@Test
	void chipsUseOneRowInLandscapeAndUpToTwoInPortrait() {
		postMeta("Lang", host.name, 2, new ReelsMeta("1.21.4", "neoforge", chars(20), chars(18)));
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		frame(ui);
		int[] shader = spot(ui, "metaShader"), pack = spot(ui, "metaPack"), version = spot(ui, "metaVersion");
		assertTrue(shader[1] == pack[1] && pack[1] == version[1], "quer: eine Zeile");
		ui.dispose();

		ui = ui(PhoneHold.PORTRAIT);
		frame(ui);
		shader = spot(ui, "metaShader");
		pack = spot(ui, "metaPack");
		PhoneLayout l = PhoneLayout.of(host.w, host.h, PhoneHold.PORTRAIT);
		assertEquals(shader[1] + shader[3] + Math.round(4 * l.q), pack[1], "hochkant: die Instanz steht in der zweiten Zeile");
		assertEquals(shader[0], pack[0], "beide beginnen links");
		assertNull(ui.testSpot("metaVersion"), "die Version bekommt nie eine dritte Zeile");
		assertEquals(22, shader[3]);
		assertTrue(pack[1] + pack[3] <= spot(ui, "home")[1], "über der Leiste");
		ui.dispose();

		// Kurze Namen passen auch hochkant in eine Zeile.
		postMeta("Kurz", host.name, 1, new ReelsMeta("26.2", "fabric", "BSL", "Pack"));
		ui = ui(PhoneHold.PORTRAIT);
		frame(ui);
		assertEquals(spot(ui, "metaShader")[1], spot(ui, "metaPack")[1]);
		ui.dispose();
	}

	@Test
	void packChipSurvivesALongShaderNameInPortrait() {
		postMeta("Lang", host.name, 2, new ReelsMeta("26.2", "fabric", chars(40), "All the Mods 10"));
		PhoneUi ui = ui(PhoneHold.PORTRAIT);
		RecCanvas c = frame(ui);
		int[] shader = spot(ui, "metaShader"), pack = spot(ui, "metaPack");
		assertTrue(pack[1] > shader[1], "die Instanz steht in ihrer eigenen Zeile");
		assertTrue(c.strings().contains("All the Mods 10"), "und ist ganz zu lesen: " + c.strings());
		PhoneLayout l = PhoneLayout.of(host.w, host.h, PhoneHold.PORTRAIT);
		int width = l.screenW - Math.round(14 * l.q) - Math.round(84 * l.q);
		assertTrue(shader[2] <= width, "der Shader-Name ist auf die Spalte gekürzt");
		boolean cut = false;
		for (String s : c.strings()) if (s.startsWith("abcdef") && s.endsWith("…")) cut = true;
		assertTrue(cut, "gekürzt mit „…“: " + c.strings());
		ui.dispose();
	}

	@Test
	void versionChipIsDroppedBeforeAnythingIsClipped() {
		String shader = chars(28), pack = new StringBuilder(chars(28)).reverse().toString();
		postMeta("Zwei lange Namen", host.name, 2, new ReelsMeta("1.21.4", "neoforge", shader, pack));
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		assertEquals(373, spot(ui, "metaShader")[2], "28 Zeichen mit Symbol");
		spot(ui, "metaPack");
		assertNull(ui.testSpot("metaVersion"), "die Version passt nicht mehr dahinter und fällt weg");
		assertTrue(c.strings().contains(shader) && c.strings().contains(pack), "die Namen bleiben ganz: " + c.strings());
		ui.dispose();
		// Mit etwas kürzeren Namen passen alle drei.
		postMeta("Kürzer", host.name, 1, new ReelsMeta("1.21.4", "neoforge", chars(20), chars(20)));
		ui = ui(PhoneHold.LANDSCAPE);
		frame(ui);
		spot(ui, "metaVersion");
		ui.dispose();
	}

	@Test
	void longNamesAreClippedInsideTheInfoColumn() {
		postMeta("Sehr lang", host.name, 2, new ReelsMeta("26.2", "fabric", chars(40), chars(64)));
		for (PhoneHold hold : PhoneHold.values()) {
			boolean land = hold == PhoneHold.LANDSCAPE;
			PhoneLayout l = PhoneLayout.of(host.w, host.h, hold);
			int left = l.screenX + Math.round((land ? 16 : 14) * l.q);
			int right = l.screenX + l.screenW - Math.round((land ? 120 : 84) * l.q);
			PhoneUi ui = ui(hold);
			RecCanvas c = frame(ui);
			for (String s : new String[]{"metaShader", "metaPack"}) {
				int[] r = spot(ui, s);
				assertTrue(r[0] >= left && r[0] + r[2] <= right, hold + " " + s + " in der Spalte " + left + "…" + right + ": " + r[0] + "+" + r[2]);
			}
			// Höchstens 28 Zeichen je Name, dann „…“.
			int shown = 0;
			for (RecCanvas.Text t : c.texts) {
				if (!t.text.startsWith("abc") || !t.text.endsWith("…")) continue;
				shown++;
				assertTrue(t.text.length() <= 28, hold + ": " + t.text);
				assertTrue(t.x >= left && t.x + t.w <= right, hold + ": Text in der Spalte: " + t);
			}
			assertEquals(2, shown, hold + ": beide Namen gekürzt: " + c.strings());
			ui.dispose();
		}
	}

	@Test
	void chipsNeverTouchTheRightColumn() {
		host.meta = new ReelsMeta("26.2", "fabric", null, null);
		postMeta("Sehr lang", host.name, 3, new ReelsMeta("1.21.4", "neoforge", chars(40), chars(64)));
		postMeta("Mittel", host.name, 2, new ReelsMeta("1.21.4", "neoforge", chars(20), chars(20)));
		postMeta("Kurz", host.name, 1, new ReelsMeta("1.21.4", "neoforge", "BSL", "Pack"));
		int[][] windows = {{1920, 1080}, {1280, 720}, {854, 480}, {1024, 768}, {800, 1000}};
		for (int[] win : windows) {
			for (PhoneHold hold : PhoneHold.values()) {
				host.size(win[0], win[1], 2);
				PhoneUi ui = ui(hold);
				for (int page = 0; page < 3; page++) {
					frame(ui);
					String tag = hold + " " + win[0] + "x" + win[1] + " Seite " + page;
					int[] heart = spot(ui, "heart");
					int[] screen = spot(ui, "screen");
					int seen = 0;
					for (String s : new String[]{"metaShader", "metaPack", "metaVersion"}) {
						int[] r = ui.testSpot(s);
						if (r == null) continue;
						seen++;
						assertTrue(r[0] + r[2] < heart[0], tag + ": " + s + " endet vor der rechten Spalte");
						assertTrue(inside(r, screen), tag + ": " + s + " auf dem Bildschirm");
						for (String other : new String[]{"heart", "comment", "save", "camera", "rotate", "home", "filterChip", "pos"}) {
							int[] o = ui.testSpot(other);
							if (o != null) assertFalse(overlap(r, o), tag + ": " + s + " überlappt " + other);
						}
					}
					assertTrue(seen >= 1, tag + ": mindestens eine Marke");
					ui.inputScroll(0, 0, -1);
				}
				ui.dispose();
			}
		}
	}

	// --- C: Filter und Reihenfolge ---

	@Test
	void filterChipSitsBesideThePageChipInLandscapeAndBelowTheTitleInPortrait() {
		post("Älter", "Rotkaeppchen", 5, false);
		post("Neuer", host.name, 2, false);
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		int[] pos = spot(ui, "pos"), chip = spot(ui, "filterChip");
		String label = I18n.tr("setting.mcReels.feedFilter.all") + " · " + I18n.tr("setting.mcReels.feedSort.newest");
		assertEquals("All · New", label);
		RecCanvas.Text text = textOf(c, label);
		assertNotNull(text, "die Marke nennt Filter und Reihenfolge: " + c.strings());
		RecCanvas.Text title = textOf(c, I18n.tr("reels.tab"));
		assertTrue(chip[0] + chip[2] > pos[0] + pos[2] && text.x >= pos[0] + pos[2], "quer: rechts neben der Seitenzahl");
		assertEquals(pos[1], Math.round(text.y) - Math.max(1, Math.round(3 * PhoneLayout.of(host.w, host.h, PhoneHold.LANDSCAPE).q)),
				"auf derselben Höhe wie die Seitenzahl");
		assertTrue(chip[0] + chip[2] <= title.x, "links vom Titel");
		assertTrue(inside(chip, spot(ui, "screen")));
		// Die sichtbare Marke: dunkles Feld wie die Seitenzahl, 24 Pixel hoch, mit dem kleinen Pfeil.
		RecCanvas.Fill box = null;
		for (RecCanvas.Fill f : c.fills) {
			if (f.argb == FeedView.CHIP && f.x1 < text.x && f.x2 > text.x + text.w && f.y1 <= text.y && f.y2 >= text.y + text.h) box = f;
		}
		assertNotNull(box);
		assertEquals(24.0, box.y2 - box.y1, 0.01);
		assertTrue(box.x2 - box.x1 > text.w + 2 * 10, "Platz für den Pfeil hinter dem Text");
		ui.dispose();

		ui = ui(PhoneHold.PORTRAIT);
		c = frame(ui);
		pos = spot(ui, "pos");
		chip = spot(ui, "filterChip");
		text = textOf(c, label);
		title = textOf(c, I18n.tr("reels.tab"));
		assertTrue(text.y >= title.y + title.h, "hochkant: unter dem Titel");
		assertTrue(text.y >= pos[1] + pos[3], "und unter der Seitenzahl");
		assertTrue(chip[1] >= title.y + title.h, "auch die Klickfläche beginnt erst unter der Titelzeile");
		assertEquals(pos[0], Math.round(text.x) - Math.round(8 * PhoneLayout.of(host.w, host.h, PhoneHold.PORTRAIT).q), "linksbündig mit der Seitenzahl");
		assertTrue(inside(chip, spot(ui, "screen")));
		assertFalse(overlap(chip, spot(ui, "streak")) || overlap(chip, spot(ui, "heart")));
		ui.dispose();
	}

	@Test
	void chipHitAreaIsAtLeastFortyPixelsHigh() {
		post("Neuer", host.name, 2, false);
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = feed(hold);
			int[] chip = spot(ui, "filterChip");
			assertTrue(chip[3] >= 40, hold + ": " + chip[3] + " Pixel hoch");
			assertTrue(chip[2] >= 40, hold + ": " + chip[2] + " Pixel breit");
			ui.dispose();
		}
		// Auch mit dem längsten Text (Polnisch) passt die Marke in beide Haltungen.
		I18n.use("pl");
		for (PhoneHold hold : PhoneHold.values()) {
			modules.reels.feedSort.set(FeedSort.POPULAR);
			PhoneUi ui = ui(hold);
			RecCanvas c = frame(ui);
			String label = I18n.tr("setting.mcReels.feedFilter.all") + " · " + I18n.tr("setting.mcReels.feedSort.popular");
			assertEquals(21, label.length());
			RecCanvas.Text t = textOf(c, label);
			assertNotNull(t, hold + ": ganz zu lesen: " + c.strings());
			assertEquals(PhoneText.scale(22, PhoneLayout.of(host.w, host.h, hold).q), t.scale, 1e-6, hold + ": in voller Größe");
			ui.dispose();
		}
	}

	@Test
	void chipOpensTheSheetAndAChoiceAppliesAtOnce() {
		post("Mein altes", host.name, 50, false);
		post("Fremdes", "Rotkaeppchen", 26, false);
		post("Mein neues", host.name, 2, false);
		for (PhoneHold hold : PhoneHold.values()) {
			modules.reels.feedFilter.set(FeedFilter.ALL);
			modules.reels.feedSort.set(FeedSort.NEWEST);
			PhoneUi ui = feed(hold);
			int clicks = host.clicks;
			click(ui, "filterChip");
			assertEquals(PhoneUi.Sheet.FILTER, ui.testSheet(), hold.toString());
			assertEquals(clicks + 1, host.clicks);
			RecCanvas c = frame(ui);
			for (String s : new String[]{"filterSheet", "filterBack", "filterClose", "filterAll", "filterFriends", "filterMine",
					"sortNewest", "sortPopular"}) {
				spot(ui, s);
			}
			assertTrue(c.strings().contains(I18n.tr("reels.filter.title")) && c.strings().contains(I18n.tr("reels.filter.show"))
					&& c.strings().contains(I18n.tr("reels.filter.sort")), c.strings().toString());
			assertTrue(c.strings().contains("Friends") && c.strings().contains("Me") && c.strings().contains("Popular"), c.strings().toString());
			// Das Gewählte ist rot, die anderen grau; kein Segment ist schmaler als 60 Pixel.
			assertEquals(FeedView.RED_BUTTON, fillAt(c, spot(ui, "filterAll")).argb);
			assertEquals(ComposeView.BUTTON_GREY, fillAt(c, spot(ui, "filterMine")).argb);
			assertEquals(FeedView.RED_BUTTON, fillAt(c, spot(ui, "sortNewest")).argb);
			for (String s : new String[]{"filterAll", "filterFriends", "filterMine", "sortNewest", "sortPopular"}) {
				assertTrue(spot(ui, s)[2] >= 60 && spot(ui, s)[3] >= 40, hold + " " + s + ": " + spot(ui, s)[2] + "x" + spot(ui, s)[3]);
				assertTrue(inside(spot(ui, s), spot(ui, "filterSheet")), hold + " " + s);
			}
			int[] all = spot(ui, "filterAll"), friends = spot(ui, "filterFriends"), mine = spot(ui, "filterMine");
			assertTrue(all[0] + all[2] < friends[0] && friends[0] + friends[2] < mine[0], "Alle | Freunde | Ich");
			if (hold == PhoneHold.LANDSCAPE) {
				assertEquals(all[1], spot(ui, "sortNewest")[1], "quer: beide Gruppen in einer Reihe");
				assertTrue(mine[0] + mine[2] < spot(ui, "sortNewest")[0]);
			} else {
				assertTrue(spot(ui, "sortNewest")[1] >= all[1] + all[3], "hochkant: Sortieren darunter");
			}

			click(ui, "filterMine");
			assertEquals(FeedFilter.MINE, ui.testFilter());
			assertEquals(FeedFilter.MINE, modules.reels.feedFilter.get(), "wird gemerkt");
			assertEquals(PhoneUi.Sheet.FILTER, ui.testSheet(), "das Blatt bleibt offen");
			c = frame(ui);
			assertTrue(c.shows("1 / 2"), "gilt sofort: " + c.strings());
			assertEquals(FeedView.RED_BUTTON, fillAt(c, spot(ui, "filterMine")).argb);
			assertEquals(ComposeView.BUTTON_GREY, fillAt(c, spot(ui, "filterAll")).argb);
			click(ui, "sortPopular");
			assertEquals(FeedSort.POPULAR, ui.testSort());
			assertEquals(FeedSort.POPULAR, modules.reels.feedSort.get());
			c = frame(ui);
			assertTrue(c.strings().contains("Me · Popular"), "die Marke im Kopf zeigt die Wahl: " + c.strings());
			click(ui, "filterAll");
			assertEquals(FeedFilter.ALL, ui.testFilter());
			frame(ui);
			click(ui, "filterClose");
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
			frame(ui);
			click(ui, "filterChip");
			frame(ui);
			click(ui, "filterBack");
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), "Klick aufs Foto schließt");
			ui.dispose();
		}
	}

	@Test
	void pagesAndPositionFollowTheFilteredList() {
		post("Mein altes", host.name, 50, false);
		post("Fremdes", "Rotkaeppchen", 26, false);
		// Der eigene Name zählt ohne Groß-/Kleinschreibung.
		post("Mein neues", host.name.toLowerCase(java.util.Locale.ROOT), 2, false);
		PhoneUi ui = feed(PhoneHold.PORTRAIT);
		ui.setFilter(FeedFilter.MINE);
		RecCanvas c = frame(ui);
		assertTrue(c.shows("1 / 2") && c.shows("Mein neues"), c.strings().toString());
		ui.inputScroll(0, 0, -1);
		c = frame(ui);
		assertEquals(1, ui.testPage());
		assertTrue(c.shows("2 / 2") && c.shows("Mein altes"), "das fremde Foto wird übersprungen: " + c.strings());
		ui.inputScroll(0, 0, -1);
		assertEquals(1, ui.testPage(), "am Ende der gefilterten Liste ist Schluss");
		// Zurück zu „Alle“: das Foto im Bild bleibt, nur seine Seitenzahl ändert sich.
		ui.setFilter(FeedFilter.ALL);
		c = frame(ui);
		assertEquals(2, ui.testPage());
		assertTrue(c.shows("3 / 3") && c.shows("Mein altes"), c.strings().toString());
		// Die Streak zählt immer alle Posts, was auch immer der Filter zeigt.
		assertEquals(3, backend.streak(NOON));
		assertNotNull(textOf(c, "3"), "drei Tage in Folge, auch das fremde Foto zählt: " + c.strings());
		ui.dispose();
	}

	@Test
	void thePostInViewStaysWhenALikeReordersPopular() {
		post("Zweites", host.name, 5, false);
		post("Erstes", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		ui.setSort(FeedSort.POPULAR);
		RecCanvas c = frame(ui);
		assertTrue(c.shows("Erstes") && c.shows("1 / 2"), "ohne Likes: das neueste zuerst: " + c.strings());
		ui.inputScroll(0, 0, -1);
		c = frame(ui);
		assertTrue(c.shows("Zweites") && c.shows("2 / 2"));
		// Ein Like schiebt den Post nach vorn – das Foto im Bild bleibt, nur die Seitenzahl springt.
		click(ui, "heart");
		c = frame(ui);
		assertTrue(c.shows("Zweites"), "das Foto bleibt: " + c.strings());
		assertTrue(c.shows("1 / 2"), "es ist jetzt Seite 1: " + c.strings());
		assertEquals(0, ui.testPage());
		ui.inputScroll(0, 0, -1);
		assertTrue(frame(ui).shows("Erstes"));
		ui.dispose();
	}

	@Test
	void friendsFilterShowsTheHonestEmptyTextAndKeepsTheChip() {
		post("Von mir", host.name, 2, false);
		for (PhoneHold hold : PhoneHold.values()) {
			modules.reels.feedFilter.set(FeedFilter.ALL);
			PhoneUi ui = feed(hold);
			ui.setFilter(FeedFilter.FRIENDS);
			RecCanvas c = frame(ui);
			assertEquals(0, ui.pages(), hold.toString());
			assertTrue(c.strings().contains(I18n.tr("reels.empty.friends")), hold + ": " + c.strings());
			assertTrue(all(c).contains(I18n.tr("reels.empty.friends.hint")), "der Hinweis steht ganz da: " + c.strings());
			assertFalse(c.shows(I18n.tr("reels.empty")), "nicht „Noch keine Fotos“ – es gibt ja welche");
			assertFalse(all(c).contains("K:"), "ohne die Tasten-Zeile: " + c.strings());
			assertFalse(c.shows("Von mir"));
			// Die Marke bleibt, damit man zurückschalten kann – quer an der Stelle der Seitenzahl.
			int[] chip = spot(ui, "filterChip");
			assertNull(ui.testSpot("pos"));
			assertNull(ui.testSpot("heart"));
			assertTrue(c.strings().contains("Friends · New"), c.strings().toString());
			PhoneLayout l = PhoneLayout.of(host.w, host.h, hold);
			if (hold == PhoneHold.LANDSCAPE) assertEquals(l.screenX + Math.round(12 * l.q), textOf(c, "Friends · New").x - Math.round(8 * l.q), 1e-6);
			assertNotNull(spot(ui, "camera"), "die Kamera bleibt erreichbar");
			click(ui, "filterChip");
			assertEquals(PhoneUi.Sheet.FILTER, ui.testSheet());
			frame(ui);
			click(ui, "filterAll");
			assertTrue(frame(ui).shows("Von mir"));
			assertTrue(chip[3] > 0);
			ui.dispose();
		}
		// „Ich“ ohne eigene Fotos.
		host.name = "Niemand";
		modules.reels.feedFilter.set(FeedFilter.MINE);
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		assertTrue(c.strings().contains(I18n.tr("reels.empty.mine")), c.strings().toString());
		assertTrue(all(c).contains(I18n.tr("reels.empty.hint")));
		spot(ui, "filterChip");
		ui.dispose();
	}

	@Test
	void friendPostsShowWhenTheHostKnowsFriends() {
		post("Von mir", host.name, 5, false);
		post("Von Rotkäppchen", "Rotkaeppchen", 3, false);
		post("Von Fremden", "Fremder", 2, false);
		host.friends = java.util.Arrays.asList("ROTKAEPPCHEN", "Steve_Baut");
		modules.reels.feedFilter.set(FeedFilter.FRIENDS);
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		assertTrue(c.shows("Von Rotkäppchen") && c.shows("1 / 1"), "Namen ohne Groß-/Kleinschreibung: " + c.strings());
		// Die Liste ändert sich, während das Handy draußen ist.
		host.friends = java.util.Arrays.asList("Fremder", "Rotkaeppchen");
		c = frame(ui);
		assertTrue(c.shows("Von Rotkäppchen") && c.shows("2 / 2"), "das Foto im Bild bleibt: " + c.strings());
		host.friends = new ArrayList<String>();
		c = frame(ui);
		assertTrue(c.strings().contains(I18n.tr("reels.empty.friends")));
		// Unbekannt (TRS Online hat die Liste nie geladen) ist wie leer.
		host.friends = null;
		assertTrue(frame(ui).strings().contains(I18n.tr("reels.empty.friends")));
		ui.dispose();
		// Unter „Alle“ wird die Liste gar nicht erst gelesen.
		modules.reels.feedFilter.set(FeedFilter.ALL);
		int calls = host.friendCalls;
		ui = ui(PhoneHold.LANDSCAPE);
		frame(ui);
		frame(ui);
		assertEquals(calls, host.friendCalls);
		ui.dispose();
	}

	@Test
	void emptyBackendFeedShowsNoChipAndTabDoesNothing() {
		modules.reels.feedFilter.set(FeedFilter.MINE);
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = ui(hold);
			RecCanvas c = frame(ui);
			assertNull(ui.testSpot("filterChip"), hold + ": ganz leerer Feed: keine Marke");
			assertTrue(c.shows(I18n.tr("reels.empty")), "und der Text wie immer: " + c.strings());
			assertTrue(all(c).contains("K:"), "mit der Tasten-Zeile");
			ui.inputKey(0, UiKey.TAB, false);
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
			ui.dispose();
		}
	}

	@Test
	void postingWhileFilteredToFriendsSwitchesBackToAll() {
		post("Alt", host.name, 30, false);
		modules.reels.feedFilter.set(FeedFilter.FRIENDS);
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		shoot(ui);
		for (char ch : "Neu".toCharArray()) ui.inputChar(ch);
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		assertEquals(FeedFilter.ALL, ui.testFilter(), "sonst wäre der neue Post nicht zu sehen");
		assertEquals(FeedFilter.ALL, modules.reels.feedFilter.get());
		assertEquals(0, ui.testPage());
		RecCanvas c = frame(ui);
		assertTrue(c.shows("Neu") && c.shows("1 / 2"), c.strings().toString());
		ui.dispose();
	}

	@Test
	void newPostIsInViewUnderPopularToo() {
		ReelsPost liked = post("Beliebt", host.name, 30, false);
		backend.toggleLike(liked.id);
		modules.reels.feedSort.set(FeedSort.POPULAR);
		modules.reels.feedFilter.set(FeedFilter.MINE);
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		shoot(ui);
		for (char ch : "Neu".toCharArray()) ui.inputChar(ch);
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		assertEquals(FeedSort.POPULAR, ui.testSort(), "die Reihenfolge bleibt");
		assertEquals(FeedFilter.MINE, ui.testFilter(), "unter „Ich“ ist der eigene Post ohnehin zu sehen");
		assertEquals(1, ui.testPage(), "ohne Likes steht der neue Post hinten – der Feed zeigt ihn trotzdem");
		RecCanvas c = frame(ui);
		assertTrue(c.shows("Neu") && c.shows("2 / 2"), c.strings().toString());
		ui.dispose();
	}

	@Test
	void tabOpensAndEscEnterTabCloseTheFilterSheet() {
		post("Schaut mal!", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		for (UiKey close : new UiKey[]{UiKey.ESCAPE, UiKey.ENTER, UiKey.TAB}) {
			assertTrue(ui.inputKey(0, UiKey.TAB, false));
			assertEquals(PhoneUi.Sheet.FILTER, ui.testSheet(), "Tab öffnet „Feed“");
			frame(ui);
			assertTrue(ui.inputKey(0, close, false), close + " ist verbraucht");
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), close + " schließt");
			assertEquals(PhoneUi.View.FEED, ui.testView(), close + " öffnet nicht die Kommentare");
			assertFalse(ui.isClosing());
			frame(ui);
		}
		ui.dispose();
	}

	@Test
	void arrowKeysChangeFilterAndSortInTheSheet() {
		post("Älter", host.name, 5, false);
		post("Neuer", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		ui.inputKey(0, UiKey.TAB, false);
		ui.inputKey(0, UiKey.RIGHT, false);
		assertEquals(FeedFilter.FRIENDS, ui.testFilter());
		ui.inputKey(0, UiKey.RIGHT, false);
		assertEquals(FeedFilter.MINE, ui.testFilter());
		ui.inputKey(0, UiKey.RIGHT, false);
		assertEquals(FeedFilter.MINE, ui.testFilter(), "am Ende ist Schluss");
		ui.inputKey(0, UiKey.LEFT, false);
		assertEquals(FeedFilter.FRIENDS, ui.testFilter());
		ui.inputKey(0, UiKey.LEFT, false);
		ui.inputKey(0, UiKey.LEFT, false);
		assertEquals(FeedFilter.ALL, ui.testFilter());
		ui.inputKey(0, UiKey.DOWN, false);
		assertEquals(FeedSort.POPULAR, ui.testSort());
		ui.inputKey(0, UiKey.DOWN, false);
		assertEquals(FeedSort.POPULAR, ui.testSort());
		ui.inputKey(0, UiKey.UP, false);
		assertEquals(FeedSort.NEWEST, ui.testSort());
		assertEquals(0, ui.testPage(), "die Pfeile blättern dabei nicht");
		assertEquals(PhoneUi.Sheet.FILTER, ui.testSheet());
		assertEquals(FeedFilter.ALL, modules.reels.feedFilter.get());
		ui.dispose();
	}

	@Test
	void lastPhotoIgnoresTheFilter() throws IOException {
		postWithPhoto(160, 90, 5);
		String me = host.name;
		host.name = "Rotkaeppchen";
		postWithPhoto(90, 160, 1);
		host.name = me;
		modules.reels.feedFilter.set(FeedFilter.MINE);
		PhoneUi ui = feed(PhoneHold.PORTRAIT);
		frame(ui);
		RecCanvas c = frame(ui);
		assertTrue(c.shows("Foto 160x90") && c.shows("1 / 1"), c.strings().toString());
		String own = photoTexture(c);
		// Das kleine Bild neben dem Auslöser ist das neueste Foto im ganzen Feed – der Filter zählt dort nicht.
		click(ui, "camera");
		frame(ui);
		frame(ui);
		c = frame(ui);
		String thumb = photoTexture(c);
		assertFalse(own.equals(thumb), "nicht das gefilterte eigene Foto");
		ui.keyPressed(0, UiKey.ESCAPE, false);
		ui.setFilter(FeedFilter.ALL);
		ui.pages();
		frame(ui);
		frame(ui);
		c = frame(ui);
		assertTrue(c.shows("Foto 160x90"), "das Foto im Bild bleibt beim Umschalten");
		ui.inputScroll(0, 0, 1);
		frame(ui);
		c = frame(ui);
		assertTrue(c.shows("Foto 90x160"));
		assertEquals(thumb, photoTexture(c), "das neueste Foto im Feed");
		ui.dispose();
	}

	@Test
	void filterIsRememberedByTheNextPhone() {
		post("Schaut mal!", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		assertEquals(FeedFilter.ALL, ui.testFilter());
		assertEquals(FeedSort.NEWEST, ui.testSort());
		ui.setFilter(FeedFilter.MINE);
		ui.setSort(FeedSort.POPULAR);
		ui.dispose();
		PhoneUi next = ui(PhoneHold.PORTRAIT);
		assertEquals(FeedFilter.MINE, next.testFilter());
		assertEquals(FeedSort.POPULAR, next.testSort());
		assertTrue(frame(next).strings().contains("Me · Popular"));
		next.dispose();
	}

	// --- E: Streak-Schutz ---

	private static final long DAY = 86400000L;

	/** Zwei Schutztage im November sind schon eingelöst (10. und 11.), nach einem Post am 9. */
	private void useBothProtections() {
		post("Am Neunten", host.name, 4 * 24, false);
		assertEquals(StreakGuard.Result.OK, backend.protect(false, NOON - 3 * DAY));
		assertEquals(StreakGuard.Result.OK, backend.protect(false, NOON - 2 * DAY));
	}

	/** Hat der Knopf an dieser Stelle den gelben Rahmen („Sicher? Nochmal tippen“)? */
	private static boolean asks(RecCanvas c, int[] r) {
		for (RecCanvas.Fill f : c.fills) {
			if (f.argb == FeedView.AMBER_LINE && Math.abs(f.x1 - r[0]) < 0.01 && Math.abs(f.y1 - r[1]) < 0.01
					&& Math.abs(f.x2 - r[0] - r[2]) < 0.01) return true;
		}
		return false;
	}

	/** Blatt „Streak“ öffnen und zeichnen. */
	private RecCanvas streakSheet(PhoneUi ui) {
		click(ui, "streak");
		assertEquals(PhoneUi.Sheet.STREAK, ui.testSheet());
		return frame(ui);
	}

	@Test
	void flameOpensTheStreakSheetInTheFeedOnly() {
		post("Heute", host.name, 2, false);
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = feed(hold);
			int clicks = host.clicks;
			RecCanvas c = streakSheet(ui);
			assertEquals(clicks + 1, host.clicks, "Klick-Ton");
			for (String s : new String[]{"streakSheet", "streakBack", "streakClose", "streakPause"}) spot(ui, s);
			assertNull(ui.testSpot("streakRescue"), "nichts zu retten: kein zweiter Knopf");
			assertTrue(c.strings().contains(I18n.tr("reels.streak.title")), c.strings().toString());
			assertTrue(c.strings().contains(I18n.tr("reels.compose.streak.same.one", 1)), "„Streak: 1 day“: " + c.strings());
			assertTrue(all(c).contains(I18n.tr("reels.streak.state.posted")), c.strings().toString());
			assertTrue(all(c).contains(I18n.tr("reels.streak.left", 2, 2)), c.strings().toString());
			assertTrue(all(c).contains(I18n.tr("reels.streak.rule")), c.strings().toString());
			// Niedriger als das Kommentar-Blatt.
			PhoneLayout l = PhoneLayout.of(host.w, host.h, hold);
			assertTrue(spot(ui, "streakSheet")[3] < Math.round((hold == PhoneHold.LANDSCAPE ? 290 : 400) * l.q), hold.toString());
			click(ui, "streakClose");
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
			frame(ui);
			click(ui, "streak");
			frame(ui);
			click(ui, "streakBack");
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());

			// Über den Kommentaren und in der Kamera ist die Flamme keine Klickfläche.
			frame(ui);
			click(ui, "comment");
			frame(ui);
			click(ui, "streak");
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), hold + ": Kommentare");
			ui.home();
			frame(ui);
			click(ui, "camera");
			frame(ui);
			int[] flame = spot(ui, "streak");
			ui.inputClick((flame[0] + flame[2] / 2.0) / host.gui, (flame[1] + flame[3] / 2.0) / host.gui, 0);
			assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), hold + ": Kamera");
			assertEquals(PhoneUi.View.CAMERA, ui.testView());
			ui.dispose();
		}
	}

	@Test
	void flameHitAreaIsAtLeastFortyPixelsInBothHolds() {
		post("Heute", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		assertArrayEquals(new int[]{975, 0, 68, 45}, onPhone(ui, "streak"), "quer: endet genau an der Klickfläche des Herzens");
		ui.dispose();
		ui = feed(PhoneHold.PORTRAIT);
		assertArrayEquals(new int[]{330, 0, 53, 44}, onPhone(ui, "streak"));
		ui.dispose();
		for (PhoneHold hold : PhoneHold.values()) {
			ui = feed(hold);
			int[] flame = spot(ui, "streak");
			assertTrue(flame[2] >= 40 && flame[3] >= 40, hold + ": " + flame[2] + "x" + flame[3]);
			assertTrue(inside(flame, spot(ui, "screen")), hold.toString());
			// In der Kamera bleibt es die schmale gemerkte Stelle.
			click(ui, "camera");
			frame(ui);
			assertTrue(spot(ui, "streak")[2] < flame[2], hold + ": Kamera ohne Klickfläche");
			ui.dispose();
		}
	}

	@Test
	void flameAndHeartHitAreasDoNotOverlap() {
		post("Heute", host.name, 2, false);
		int[][] windows = {{1920, 1080}, {1280, 720}};
		for (int[] win : windows) {
			for (PhoneHold hold : PhoneHold.values()) {
				host.size(win[0], win[1], 2);
				PhoneUi ui = feed(hold);
				String tag = hold + " " + win[0] + "x" + win[1];
				int[] flame = spot(ui, "streak"), heart = spot(ui, "heart");
				assertFalse(overlap(flame, heart), tag + ": Flamme " + flame[1] + "+" + flame[3] + ", Herz ab " + heart[1]);
				assertFalse(overlap(flame, spot(ui, "filterChip")), tag);
				// Ein Klick auf die Flamme liked nicht, ein Klick aufs Herz öffnet kein Blatt.
				click(ui, "heart");
				assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), tag);
				assertTrue(backend.feed().get(0).likedByMe, tag);
				click(ui, "heart");
				click(ui, "streak");
				assertEquals(PhoneUi.Sheet.STREAK, ui.testSheet(), tag);
				assertFalse(backend.feed().get(0).likedByMe, tag);
				ui.dispose();
			}
		}
	}

	@Test
	void firstTapArmsAndSecondTapUsesTheProtection() throws IOException {
		post("Vorgestern", host.name, 50, false);
		post("Gestern", host.name, 26, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		RecCanvas c = streakSheet(ui);
		assertTrue(all(c).contains(I18n.tr("reels.streak.state.open")), c.strings().toString());
		assertTrue(c.strings().contains(I18n.tr("reels.streak.pause")));
		assertEquals(FeedView.RED_BUTTON, fillAt(c, spot(ui, "streakPause")).argb, "möglich: rot");
		PhoneLayout l = PhoneLayout.of(host.w, host.h, PhoneHold.LANDSCAPE);
		assertEquals(l.screenW - 2 * Math.round(14 * l.q), spot(ui, "streakPause")[2], "ohne „Gestern retten“ über die ganze Breite");
		assertTrue(spot(ui, "streakPause")[3] >= 40);

		// Erster Druck: der Knopf fragt nur nach.
		click(ui, "streakPause");
		assertFalse(ui.testStreakGuard().todayFrozen, "noch nichts eingelöst");
		c = frame(ui);
		assertTrue(c.strings().contains(I18n.tr("reels.streak.confirm")), c.strings().toString());
		assertFalse(c.strings().contains(I18n.tr("reels.streak.pause")));
		assertTrue(asks(c, spot(ui, "streakPause")), "gelber Rahmen");
		assertEquals(PhoneUi.Sheet.STREAK, ui.testSheet());

		// Zweiter Druck nach der Wartezeit: eingelöst.
		ui.testTicker += PhoneUi.POST_GUARD_MS;
		frame(ui);
		click(ui, "streakPause");
		StreakGuard g = ui.testStreakGuard();
		assertTrue(g.todayFrozen, "heute ist geschützt");
		assertEquals(1, g.leftThisMonth);
		assertEquals(2, g.streak, "ein geschützter Tag zählt nicht mit, hält aber die Streak");
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet(), "das Blatt schließt");
		assertEquals(I18n.tr("reels.toast.streak.paused"), ui.testToast());
		frame(ui);
		// Wieder offen: pausiert, und kein zweites Mal möglich.
		ui.testTicker += PhoneUi.TOAST_MS + 1;
		frame(ui);
		c = streakSheet(ui);
		assertTrue(all(c).contains(I18n.tr("reels.streak.state.paused")), c.strings().toString());
		assertTrue(all(c).contains(I18n.tr("reels.streak.left", 1, 2)), c.strings().toString());
		assertEquals(ComposeView.BUTTON_GREY, fillAt(c, spot(ui, "streakPause")).argb);
		click(ui, "streakPause");
		ui.testTicker += PhoneUi.POST_GUARD_MS;
		frame(ui);
		click(ui, "streakPause");
		assertEquals(1, ui.testStreakGuard().leftThisMonth);
		ui.dispose();
		// Gesichert: nach dem Neustart ist der Tag noch geschützt.
		LocalReelsBackend read = new LocalReelsBackend(tmp, UTC);
		assertTrue(read.streakGuard(NOON).todayFrozen);
		assertEquals(2, read.streak(NOON + DAY), "morgen läuft die Streak noch");
	}

	@Test
	void doubleClickDoesNotUseAProtection() {
		post("Gestern", host.name, 26, false);
		PhoneUi ui = feed(PhoneHold.PORTRAIT);
		streakSheet(ui);
		click(ui, "streakPause");
		click(ui, "streakPause");
		assertFalse(ui.testStreakGuard().todayFrozen, "Doppelklick im selben Augenblick");
		ui.testTicker += PhoneUi.POST_GUARD_MS - 1;
		RecCanvas c = frame(ui);
		click(ui, "streakPause");
		assertFalse(ui.testStreakGuard().todayFrozen, "1 ms vor Ablauf noch nicht");
		assertEquals(PhoneUi.Sheet.STREAK, ui.testSheet());
		assertTrue(c.strings().contains(I18n.tr("reels.streak.confirm")), "der Knopf fragt weiter nach");
		assertEquals(2, ui.testStreakGuard().leftThisMonth);
		ui.testTicker += 1;
		frame(ui);
		click(ui, "streakPause");
		assertTrue(ui.testStreakGuard().todayFrozen);
		ui.dispose();
	}

	@Test
	void heldEnterDoesNotUseAProtection() {
		post("Gestern", host.name, 26, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		streakSheet(ui);
		// Enter drückt „Heute pausieren“ – und bleibt unten (Tastenwiederholung).
		host.down.add("key.keyboard.enter");
		assertTrue(ui.inputKey(0, UiKey.ENTER, false));
		RecCanvas c = frame(ui);
		assertTrue(c.strings().contains(I18n.tr("reels.streak.confirm")), "Enter fragt nach: " + c.strings());
		for (int i = 0; i < 4; i++) {
			ui.testTicker += PhoneUi.POST_GUARD_MS;
			frame(ui);
			ui.inputKey(0, UiKey.ENTER, false);
		}
		assertFalse(ui.testStreakGuard().todayFrozen, "gehaltenes Enter löst nichts ein – auch nicht nach der Wartezeit");
		assertEquals(PhoneUi.View.FEED, ui.testView());
		assertEquals(PhoneUi.Sheet.STREAK, ui.testSheet());
		// Losgelassen und neu gedrückt: jetzt gilt Enter als zweiter Druck.
		host.down.clear();
		frame(ui);
		ui.inputKey(0, UiKey.ENTER, false);
		assertTrue(ui.testStreakGuard().todayFrozen, "losgelassen und neu gedrückt: eingelöst");
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
		ui.dispose();
	}

	@Test
	void armingExpiresAfterThreeSeconds() {
		post("Gestern", host.name, 26, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		streakSheet(ui);
		click(ui, "streakPause");
		ui.testTicker += PhoneUi.STREAK_CONFIRM_MS - 1;
		assertTrue(frame(ui).strings().contains(I18n.tr("reels.streak.confirm")));
		ui.testTicker += 1;
		RecCanvas c = frame(ui);
		assertTrue(c.strings().contains(I18n.tr("reels.streak.pause")), "nach drei Sekunden steht der Knopf wieder normal da");
		assertFalse(asks(c, spot(ui, "streakPause")), "ohne gelben Rahmen");
		// Der nächste Druck ist wieder der erste.
		click(ui, "streakPause");
		assertFalse(ui.testStreakGuard().todayFrozen);
		assertTrue(frame(ui).strings().contains(I18n.tr("reels.streak.confirm")));
		// Auch das Schließen des Blatts nimmt die Nachfrage zurück.
		ui.closeSheet();
		ui.testTicker += PhoneUi.POST_GUARD_MS;
		frame(ui);
		streakSheet(ui);
		click(ui, "streakPause");
		assertFalse(ui.testStreakGuard().todayFrozen);
		ui.dispose();
	}

	@Test
	void pauseIsNotPossibleAfterPostingToday() {
		post("Gestern", host.name, 26, false);
		post("Heute", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.PORTRAIT);
		RecCanvas c = streakSheet(ui);
		assertTrue(all(c).contains(I18n.tr("reels.streak.state.posted")), c.strings().toString());
		assertEquals(ComposeView.BUTTON_GREY, fillAt(c, spot(ui, "streakPause")).argb, "grau");
		assertEquals(FeedView.TEXT_DIM, textOf(c, I18n.tr("reels.streak.pause")).argb, "blasse Schrift");
		click(ui, "streakPause");
		ui.inputKey(0, UiKey.ENTER, false);
		c = frame(ui);
		assertFalse(c.strings().contains(I18n.tr("reels.streak.confirm")), "der Knopf ruht");
		ui.testTicker += PhoneUi.POST_GUARD_MS;
		frame(ui);
		click(ui, "streakPause");
		ui.inputKey(0, UiKey.ENTER, false);
		assertFalse(ui.testStreakGuard().todayFrozen);
		assertEquals(2, ui.testStreakGuard().leftThisMonth);
		assertEquals(PhoneUi.Sheet.STREAK, ui.testSheet());
		ui.dispose();
	}

	@Test
	void rescueButtonAppearsOnlyTheDayAfterAMissedDay() {
		// Gestern und heute gepostet: nichts zu retten.
		post("Gestern", host.name, 26, false);
		post("Heute", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		streakSheet(ui);
		assertNull(ui.testSpot("streakRescue"));
		ui.dispose();

		// Vorgestern und davor gepostet, gestern nicht: „Gestern retten“ steht neben „Heute pausieren“.
		backend = new LocalReelsBackend(tmp.resolve("b"), UTC);
		reels = Reels.forTest(modules, backend, DIRECT, UTC);
		post("Vor drei Tagen", host.name, 74, false);
		post("Vorgestern", host.name, 50, false);
		for (PhoneHold hold : PhoneHold.values()) {
			ui = feed(hold);
			RecCanvas c = streakSheet(ui);
			int[] pause = spot(ui, "streakPause"), rescue = spot(ui, "streakRescue");
			assertTrue(c.strings().contains(I18n.tr("reels.streak.rescue")), c.strings().toString());
			if (hold == PhoneHold.LANDSCAPE) {
				assertEquals(pause[1], rescue[1], "quer: nebeneinander");
				assertTrue(pause[0] + pause[2] < rescue[0]);
			} else {
				assertTrue(rescue[1] >= pause[1] + pause[3], "hochkant: untereinander");
			}
			assertTrue(rescue[3] >= 40 && pause[3] >= 40);
			assertTrue(inside(rescue, spot(ui, "streakSheet")));
			assertEquals(FeedView.RED_BUTTON, fillAt(c, rescue).argb);
			// Ohne Streak gibt es heute nichts zu pausieren.
			assertEquals(ComposeView.BUTTON_GREY, fillAt(c, pause).argb);
			PhoneLayout l = PhoneLayout.of(host.w, host.h, hold);
			assertTrue(spot(ui, "streakSheet")[3] < Math.round((hold == PhoneHold.LANDSCAPE ? 290 : 400) * l.q), hold + ": niedriger als die Kommentare");
			ui.dispose();
		}

		// Zwei Tage verpasst: ein Schutz für gestern brächte nichts – kein Knopf.
		backend = new LocalReelsBackend(tmp.resolve("c"), UTC);
		reels = Reels.forTest(modules, backend, DIRECT, UTC);
		post("Vor vier Tagen", host.name, 98, false);
		post("Vor drei Tagen", host.name, 74, false);
		ui = feed(PhoneHold.LANDSCAPE);
		RecCanvas c = streakSheet(ui);
		assertNull(ui.testSpot("streakRescue"));
		assertTrue(all(c).contains(I18n.tr("reels.streak.state.none")), c.strings().toString());
		assertNull(ui.testToast(), "und kein Hinweis beim Herausholen");
		ui.dispose();
	}

	@Test
	void stateTextSaysWhatTheRescueBringsBack() {
		post("Vor vier Tagen", host.name, 98, false);
		post("Vor drei Tagen", host.name, 74, false);
		post("Vorgestern", host.name, 50, false);
		post("Heute", host.name, 2, false);
		for (PhoneHold hold : PhoneHold.values()) {
			PhoneUi ui = feed(hold);
			RecCanvas c = streakSheet(ui);
			assertEquals("Yesterday is missing. A protection brings your streak from 1 back to 4.", I18n.tr("reels.streak.state.broken", 1, 4));
			assertTrue(all(c).contains(I18n.tr("reels.streak.state.broken", 1, 4)), hold + ": " + c.strings());
			StreakGuard g = ui.testStreakGuard();
			assertEquals(1, g.streak);
			assertEquals(4, g.streakIfRescued);
			assertTrue(g.canRescueYesterday);
			assertFalse(g.canPauseToday, "heute ist schon gepostet");
			ui.dispose();
		}
	}

	@Test
	void stateTextSaysWhenNoProtectionIsLeftForYesterday() {
		useBothProtections();
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		assertNull(ui.testToast(), "nichts mehr zu retten: kein Hinweis");
		RecCanvas c = streakSheet(ui);
		StreakGuard g = ui.testStreakGuard();
		assertTrue(g.rescueOffered && !g.canRescueYesterday, "gestern fehlt, aber der Monat ist aufgebraucht");
		assertEquals(0, g.leftForYesterday);
		assertTrue(all(c).contains(I18n.tr("reels.streak.state.missed")), c.strings().toString());
		assertTrue(all(c).contains(I18n.tr("reels.streak.left", 0, 2)), c.strings().toString());
		// „Gestern retten“ steht da, ruht aber.
		assertEquals(ComposeView.BUTTON_GREY, fillAt(c, spot(ui, "streakRescue")).argb);
		assertEquals(FeedView.TEXT_DIM, textOf(c, I18n.tr("reels.streak.rescue")).argb);
		click(ui, "streakRescue");
		ui.testTicker += PhoneUi.POST_GUARD_MS;
		frame(ui);
		click(ui, "streakRescue");
		ui.inputKey(0, UiKey.ENTER, false);
		assertFalse(ui.testStreakGuard().yesterdayFrozen);
		ui.dispose();
	}

	@Test
	void rescueRaisesTheShownStreakAtOnce() {
		post("Vor drei Tagen", host.name, 74, false);
		post("Vorgestern", host.name, 50, false);
		post("Heute", host.name, 2, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		RecCanvas c = frame(ui);
		assertNotNull(textOf(c, "1"), "Streak 1: " + c.strings());
		streakSheet(ui);
		click(ui, "streakRescue");
		assertEquals(1, backend.streak(NOON), "der erste Druck ändert nichts");
		ui.testTicker += PhoneUi.POST_GUARD_MS;
		frame(ui);
		click(ui, "streakRescue");
		assertEquals(3, backend.streak(NOON));
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
		assertEquals(I18n.tr("reels.toast.streak.rescued", 3), ui.testToast());
		assertEquals("Rescued! Streak: 3 days", ui.testToast());
		c = frame(ui);
		assertNotNull(textOf(c, "3"), "die Kopfzeile zeigt sofort 3: " + c.strings());
		assertNull(textOf(c, "1"));
		StreakGuard g = ui.testStreakGuard();
		assertTrue(g.yesterdayFrozen);
		assertEquals(1, g.leftForYesterday);
		// Blatt wieder auf: „Gestern retten“ ist weg.
		ui.testTicker += PhoneUi.TOAST_MS + 1;
		frame(ui);
		streakSheet(ui);
		assertNull(ui.testSpot("streakRescue"));
		ui.dispose();

		// Ein Tag: eigene Fassung.
		backend = new LocalReelsBackend(tmp.resolve("einer"), UTC);
		reels = Reels.forTest(modules, backend, DIRECT, UTC);
		post("Vorgestern", host.name, 50, false);
		ui = feed(PhoneHold.PORTRAIT);
		streakSheet(ui);
		click(ui, "streakRescue");
		ui.testTicker += PhoneUi.POST_GUARD_MS;
		frame(ui);
		click(ui, "streakRescue");
		assertEquals(I18n.tr("reels.toast.streak.rescued.one", 1), ui.testToast());
		ui.dispose();
	}

	@Test
	void rescuableToastShowsOncePerDay() {
		post("Vor drei Tagen", host.name, 74, false);
		post("Vorgestern", host.name, 50, false);
		post("Heute", host.name, 2, false);
		PhoneUi first = feed(PhoneHold.PORTRAIT);
		assertEquals(I18n.tr("reels.toast.streak.rescuable"), first.testToast(), "beim Herausholen: gestern lässt sich retten");
		RecCanvas c = frame(first);
		assertNotNull(first.testSpot("toast"));
		assertTrue(all(c).contains(I18n.tr("reels.toast.streak.rescuable")), "ganz zu lesen: " + c.strings());
		// Die Meldung steht unter der Marke im Kopf, nicht darauf.
		RecCanvas.Text chip = textOf(c, "All · New");
		assertTrue(spot(first, "toast")[1] >= chip.y + chip.h, "unter der Filter-Marke");
		first.dispose();
		PhoneUi second = feed(PhoneHold.PORTRAIT);
		assertNull(second.testToast(), "am selben Tag nicht noch einmal");
		second.dispose();

		// Eine Antwort auf einen Klick ersetzt die Meldung sofort; was von selbst kommt, wartet.
		reels = Reels.forTest(modules, backend, DIRECT, UTC);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		assertEquals(I18n.tr("reels.toast.streak.rescuable"), ui.testToast(), "nach einem Neustart des Spiels noch einmal");
		ui.announce("Zweite", null);
		ui.announce("Dritte", null);
		ui.announce("Vierte", null);
		ui.announce("Fünfte – zu viel", null);
		assertEquals(I18n.tr("reels.toast.streak.rescuable"), ui.testToast(), "die laufende bleibt stehen");
		ui.testTicker += PhoneUi.TOAST_MS;
		frame(ui);
		assertEquals("Zweite", ui.testToast());
		ui.toast("Direkt");
		assertEquals("Direkt", ui.testToast());
		ui.testTicker += PhoneUi.TOAST_MS;
		frame(ui);
		assertEquals("Dritte", ui.testToast(), "die wartende kommt danach");
		assertNull(ui.testSpot("toast") == null ? "fehlt" : null, "sie wird gezeichnet");
		ui.testTicker += PhoneUi.TOAST_MS;
		frame(ui);
		assertEquals("Vierte", ui.testToast());
		ui.testTicker += PhoneUi.TOAST_MS;
		frame(ui);
		assertNull(ui.testToast(), "höchstens drei warten");
		ui.dispose();

		// Am nächsten Tag ist „gestern“ ein Tag mit Post: nichts zu retten, keine Meldung.
		reels = Reels.forTest(modules, backend, DIRECT, UTC);
		PhoneUi next = ui(PhoneHold.PORTRAIT);
		next.testNow = NOON + DAY;
		next.testTicker = 5000;
		frame(next);
		assertNull(next.testToast());
		next.dispose();
	}

	@Test
	void noProtectionLeftShowsWhy() {
		useBothProtections();
		post("Gestern", host.name, 26, false);
		PhoneUi ui = feed(PhoneHold.PORTRAIT);
		RecCanvas c = streakSheet(ui);
		StreakGuard g = ui.testStreakGuard();
		assertEquals(2, g.streak, "9. und 12., dazwischen zwei geschützte Tage");
		assertFalse(g.canPauseToday);
		assertEquals(0, g.leftThisMonth);
		assertTrue(all(c).contains(I18n.tr("reels.streak.left", 0, 2)), c.strings().toString());
		assertTrue(all(c).contains(I18n.tr("reels.streak.state.open")), c.strings().toString());
		assertEquals(ComposeView.BUTTON_GREY, fillAt(c, spot(ui, "streakPause")).argb);
		click(ui, "streakPause");
		ui.testTicker += PhoneUi.POST_GUARD_MS;
		frame(ui);
		click(ui, "streakPause");
		assertFalse(ui.testStreakGuard().todayFrozen);
		ui.dispose();

		// Die Uhr steht hinter dem neuesten Post: dann sagt die Zeile das.
		PhoneUi back = ui(PhoneHold.LANDSCAPE);
		back.testNow = NOON - 3 * DAY;
		back.testTicker = 5000;
		frame(back);
		c = streakSheet(back);
		assertTrue(back.testStreakGuard().clockBehind);
		assertTrue(all(c).contains(I18n.tr("reels.streak.clock")), c.strings().toString());
		assertFalse(all(c).contains(I18n.tr("reels.streak.left", 0, 2)));
		assertEquals(ComposeView.BUTTON_GREY, fillAt(c, spot(back, "streakPause")).argb);
		back.dispose();
	}

	@Test
	void secondWindowShowsWhyAndChangesNothing() throws IOException {
		post("Vor drei Tagen", host.name, 74, false);
		post("Vorgestern", host.name, 50, false);
		post("Gestern", host.name, 26, false);
		backend.flush();
		byte[] before = Files.readAllBytes(tmp.resolve("feed.json"));
		List<ReelsPost> feed = backend.feed();
		reels = Reels.forTest(modules, secondWindow(backend), DIRECT, UTC);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		RecCanvas c = streakSheet(ui);
		assertTrue(ui.testStreakGuard().readOnly);
		assertTrue(all(c).contains(I18n.tr("reels.streak.readonly")), c.strings().toString());
		assertFalse(all(c).contains(I18n.tr("reels.streak.left", 2, 2)));
		assertEquals(ComposeView.BUTTON_GREY, fillAt(c, spot(ui, "streakPause")).argb);
		click(ui, "streakPause");
		ui.inputKey(0, UiKey.ENTER, false);
		ui.testTicker += PhoneUi.POST_GUARD_MS;
		frame(ui);
		click(ui, "streakPause");
		ui.inputKey(0, UiKey.ENTER, false);
		assertEquals(PhoneUi.Sheet.STREAK, ui.testSheet());
		ui.dispose();
		assertTrue(feed == backend.feed(), "der Feed ist unverändert");
		assertFalse(backend.streakGuard(NOON).todayFrozen);
		assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(tmp.resolve("feed.json"))));
	}

	@Test
	void composeHintStaysCorrectWithAProtectedDay() {
		post("Vorgestern", host.name, 50, false);
		assertEquals(StreakGuard.Result.OK, backend.protect(false, NOON - DAY));
		assertEquals(1, backend.streak(NOON), "gestern war geschützt: die Streak läuft noch");
		PhoneUi ui = camera(PhoneHold.PORTRAIT);
		shoot(ui);
		RecCanvas c = frame(ui);
		assertTrue(c.shows(I18n.tr("reels.compose.streak", 1, 2)), "„Streak: 1 → 2 days“: " + c.strings());
		armPost(ui);
		ui.keyPressed(0, UiKey.ENTER, false);
		assertEquals(I18n.tr("reels.toast.posted", 2), ui.testToast());
		ui.dispose();
	}

	@Test
	void escClosesTheStreakSheetOnly() {
		post("Gestern", host.name, 26, false);
		PhoneUi ui = feed(PhoneHold.LANDSCAPE);
		streakSheet(ui);
		click(ui, "streakPause");
		assertTrue(ui.inputKey(0, UiKey.ESCAPE, false), "Esc ist verbraucht");
		assertEquals(PhoneUi.Sheet.NONE, ui.testSheet());
		assertFalse(ui.isClosing());
		assertEquals(PhoneUi.View.FEED, ui.testView());
		assertFalse(ui.testStreakGuard().todayFrozen);
		assertFalse(ui.inputKey(0, UiKey.ESCAPE, false), "das nächste Esc gehört dem Bildschirm der Version");
		ui.dispose();
	}

	// --- Erstes Bild: der Feed merkt sich den Tag ---

	@Test
	void takingThePhoneOutRemembersTheDayOnceAndSavesIt() throws IOException {
		post("Heute", host.name, 2, false);
		backend.flush();
		assertFalse(new String(Files.readAllBytes(tmp.resolve("feed.json")), java.nio.charset.StandardCharsets.UTF_8).contains("clockDay"));
		PhoneUi ui = ui(PhoneHold.LANDSCAPE);
		assertFalse(backend.dirty(), "vor dem ersten Bild ändert sich nichts");
		frame(ui);
		// Mit Posts im Feed merkt er sich den Tag – und sichert ihn gleich.
		assertFalse(backend.dirty(), "gleich gesichert");
		String json = new String(Files.readAllBytes(tmp.resolve("feed.json")), java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(json.contains("\"clockDay\": \"2026-11-13\"") || json.contains("\"clockDay\":\"2026-11-13\""), json);
		byte[] once = Files.readAllBytes(tmp.resolve("feed.json"));
		frame(ui);
		ui.dispose();
		PhoneUi again = feed(PhoneHold.PORTRAIT);
		again.dispose();
		assertTrue(java.util.Arrays.equals(once, Files.readAllBytes(tmp.resolve("feed.json"))), "am selben Tag nur einmal");
		// Ein leerer Feed bleibt ungeschrieben.
		Path empty = tmp.resolve("leer2");
		backend = new LocalReelsBackend(empty, UTC);
		reels = Reels.forTest(modules, backend, DIRECT, UTC);
		PhoneUi ui2 = feed(PhoneHold.LANDSCAPE);
		ui2.dispose();
		assertFalse(Files.exists(empty.resolve("feed.json")));
	}
}
