package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.reels.ArmRig;
import dev.theredstonee.trsclient.core.reels.ArmSkin;
import dev.theredstonee.trsclient.core.reels.LocalReelsBackend;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.reels.Reels;
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
			final float u, v;
			final int w, h, argb;
			/** x' = m0·x + m2·y + m4, y' = m1·x + m3·y + m5 (Texel → Fensterpixel). */
			final double[] m;

			Img(TextureRef texture, float u, float v, int w, int h, int argb, double[] m) {
				this.texture = texture;
				this.u = u;
				this.v = v;
				this.w = w;
				this.h = h;
				this.argb = argb;
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
			return rawKey == 'H' ? "key.keyboard.h" : rawKey == 'R' ? "key.keyboard.r" : rawKey == 'X' ? "key.keyboard.x" : null;
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
		assertTrue(all.toString().contains("H:"), "die Handy-Taste steht im Hinweis: " + all);
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

		// Buchstaben sind im Textfeld nie Kurztasten: R dreht nicht, H steckt nicht weg.
		assertTrue(ui.inputKey('R', UiKey.NONE, false));
		assertTrue(ui.inputKey('H', UiKey.NONE, false));
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
		// H ist beim Öffnen noch unten (Tastenwiederholung): das Handy bleibt draußen.
		host.down.add("key.keyboard.h");
		frame(ui);
		assertTrue(ui.inputKey('H', UiKey.NONE, false), "die Taste gehört trotzdem dem Handy");
		assertFalse(ui.isClosing(), "gehaltene Taste steckt das Handy nicht gleich wieder weg");
		frame(ui);
		ui.inputKey('H', UiKey.NONE, false);
		assertFalse(ui.isClosing());
		// Losgelassen, neu gedrückt: jetzt weg damit.
		host.down.clear();
		frame(ui);
		host.down.add("key.keyboard.h");
		assertTrue(ui.inputKey('H', UiKey.NONE, false));
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
		assertEquals(3, store.live.size(), "Schleier + beide Fotos (das Nachbarfoto wird mitgeladen): " + store.live);
		backend.toggleLike(p.id);
		assertTrue(backend.dirty());
		assertFalse(reels.keyGate().blocked());

		ui.dispose();
		assertTrue(ui.disposed());
		assertTrue(store.live.isEmpty(), "alle Texturen frei: " + store.live);
		assertEquals(3, store.released.size());
		assertTrue(reels.keyGate().blocked(), "die Handy-Taste ist bis zum Loslassen gesperrt");
		assertFalse(backend.dirty(), "der Feed ist gesichert");
		assertTrue(new LocalReelsBackend(tmp, UTC).feed().get(0).likedByMe);

		// Noch einmal (Schließ-Animation UND removed() des Bildschirms): nichts passiert doppelt.
		reels.keyGate().shouldOpen(false, false);
		assertFalse(reels.keyGate().blocked());
		int uploads = store.uploads;
		ui.dispose();
		assertEquals(3, store.released.size());
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
