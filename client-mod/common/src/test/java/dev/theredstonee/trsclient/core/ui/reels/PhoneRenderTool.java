package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.reels.FeedFilter;
import dev.theredstonee.trsclient.core.reels.FeedSort;
import dev.theredstonee.trsclient.core.reels.LocalReelsBackend;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.reels.ReelsComment;
import dev.theredstonee.trsclient.core.reels.ReelsMeta;
import dev.theredstonee.trsclient.core.reels.ReelsPost;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.TextureRef;
import dev.theredstonee.trsclient.core.ui.Textures;
import dev.theredstonee.trsclient.core.ui.UiKey;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Entwickler-Werkzeug (kein normaler Test): zeichnet das MC-Reels-Handy ohne Spiel in PNG-Dateien – zum Vergleichen mit
 * den freigegebenen Bildern (Arme, Gehäuse, Feed, Kommentare – auch beim Antworten, mit Namensvorschlägen und beim
 * Bearbeiten –, die Marke und die Meldung zu einer Erwähnung, die Blätter „Bild“, „Feed“ und „Streak“, die Marken am
 * Post, verschiedene Fenstergrößen und GUI-Skalierungen). Geklickt wird dabei nie auf „Bild kopieren“ oder „Ordner
 * öffnen“ – das Werkzeug fasst weder die Zwischenablage noch den Dateimanager an. Die
 * Zeichenfläche ist eine einfache Software-Umsetzung von {@link Canvas}; die Schrift ist nur ähnlich breit wie die des
 * Spiels (6 px je Zeichen), nicht dieselbe.
 *
 * <p>Läuft nur mit {@code -Dtrsclient.test.phoneOut=<Ordner>}. Optional: {@code -Dtrsclient.test.phoneSkin=<skin.png>}
 * (64×64; sonst ein einfacher Test-Skin), {@code -Dtrsclient.test.phoneSlim=true},
 * {@code -Dtrsclient.test.phonePhotos=<a.png,b.png,…>} (werden zu Posts, das letzte ist das neueste),
 * {@code -Dtrsclient.test.phoneBg=<bild>} (Spielbild hinter dem Schleier), {@code -Dtrsclient.test.phoneLang=de}.
 */
class PhoneRenderTool {
	/** Software-Zeichenfläche auf einem Bild in Fensterpixeln. */
	static final class AwtCanvas implements Canvas {
		final BufferedImage image;
		final Graphics2D g;
		final Map<Object, BufferedImage> textures;
		private final Deque<AffineTransform> stack = new ArrayDeque<AffineTransform>();
		private final Font font = new Font(Font.MONOSPACED, Font.BOLD, 10);

		AwtCanvas(int w, int h, Map<Object, BufferedImage> textures) {
			image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			g = image.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			// Ohne das verschiebt Java2D Formen um Bruchteile eines Pixels – zwischen den Arm-Streifen entstünden Haarlinien.
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			this.textures = textures;
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int argb) {
			if (x2 <= x1 || y2 <= y1 || (argb >>> 24) == 0) return;
			g.setColor(new Color(argb, true));
			g.fill(new java.awt.geom.Rectangle2D.Float(x1, y1, x2 - x1, y2 - y1));
		}

		@Override
		public void text(String text, int x, int y, int argb, boolean shadow) {
			if (text == null || (argb >>> 24) == 0) return;
			g.setFont(font);
			if (shadow) {
				int a = argb >>> 24;
				g.setColor(new Color(((argb >> 16) & 0xFF) / 4, ((argb >> 8) & 0xFF) / 4, (argb & 0xFF) / 4, a));
				g.drawString(text, x + 1, y + 8);
			}
			g.setColor(new Color(argb, true));
			g.drawString(text, x, y + 7);
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
			stack.push(g.getTransform());
		}

		@Override
		public void translate(float x, float y) {
			g.translate(x, y);
		}

		@Override
		public void scale(float factor) {
			g.scale(factor, factor);
		}

