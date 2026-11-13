package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.reels.ArmRig;
import dev.theredstonee.trsclient.core.reels.ArmSkin;
import dev.theredstonee.trsclient.core.reels.LocalReelsBackend;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.reels.ReelsBackend;
import dev.theredstonee.trsclient.core.reels.ReelsComment;
import dev.theredstonee.trsclient.core.reels.ReelsPost;
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
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das MC-Reels-Handy ohne Spiel: zeichnen in Fensterpixeln bei jeder GUI-Skalierung, Arme aus dem Skin (breit, schlank,
 * Innenseite, Ärmel), Texte bleiben auf dem Bildschirm des Handys, Feed blättern, Herz, Kommentare, Drehen, Schließen
 * und Aufräumen.
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
		ReelsPost old = post("Aussicht hinter meiner Basis – und ein ziemlich langer Text dazu, der umbrechen muss!", "Rotkaeppchen_2010",
				30, true);
		ReelsPost top = post("Schaut mal, wer bei mir eingezogen ist! Der Panda bleibt jetzt für immer hier bei mir.", host.name, 2,
				false);
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
			assertTrue(ui.commentsShown >= 1 && ui.commentsShown <= FeedView.MAX_COMMENTS_SHOWN, "gezeigt: " + ui.commentsShown);
			assertTrue(c.shows("Kommentar 6"), "der neueste steht da: " + c.strings());
			assertFalse(c.shows("Kommentar 0"), "nur die neuesten");
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
		// Dasselbe im Kommentarfeld.
		post("Hallo", host.name, 1, false);
		PhoneUi feed = ui(PhoneHold.LANDSCAPE);
		frame(feed);
		feed.keyPressed(0, UiKey.ENTER, false);
		assertEquals(PhoneUi.View.COMMENTS, feed.testView());
		for (char ch : "abc".toCharArray()) feed.inputChar(ch);
		feed.keyPressed(0, UiKey.LEFT, false);
		feed.keyPressed(0, UiKey.HOME, false);
		feed.inputChar('d');
		assertEquals("abcd", feed.commentInput.text());
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
}