		@Override
		public void pop() {
			g.setTransform(stack.pop());
		}

		@Override
		public boolean images() {
			return true;
		}

		@Override
		public void image(TextureRef texture, float u, float v, int w, int h, int argb) {
			BufferedImage src = textures.get(texture.id);
			if (src == null || w <= 0 || h <= 0) return;
			// Texel der Nenngröße → Pixel des Bildes (HD-Skins).
			float fx = src.getWidth() / (float) texture.width, fy = src.getHeight() / (float) texture.height;
			int tr = (argb >> 16) & 0xFF, tg = (argb >> 8) & 0xFF, tb = argb & 0xFF, ta = argb >>> 24;
			if (w * fx * h * fy <= 64.5f) {
				// Kleine Blits (Arm-Streifen, Gesicht) Texel für Texel: exakt, ohne Rundung des Bild-Zeichnens. Die Arme
				// adressieren den Skin feiner als in ganzen Texeln und lesen etwas innerhalb – also je Bild-Texel den Teil
				// des Blits füllen, der auf ihn fällt.
				int x0 = (int) Math.floor(u * fx + 1e-4f), x1 = (int) Math.ceil((u + w) * fx - 1e-4f);
				int y0 = (int) Math.floor(v * fy + 1e-4f), y1 = (int) Math.ceil((v + h) * fy - 1e-4f);
				for (int ty = y0; ty < y1; ty++) {
					for (int tx = x0; tx < x1; tx++) {
						int p = src.getRGB(Math.floorMod(tx, src.getWidth()), Math.floorMod(ty, src.getHeight()));
						int a = (p >>> 24) * ta / 255;
						if (a == 0) continue;
						float lx0 = Math.max(0f, tx / fx - u), lx1 = Math.min(w, (tx + 1) / fx - u);
						float ly0 = Math.max(0f, ty / fy - v), ly1 = Math.min(h, (ty + 1) / fy - v);
						g.setColor(new Color(((p >> 16) & 0xFF) * tr / 255, ((p >> 8) & 0xFF) * tg / 255, (p & 0xFF) * tb / 255, a));
						g.fill(new java.awt.geom.Rectangle2D.Float(lx0, ly0, lx1 - lx0, ly1 - ly0));
					}
				}
				return;
			}
			int sx = Math.max(0, Math.min(src.getWidth() - 1, Math.round(u * fx)));
			int sy = Math.max(0, Math.min(src.getHeight() - 1, Math.round(v * fy)));
			int sw = Math.max(1, Math.min(src.getWidth() - sx, Math.round(w * fx)));
			int sh = Math.max(1, Math.min(src.getHeight() - sy, Math.round(h * fy)));
			BufferedImage part = src.getSubimage(sx, sy, sw, sh);
			if (argb != 0xFFFFFFFF) {
				BufferedImage tinted = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
				for (int y = 0; y < sh; y++) {
					for (int x = 0; x < sw; x++) {
						int p = part.getRGB(x, y);
						tinted.setRGB(x, y, ((p >>> 24) * ta / 255) << 24 | (((p >> 16) & 0xFF) * tr / 255) << 16
								| (((p >> 8) & 0xFF) * tg / 255) << 8 | ((p & 0xFF) * tb / 255));
					}
				}
				part = tinted;
			}
			g.drawImage(part, 0, 0, w, h, null);
		}

		@Override
		public void rotate(float radians) {
			g.rotate(radians);
		}

		@Override
		public void scale(float sx, float sy) {
			g.scale(sx, sy);
		}
	}

	private final Map<Object, BufferedImage> textures = new HashMap<Object, BufferedImage>();

	private Textures.Store store() {
		return new Textures.Store() {
			@Override
			public TextureRef upload(String name, int width, int height, int[] argb) {
				BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				img.setRGB(0, 0, width, height, argb, 0, width);
				textures.put(name, img);
				return new TextureRef(name, width, height);
			}

			@Override
			public void release(TextureRef texture) {
				textures.remove(texture.id);
			}

			@Override
			public TextureRef game(String location, int width, int height) {
				return new TextureRef(location, width, height);
			}

			@Override
			public Textures.DefaultSkin defaultSkin(UUID uuid) {
				return new Textures.DefaultSkin(new TextureRef("skin", 64, 64), false);
			}
		};
	}

	/** Einfacher Test-Skin: grüne Arme mit dunklen Streifen und hellen Ecken an der Hand. */
	private static BufferedImage testSkin(boolean slim) {
		BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		int w = slim ? 3 : 4;
		int[][] boxes = {{32, 48}, {40, 16}};
		for (int[] b : boxes) {
			for (int y = 0; y < 12; y++) {
				for (int x = 0; x < 8 + 2 * w; x++) {
					int rgb = y == 4 || y == 8 ? 0x00724A : 0x0BD666;
					skin.setRGB(b[0] + x, b[1] + 4 + y, 0xFF000000 | rgb);
				}
				if (y == 11) {
					for (int x : new int[]{0, 3, 4, 4 + w - 1, 4 + w, 4 + w + 3}) skin.setRGB(b[0] + x, b[1] + 4 + y, 0xFFFFFFFF);
				}
			}
		}
		for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) skin.setRGB(x, y, 0xFFC68E6B);
		for (int x = 8; x < 16; x++) skin.setRGB(x, 8, 0xFF4A3220);
		skin.setRGB(10, 12, 0xFFFFFFFF);
		skin.setRGB(13, 12, 0xFFFFFFFF);
		return skin;
	}

	private static PhoneUi.Host host(final int w, final int h, final int gui, final boolean slim) {
		return new PhoneUi.Host() {
			@Override public int windowWidth() { return w; }
			@Override public int windowHeight() { return h; }
			@Override public int guiScale() { return gui; }
			@Override public TextureRef skin() { return new TextureRef("skin", 64, 64); }
			@Override public boolean slimSkin() { return slim; }
			@Override public boolean sleeve(boolean left) { return true; }
			@Override public String playerName() { return "PixelDEV_CAM"; }
			@Override public String keyName(int rawKey) { return rawKey == 'K' ? "key.keyboard.k" : rawKey == 'R' ? "key.keyboard.r" : null; }
			@Override public boolean keyDown(String keyName) { return false; }
			@Override public String keyLabel(String keyName) { return keyName.substring(keyName.lastIndexOf('.') + 1).toUpperCase(); }
			@Override public String paste() { return null; }
			@Override public void click() { }
			@Override public void turn(float yawDeg, float pitchDeg) { }
			@Override public void requestFrame(boolean photo, int needW, int needH) { pending = Boolean.valueOf(photo); }
			@Override public PhoneUi.Frame pollFrame() {
				Boolean photo = pending;
				pending = null;
				if (photo == null) return null;
				// Das „Spiel“ ohne Oberfläche: das Hintergrundbild in Fenstergröße.
				BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
				Graphics2D g = img.createGraphics();
				if (world != null) g.drawImage(world, 0, 0, w, h, null);
				else {
					g.setColor(new Color(0x6FA8DC));
					g.fillRect(0, 0, w, h);
					g.setColor(new Color(0x58B04A));
					g.fillRect(0, h * 6 / 10, w, h);
				}
				g.dispose();
				return new PhoneUi.Frame(img.getRGB(0, 0, w, h, null, 0, w), w, h, photo.booleanValue());
			}
			@Override public void close() { }
			// Das Werkzeug fasst weder die Zwischenablage noch den Dateimanager des Systems an.
			@Override public boolean openFolder(Path dir) { return false; }
			@Override public dev.theredstonee.trsclient.core.screenshot.ImageClipboard.Result copyImage(Path png) {
				return dev.theredstonee.trsclient.core.screenshot.ImageClipboard.Result.UNSUPPORTED;
			}
			@Override public java.util.Collection<String> friendNames() { return null; }
			// Ausgedachte Spieler auf dem „Server“ – für die Namensvorschläge in den Kommentaren.
			@Override public java.util.List<String> onlinePlayers() { return java.util.Arrays.asList("Rotkaeppchen", "Robin_Hood", "Rosalinde"); }
		};
	}

	/** Angeforderte Art des nächsten Bilds (Foto?) oder null. */
	private static Boolean pending;
	/** Bild der „Welt“ für die Kamera. */
	private static BufferedImage world;

	@Test
	void render() throws Exception {
		String outDir = System.getProperty("trsclient.test.phoneOut");
		Assumptions.assumeTrue(outDir != null, "nur als Werkzeug");
		System.setProperty("java.awt.headless", "true");
		Path out = Paths.get(outDir);
		Files.createDirectories(out);
		boolean slim = Boolean.getBoolean("trsclient.test.phoneSlim");
		String skinFile = System.getProperty("trsclient.test.phoneSkin");
		textures.put("skin", skinFile != null ? ImageIO.read(new File(skinFile)) : testSkin(slim));
		String bgFile = System.getProperty("trsclient.test.phoneBg");
		BufferedImage bg = bgFile != null ? ImageIO.read(new File(bgFile)) : null;
		I18n.use(System.getProperty("trsclient.test.phoneLang", "de"));
		Textures.Store before = Textures.store();
		Textures.replaceForTests(store());
		Path work = Files.createTempDirectory("trs-reels-render");
		try {
			TrsModules modules = new TrsModules();
			LocalReelsBackend backend = new LocalReelsBackend(work, TimeZone.getDefault());
			Executor direct = new Executor() {
				@Override
				public void execute(Runnable r) {
					r.run();
				}
			};
			Reels reels = Reels.forTest(modules, backend, direct, TimeZone.getDefault());
			long now = System.currentTimeMillis();

			// Leerer Feed zuerst.
			shot(reels, modules, bg, out, "leer-quer", 1920, 1080, 2, PhoneHold.LANDSCAPE, slim, false);
			shot(reels, modules, bg, out, "leer-hochkant", 1920, 1080, 2, PhoneHold.PORTRAIT, slim, false);

			String photos = System.getProperty("trsclient.test.phonePhotos");
			if (photos != null) {
				String[] files = photos.split(",");
				for (int i = 0; i < files.length; i++) {
					BufferedImage img = ImageIO.read(new File(files[i].trim()));
					long at = now - (files.length - i) * 7200000L;
					String id = backend.newId(at);
					Path target = backend.newPhotoFile(id);
					Files.createDirectories(target.getParent());
					ImageIO.write(img, "png", target.toFile());
					ReelsPost p = backend.post(id + ".png", img.getWidth(), img.getHeight(), img.getHeight() > img.getWidth(), false,
							i % 2 == 0 ? "Schaut mal, wer bei mir eingezogen ist!" : "Aussicht hinter meiner Basis – und ein ziemlich langer Text dazu.",
							"PixelDEV_CAM", at);
					if (p != null && i == files.length - 1) {
						reels.toggleLike(p.id);
						reels.comment(p.id, "Rotkaeppchen", "Wie süß!", at + 1000);
						reels.comment(p.id, "PixelDEV_CAM", "Der Panda wohnt jetzt bei mir, die Katze sowieso. Kommt vorbei!", at + 2000);
						reels.comment(p.id, "Du", "Bin gleich da.", at + 3000);
					}
				}
				int[][] sizes = {{1920, 1080, 2}, {1920, 1080, 4}, {1280, 720, 2}, {854, 480, 1}, {1280, 1024, 2}, {800, 1000, 2},
						{2560, 1080, 3}, {3840, 2160, 4}};
				for (int[] s : sizes) {
					for (PhoneHold hold : PhoneHold.values()) {
						String tag = (hold == PhoneHold.LANDSCAPE ? "quer" : "hochkant") + "-" + s[0] + "x" + s[1] + "-gui" + s[2];
						shot(reels, modules, bg, out, "feed-" + tag, s[0], s[1], s[2], hold, slim, false);
					}
				}
				shot(reels, modules, bg, out, "kommentare-quer", 1920, 1080, 2, PhoneHold.LANDSCAPE, slim, true);
				shot(reels, modules, bg, out, "kommentare-hochkant", 1920, 1080, 2, PhoneHold.PORTRAIT, slim, true);
				shot(reels, modules, bg, out, "kommentare-hochkant-854", 854, 480, 1, PhoneHold.PORTRAIT, slim, true);
				// Kamera und „Neuer Post“.
				world = bg;
				int[][] camSizes = {{1920, 1080, 2}, {854, 480, 1}, {800, 1000, 2}};
				for (int[] s : camSizes) {
					for (PhoneHold hold : PhoneHold.values()) {
						String tag = (hold == PhoneHold.LANDSCAPE ? "quer" : "hochkant") + "-" + s[0] + "x" + s[1];
						cameraShot(reels, modules, bg, out, "kamera-" + tag, s[0], s[1], s[2], hold, slim, false, false);
						cameraShot(reels, modules, bg, out, "kamera-selfie-" + tag, s[0], s[1], s[2], hold, slim, true, false);
						cameraShot(reels, modules, bg, out, "posten-" + tag, s[0], s[1], s[2], hold, slim, false, true);
					}
				}
				// Zweiter Post (anders ausgerichtet): eingepasst statt beschnitten.
				if (files.length > 1) {
					pageShot(reels, modules, bg, out, "seite2-quer", PhoneHold.LANDSCAPE, slim);
					pageShot(reels, modules, bg, out, "seite2-hochkant", PhoneHold.PORTRAIT, slim);
				}
				additions(reels, modules, backend, bg, out, slim, ImageIO.read(new File(files[files.length - 1].trim())), now);
			}
		} finally {
			Textures.replaceForTests(before);
			Reels.clearForTest();
			I18n.use("en");
		}
	}

	/**
	 * Die Ergänzungen: die drei Blätter, der Feed unter „Freunde“ und „Beliebt“, Posts mit Marken (Shader, Instanz,
	 * Version) und der Streak-Schutz am Tag nach einem verpassten Tag (dafür ein eigener Feed).
	 */
	private void additions(Reels reels, TrsModules modules, LocalReelsBackend backend, BufferedImage bg, Path out, boolean slim,
			BufferedImage photo, long now) throws Exception {
		PhoneHold[] holds = PhoneHold.values();
		int[][] sizes = {{1920, 1080, 2}, {854, 480, 1}};
		for (PhoneHold hold : holds) {
			for (int[] s : sizes) {
				String tag = (hold == PhoneHold.LANDSCAPE ? "quer" : "hochkant") + (s[0] == 1920 ? "" : "-" + s[0]);
				sheetShot(reels, modules, bg, out, "blatt-bild-" + tag, s, hold, slim, PhoneUi.Sheet.SAVE, 0);
				sheetShot(reels, modules, bg, out, "blatt-feed-" + tag, s, hold, slim, PhoneUi.Sheet.FILTER, 0);
				sheetShot(reels, modules, bg, out, "blatt-streak-" + tag, s, hold, slim, PhoneUi.Sheet.STREAK, 0);
			}
		}
		// Filter „Freunde“ (ohne Server leer) und Reihenfolge „Beliebt“.
		modules.reels.feedFilter.set(FeedFilter.FRIENDS);
		shot(reels, modules, bg, out, "leer-freunde-quer", 1920, 1080, 2, PhoneHold.LANDSCAPE, slim, false);
		shot(reels, modules, bg, out, "leer-freunde-hochkant", 1920, 1080, 2, PhoneHold.PORTRAIT, slim, false);
		modules.reels.feedFilter.set(FeedFilter.MINE);
		modules.reels.feedSort.set(FeedSort.POPULAR);
		shot(reels, modules, bg, out, "feed-ich-beliebt-quer", 1920, 1080, 2, PhoneHold.LANDSCAPE, slim, false);
		shot(reels, modules, bg, out, "feed-ich-beliebt-hochkant", 1920, 1080, 2, PhoneHold.PORTRAIT, slim, false);
		modules.reels.feedFilter.set(FeedFilter.ALL);
		modules.reels.feedSort.set(FeedSort.NEWEST);

		// Posts mit Marken: lange Namen (die Version fällt weg bzw. die Namen werden gekürzt) und kurze.
		metaPost(backend, photo, now - 120000L, new ReelsMeta("26.2", "fabric", "Complementary Reimagined r5.5.1",
				"All the Mods 10 – To the Sky"));
		shot(reels, modules, bg, out, "marken-lang-quer", 1920, 1080, 2, PhoneHold.LANDSCAPE, slim, false);
		shot(reels, modules, bg, out, "marken-lang-hochkant", 1920, 1080, 2, PhoneHold.PORTRAIT, slim, false);
		shot(reels, modules, bg, out, "marken-lang-hochkant-854", 854, 480, 1, PhoneHold.PORTRAIT, slim, false);
		metaPost(backend, photo, now - 60000L, new ReelsMeta("1.21.4", "neoforge", "BSL", "Mein Pack"));
		shot(reels, modules, bg, out, "marken-kurz-quer", 1920, 1080, 2, PhoneHold.LANDSCAPE, slim, false);
		shot(reels, modules, bg, out, "marken-kurz-hochkant", 1920, 1080, 2, PhoneHold.PORTRAIT, slim, false);
		shot(reels, modules, bg, out, "marken-kurz-quer-854", 854, 480, 1, PhoneHold.LANDSCAPE, slim, false);

		// Streak-Schutz: heute gepostet, gestern fehlt, davor drei Tage in Folge – „Gestern retten“ wird angeboten.
		Path work = Files.createTempDirectory("trs-reels-render-streak");
		LocalReelsBackend streak = new LocalReelsBackend(work, TimeZone.getDefault());
		Executor direct = new Executor() {
			@Override
			public void execute(Runnable r) {
				r.run();
			}
		};
		Reels rescue = Reels.forTest(modules, streak, direct, TimeZone.getDefault());
		long day = 86400000L;
		long[] at = {now - 4 * day, now - 3 * day, now - 2 * day, now};
		for (long t : at) metaPost(streak, photo, t, null);
		for (PhoneHold hold : holds) {
			String tag = hold == PhoneHold.LANDSCAPE ? "quer" : "hochkant";
			sheetShot(rescue, modules, bg, out, "blatt-streak-retten-" + tag, sizes[0], hold, slim, PhoneUi.Sheet.STREAK, 0);
			sheetShot(rescue, modules, bg, out, "blatt-streak-sicher-" + tag, sizes[0], hold, slim, PhoneUi.Sheet.STREAK, 2);
			sheetShot(rescue, modules, bg, out, "blatt-streak-retten-" + tag + "-854", sizes[1], hold, slim, PhoneUi.Sheet.STREAK, 0);
		}
		commentShots(modules, backend, bg, out, slim, now);
	}

	/**
	 * Kommentare mit einem Faden unter dem neuesten Post: erst der Feed mit der Marke am Kommentar-Symbol und der Meldung
	 * zu einer Erwähnung, dann das Blatt – schlicht, beim Antworten, mit Namensvorschlägen und beim Bearbeiten.
	 */
	private void commentShots(TrsModules modules, LocalReelsBackend backend, BufferedImage bg, Path out, boolean slim, long now)
			throws Exception {
		Executor direct = new Executor() {
			@Override
			public void execute(Runnable r) {
				r.run();
			}
		};
		ReelsPost post = backend.feed().get(0);
		ReelsComment first = backend.comment(post.id, "Rotkaeppchen", "Wie süß! Wo hast du den gefunden?", now - 50000L);
		ReelsComment mine = backend.reply(post.id, first.id, "PixelDEV_CAM", "Im Bambuswald hinter dem Dorf", now - 40000L);
		backend.editComment(post.id, mine.id, "PixelDEV_CAM", "Im Bambuswald hinter dem Dorf, gleich am Fluss.", now - 39000L);
		ReelsComment theirs = backend.reply(post.id, first.id, "Steve_Baut", "@PixelDEV_CAM zeigst du mir den Weg?", now - 30000L);
		backend.comment(post.id, "Du", "Bin gleich da.", now - 20000L);
		backend.comment(post.id, "PixelDEV_CAM", "Kommt alle vorbei, @Rotkaeppchen und @Steve_Baut!", now - 10000L);
		int[][] sizes = {{1920, 1080, 2}, {854, 480, 1}};
		for (PhoneHold hold : PhoneHold.values()) {
			String tag = hold == PhoneHold.LANDSCAPE ? "quer" : "hochkant";
			// Je Bild ein neuer Dienst: die Meldung kommt nur einmal je Spielstart.
			shot(Reels.forTest(modules, backend, direct, TimeZone.getDefault()), modules, bg, out, "ping-" + tag, 1920, 1080, 2, hold, slim, false);
		}
		Reels reels = Reels.get();
		for (PhoneHold hold : PhoneHold.values()) {
			for (int[] s : sizes) {
				String tag = (hold == PhoneHold.LANDSCAPE ? "quer" : "hochkant") + (s[0] == 1920 ? "" : "-" + s[0]);
				commentShot(reels, modules, bg, out, "kommentare-faden-" + tag, s, hold, slim, 0, theirs.id, mine.id);
				commentShot(reels, modules, bg, out, "kommentare-antwort-" + tag, s, hold, slim, 1, theirs.id, mine.id);
				commentShot(reels, modules, bg, out, "kommentare-namen-" + tag, s, hold, slim, 2, theirs.id, mine.id);
				commentShot(reels, modules, bg, out, "kommentare-bearbeiten-" + tag, s, hold, slim, 3, theirs.id, mine.id);
			}
		}
	}

	/**
	 * Das Kommentar-Blatt: {@code state} 0 schlicht, 1 beim Antworten auf {@code replyId}, 2 dazu ein getippter
	 * Namensanfang (Vorschläge und Hinweis), 3 beim Bearbeiten von {@code editId}.
	 */
	private void commentShot(Reels reels, TrsModules modules, BufferedImage bg, Path out, String name, int[] size, PhoneHold hold,
			boolean slim, int state, String replyId, String editId) throws Exception {
		modules.reels.hold.set(hold);
		PhoneUi ui = new PhoneUi(reels, host(size[0], size[1], size[2], slim));
		pump(ui, size[0], size[1], size[2], 1);
		ui.keyPressed(0, UiKey.ENTER, false);
		pump(ui, size[0], size[1], size[2], 1);
		if (state == 1 || state == 2) ui.startReply(replyId);
		if (state == 2) for (char ch : "und @Ro".toCharArray()) ui.inputChar(ch);
		if (state == 3) ui.startEdit(editId);
		write(ui, bg, out, name, size[0], size[1], size[2]);
	}

	/** Post mit eigener Kopie des Fotos, wahlweise mit Angaben, womit es aufgenommen wurde. */
	private static void metaPost(LocalReelsBackend backend, BufferedImage photo, long at, ReelsMeta meta) throws Exception {
		String id = backend.newId(at);
		Path target = backend.newPhotoFile(id);
		Files.createDirectories(target.getParent());
		ImageIO.write(photo, "png", target.toFile());
		backend.post(id + ".png", photo.getWidth(), photo.getHeight(), photo.getHeight() > photo.getWidth(), false,
				"Aussicht hinter meiner Basis", "PixelDEV_CAM", at, meta);
	}

	/**
	 * Feed mit offenem Blatt. {@code armed}: 1 bzw. 2 drückt vorher einmal „Heute pausieren“ bzw. „Gestern retten“ (der
	 * Knopf fragt dann nach, eingelöst wird nichts).
	 */
	private void sheetShot(Reels reels, TrsModules modules, BufferedImage bg, Path out, String name, int[] size, PhoneHold hold,
			boolean slim, PhoneUi.Sheet sheet, int armed) throws Exception {
		modules.reels.hold.set(hold);
		PhoneUi ui = new PhoneUi(reels, host(size[0], size[1], size[2], slim));
		pump(ui, size[0], size[1], size[2], 1);
		ui.openSheet(sheet);
		if (armed != 0) ui.useProtection(armed == 2, false);
		write(ui, bg, out, name, size[0], size[1], size[2]);
	}

	private void shot(Reels reels, TrsModules modules, BufferedImage bg, Path out, String name, int w, int h, int gui,
			PhoneHold hold, boolean slim, boolean comments) throws Exception {
		modules.reels.hold.set(hold);
		PhoneUi ui = new PhoneUi(reels, host(w, h, gui, slim));
		if (comments) ui.keyPressed(0, UiKey.ENTER, false);
		write(ui, bg, out, name, w, h, gui);
	}

	/** Kamera öffnen (wahlweise Selfie), wahlweise auslösen und einen Text tippen; dann das Bild schreiben. */
	private void cameraShot(Reels reels, TrsModules modules, BufferedImage bg, Path out, String name, int w, int h, int gui,
			PhoneHold hold, boolean slim, boolean selfie, boolean compose) throws Exception {
		modules.reels.hold.set(hold);
		PhoneUi ui = new PhoneUi(reels, host(w, h, gui, slim));
		pump(ui, w, h, gui, 1);
		ui.openCamera();
		pump(ui, w, h, gui, 2);
		if (selfie) ui.setSelfie(true);
		if (compose) {
			ui.shutter();
			pump(ui, w, h, gui, 3);
			for (char ch : "Aussicht hinter meiner Basis".toCharArray()) ui.inputChar(ch);
		}
		write(ui, bg, out, name, w, h, gui);
	}

	private void pump(PhoneUi ui, int w, int h, int gui, int frames) {
		for (int i = 0; i < frames; i++) {
			AwtCanvas c = new AwtCanvas(w, h, textures);
			c.g.scale(gui, gui);
			ui.testOpenFully();
			ui.render(c, w / gui, h / gui, -1, -1);
			c.g.dispose();
		}
	}

	private void pageShot(Reels reels, TrsModules modules, BufferedImage bg, Path out, String name, PhoneHold hold, boolean slim)
			throws Exception {
		modules.reels.hold.set(hold);
		PhoneUi ui = new PhoneUi(reels, host(1920, 1080, 2, slim));
		ui.current();
		ui.mouseScrolled(0, 0, -1);
		write(ui, bg, out, name, 1920, 1080, 2);
	}

	private void write(PhoneUi ui, BufferedImage bg, Path out, String name, int w, int h, int gui) throws Exception {
		AwtCanvas c = null;
		// Drei Bilder: Fotos werden im ersten angefordert und in den nächsten hochgeladen.
		for (int i = 0; i < 4; i++) {
			c = new AwtCanvas(w, h, textures);
			if (bg != null) c.g.drawImage(bg, 0, 0, w, h, null);
			else c.fill(0, 0, w, h, 0xFF6FA8DC);
			// Wie im Spiel: die Fläche arbeitet in GUI-Einheiten.
			c.g.scale(gui, gui);
			ui.testOpenFully();
			ui.render(c, w / gui, h / gui, -1, -1);
		}
		ImageIO.write(c.image, "png", out.resolve(name + ".png").toFile());
		ui.dispose();
	}
}
