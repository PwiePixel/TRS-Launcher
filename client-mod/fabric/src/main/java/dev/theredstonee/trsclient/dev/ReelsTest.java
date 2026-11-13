package dev.theredstonee.trsclient.dev;

// Selbsttest MC-Reels (-PtrsAutotestOnly=reels) – vorerst nur Fabric 26.2, sonst ist diese Datei leer.
//? if >=26.2 && <26.3 {
/*import dev.theredstonee.trsclient.TrsClient;
import dev.theredstonee.trsclient.compat.ChatLines;
import dev.theredstonee.trsclient.compat.Keys;
import dev.theredstonee.trsclient.compat.Mc;
import dev.theredstonee.trsclient.core.config.TrsConfig;
import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.reels.ArmRig;
import dev.theredstonee.trsclient.core.reels.LocalReelsBackend;
import dev.theredstonee.trsclient.core.reels.PhoneHold;
import dev.theredstonee.trsclient.core.reels.PhoneLayout;
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.reels.ReelsBackend;
import dev.theredstonee.trsclient.core.reels.ReelsPost;
import dev.theredstonee.trsclient.core.ui.UiKey;
import dev.theredstonee.trsclient.core.ui.reels.PhoneUi;
import dev.theredstonee.trsclient.core.wardrobe.PngWriter;
import dev.theredstonee.trsclient.reels.ReelsHooks;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/^*
 * Selbsttest „MC-Reels“ ({@code -PtrsAutotestOnly=reels}, eigener Feed-Ordner über
 * {@code -PtrsSys.trsclient.reels.dir=trs-reels-autotest}): Welt mit festem Startwert, Handy quer und hochkant, bei
 * GUI-Skalierung 1/2/4 und in vier Fenstergrößen, Feed blättern, Like per echtem Klick, Kommentar, Drehen und
 * Wegstecken per echter Taste. Jede Prüfung schreibt {@code [Autotest] Reels: OK …} oder {@code FEHLER …} ins Log;
 * die Bilder liegen als {@code screenshots/trsclient-<Version>-reels-*.png} daneben und gehören angesehen (Arme,
 * Hand weg, Minimap weg, Schnellleiste da).
 *
 * <p>Das Handy wird mit {@link ReelsHooks#open()} geöffnet: die Handy-Taste fragt den echten Tastenzustand ab, den
 * ein Test nicht setzen kann. Kamera und Posten kommen mit dem nächsten Bauschritt dazu.
 *^/
public final class ReelsTest {
	private static final String WORLD = "trs-reels-" + AutoTest.MC_VERSION;
	private static final int[] SCALES = {1, 2, 4};
	private static final String[] SCALE_SHOTS = {"03-quer-gui1", "04-quer-gui2", "05-quer-gui4"};
	private static final int[][] WINDOWS = {{1280, 720}, {854, 480}, {1024, 768}};
	private static final String[] WINDOW_SHOTS = {"06-quer-1280", "07-quer-854", "08-quer-1024"};
	private static final String COMMENT = "Sieht super aus!";
	/^* Nach so vielen Ticks bricht der Test ab, statt Gradle hängen zu lassen. *^/
	private static final int WATCHDOG_TICKS = 9000;

	private int phase;
	private int wait;
	private int waited;
	private int ticks;
	private int index;
	private boolean applied;
	private int checks;
	private int failures;
	private boolean stopped;
	private TrsConfig before;
	private int guiScaleBefore;
	private PhoneUi ui;
	private final int[][] phoneAtScale = new int[SCALES.length][];
	private String likedPost;

	public static void install() {
		ReelsTest test = new ReelsTest();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(test::tick);
	}

	private void tick(Minecraft mc) {
		if (stopped) return;
		if (++ticks > WATCHDOG_TICKS) {
			check("Test in " + WATCHDOG_TICKS + " Ticks fertig (hing in Phase " + phase + ")", false);
			finish(mc);
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		int current = phase;
		try {
			step(mc);
		} catch (RuntimeException | LinkageError | IOException e) {
			TrsClient.LOGGER.error("[Autotest] Reels: FEHLER in Phase {} – weiter", current, e);
			failures++;
			applied = false;
			if (phase == current) phase++;
			wait = 5;
		}
	}

	private void check(String what, boolean pass) {
		checks++;
		if (!pass) failures++;
		TrsClient.LOGGER.info("[Autotest] Reels: {} – {}", pass ? "OK" : "FEHLER", what);
	}

	private boolean waitFor(boolean ready, int maxTries) {
		if (!ready && waited++ < maxTries) {
			wait = 5;
			return false;
		}
		waited = 0;
		return true;
	}

	private void next(int ticksToWait) {
		phase++;
		wait = ticksToWait;
	}

	private void step(Minecraft mc) throws IOException {
		TrsModules modules = TrsClient.get().modules();
		Reels reels = Reels.get();
		switch (phase) {
			case 0: {
				if (Mc.overlay() != null || Mc.screen() == null) return; // Ressourcen laden noch
				mc.options.pauseOnLostFocus = false;
				mc.options.onboardAccessibility = false;
				mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
				before = modules.registry.capture();
				guiScaleBefore = mc.options.guiScale().get();
				I18n.override("de");
				check("MC-Reels ist gestartet (Reels.get())", reels != null);
				check("Modul ist auf Fabric 26.2 sichtbar und ab Werk aus", modules.reels.reels.available());
				if (reels != null) {
					check("Feed des Tests ist leer (eigener Ordner: " + dir(reels) + ")", reels.backend().feed().isEmpty());
				}
				windowSize(1920, 1080);
				Mc.setScreen(null);
				AutoTest.startWorld(mc, WORLD, 4242L);
				next(0);
				return;
			}
			case 1:
				if (!waitFor(mc.level != null && mc.player != null && Mc.screen() == null, 600)) return;
				next(60);
				return;
			case 2: {
				// Feste Szene: Mittag, klar, fester Blick. Ohne Rückmeldung im Chat.
				silent(mc, "time set noon");
				silent(mc, "weather clear");
				silent(mc, "execute as @p at @s run tp @s ~ ~ ~ 20 6");
				modules.reels.reels.setEnabled(true);
				modules.reels.hold.set(PhoneHold.LANDSCAPE);
				modules.minimap.setEnabled(true);
				modules.fps.setEnabled(true);
				modules.coords.setEnabled(true);
				// Die Vorschau nach einem Bildschirmfoto läge sonst auf den nächsten Bildern.
				modules.comfort.shotToast.set(false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				ChatLines.addMessage(Mc.text("MC-Reels-Selbsttest: Diese Chat-Zeile darf später auf keinem Foto sein."));
				next(60);
				return;
			}
			case 3:
				TrsClient.LOGGER.info("[Autotest] Reels: Fenster {}×{}, GUI-Skalierung {}", Mc.window().getWidth(),
						Mc.window().getHeight(), Mc.window().getGuiScale());
				AutoTest.shot(mc, "trsclient-reels-01-vorher");
				next(15);
				return;
			case 4: {
				boolean opened = ReelsHooks.open();
				ui = ReelsHooks.phone();
				check("Handy geht auf (ReelsHooks.open)", opened && ui != null);
				if (ui == null) {
					finish(mc);
					return;
				}
				next(30);
				return;
			}
			case 5: {
				check("Handy ist offen", ReelsHooks.phoneOpen());
				check("Haken für die Spielerhand greift (renderItemInHand)", ReelsHooks.handHookSeen());
				check("Haken für den Block-Umriss greift (shouldRenderBlockOutline)", ReelsHooks.outlineHookSeen());
				int[] phone = ui.testSpot("phone");
				int w = Mc.window().getWidth();
				double share = phone == null ? 0 : phone[2] / (double) w;
				check("Handy quer ist 59–61 % der Fensterbreite (" + String.format(Locale.ROOT, "%.1f", share * 100) + " %)",
						share >= 0.59 && share <= 0.61);
				armCheck("quer, leerer Feed");
				check("Haltung quer", ui.testHold() == PhoneHold.LANDSCAPE);
				AutoTest.shot(mc, "trsclient-reels-02-quer-leer");
				next(15);
				return;
			}
			case 6:
				seed(reels, mc.getUser().getName());
				check("drei Test-Posts im Feed", reels.backend().feed().size() == 3);
				next(40);
				return;
			case 7: {
				// GUI-Skalierung 1, 2, 4: das Handy muss jedes Mal an derselben Stelle gleich groß sein.
				if (!applied) {
					guiScale(mc, SCALES[index]);
					applied = true;
					wait = 25;
					return;
				}
				applied = false;
				phoneAtScale[index] = ui.testSpot("phone");
				check("GUI-Skalierung " + SCALES[index] + " ist gesetzt (ist " + Mc.window().getGuiScale() + ")",
						Mc.window().getGuiScale() == SCALES[index]);
				TrsClient.LOGGER.info("[Autotest] Reels: GUI {} → Handy {}, Bildschirm {}, {} Arm-Blits", SCALES[index],
						Arrays.toString(phoneAtScale[index]), Arrays.toString(ui.testSpot("screen")), ui.testArmBlits());
				armCheck("quer, GUI " + SCALES[index]);
				AutoTest.shot(mc, "trsclient-reels-" + SCALE_SHOTS[index]);
				wait = 15;
				if (++index == SCALES.length) {
					index = 0;
					phase++;
				}
				return;
			}
			case 8: {
				boolean same = phoneAtScale[0] != null;
				for (int[] r : phoneAtScale) same &= Arrays.equals(r, phoneAtScale[0]);
				check("Handy bei GUI 1, 2 und 4 gleich groß an derselben Stelle (" + Arrays.toString(phoneAtScale[0]) + ")", same);
				guiScale(mc, 0);
				next(25);
				return;
			}
			case 9: {
				// Andere Fenstergrößen (16:9 klein, sehr klein, 4:3): die Arme laufen immer unten aus dem Bild.
				int[] size = WINDOWS[index];
				if (!applied) {
					windowSize(size[0], size[1]);
					applied = true;
					wait = 40;
					return;
				}
				applied = false;
				int w = Mc.window().getWidth(), h = Mc.window().getHeight();
				check("Fenster ist " + size[0] + "×" + size[1] + " (ist " + w + "×" + h + ")", w == size[0] && h == size[1]);
				int[] phone = ui.testSpot("phone");
				check("Handy liegt ganz im Fenster " + w + "×" + h + " (" + Arrays.toString(phone) + ")",
						phone != null && phone[0] >= 0 && phone[1] >= 0 && phone[0] + phone[2] <= w && phone[1] + phone[3] <= h);
				armCheck("quer, Fenster " + w + "×" + h);
				AutoTest.shot(mc, "trsclient-reels-" + WINDOW_SHOTS[index]);
				wait = 15;
				if (++index == WINDOWS.length) {
					index = 0;
					phase++;
				}
				return;
			}
			case 10:
				windowSize(1920, 1080);
				next(40);
				return;
			case 11: {
				check("Fenster wieder 1920×1080", Mc.window().getWidth() == 1920 && Mc.window().getHeight() == 1080);
				// Mausrad nach unten über dem Foto: nächster Post (über den Bildschirm der Version, wie ein echtes Rad).
				int[] photo = spot("screen");
				double gs = Mc.window().getGuiScale();
				Mc.screen().mouseScrolled((photo[0] + photo[2] / 2.0) / gs, (photo[1] + photo[3] / 2.0) / gs, 0, -1);
				check("Mausrad blättert zum zweiten Post", ui.testPage() == 1);
				next(15);
				return;
			}
			case 12: {
				List<ReelsPost> feed = reels.backend().feed();
				likedPost = feed.size() > 1 ? feed.get(1).id : null;
				clickSpot(mc, "heart");
				next(10);
				return;
			}
			case 13: {
				ReelsPost post = byId(reels, likedPost);
				boolean liked = post != null && post.likedByMe && post.likes == 1;
				check("echter Klick auf das Herz: Post ist geliked", liked);
				if (!liked) {
					// Weiter mit dem direkten Weg, damit die folgenden Bilder stimmen.
					int[] heart = spot("heart");
					double gs = Mc.window().getGuiScale();
					ui.inputClick((heart[0] + heart[2] / 2.0) / gs, (heart[1] + heart[3] / 2.0) / gs, 0);
				}
				next(10);
				return;
			}
			case 14:
				// Zweiter Post ist hochkant fotografiert: im Querformat ganz zu sehen (nicht beschnitten).
				AutoTest.shot(mc, "trsclient-reels-09-quer-feed");
				next(15);
				return;
			case 15: {
				// Enter öffnet die Kommentare (echte Taste), tippen geht nur über die Oberfläche.
				AutoTest.key(mc, Keys.code("key.keyboard.enter"));
				check("Enter öffnet die Kommentare", ui.testView() == PhoneUi.View.COMMENTS);
				for (int i = 0; i < COMMENT.length(); i++) ui.inputChar(COMMENT.charAt(i));
				next(10);
				return;
			}
			case 16:
				AutoTest.shot(mc, "trsclient-reels-09b-quer-kommentar-tippen");
				next(15);
				return;
			case 17: {
				AutoTest.key(mc, Keys.code("key.keyboard.enter"));
				ReelsPost post = byId(reels, likedPost);
				check("Enter sendet den Kommentar (1 Kommentar am Post)", post != null && post.comments.size() == 1
						&& COMMENT.equals(post.comments.get(0).text));
				next(10);
				return;
			}
			case 18:
				AutoTest.shot(mc, "trsclient-reels-09c-quer-kommentare");
				next(15);
				return;
			case 19: {
				AutoTest.key(mc, Keys.code("key.keyboard.escape"));
				check("Esc schließt nur die Kommentare, das Handy bleibt offen",
						ui.testView() == PhoneUi.View.FEED && ReelsHooks.phoneOpen() && !ui.isClosing());
				next(10);
				return;
			}
			case 20: {
				// Handy drehen: echte Taste R (Standard).
				AutoTest.key(mc, Keys.code("key.keyboard.r"));
				boolean rotated = ui.testHold() == PhoneHold.PORTRAIT;
				check("Taste R dreht das Handy auf hochkant", rotated);
				if (!rotated) ui.inputKey(Keys.code("key.keyboard.r"), UiKey.NONE, false);
				check("Haltung wird gemerkt", modules.reels.hold.get() == ui.testHold());
				next(30);
				return;
			}
			case 21: {
				armCheck("hochkant");
				int[] phone = ui.testSpot("phone");
				int w = Mc.window().getWidth(), h = Mc.window().getHeight();
				check("Handy hochkant liegt ganz im Fenster (" + Arrays.toString(phone) + ")",
						phone != null && phone[0] >= 0 && phone[1] >= 0 && phone[0] + phone[2] <= w && phone[1] + phone[3] <= h
								&& phone[3] > phone[2]);
				TrsClient.LOGGER.info("[Autotest] Reels: Skin des Spielers ist {}", playerSlim(mc) ? "schlank" : "breit");
				AutoTest.shot(mc, "trsclient-reels-10-hochkant-feed");
				next(15);
				return;
			}
			case 22:
				ui.testForceSlim(Boolean.TRUE);
				next(10);
				return;
			case 23:
				AutoTest.shot(mc, "trsclient-reels-11-hochkant-slim");
				next(15);
				return;
			case 24:
				ui.testForceSlim(Boolean.FALSE);
				next(10);
				return;
			case 25:
				AutoTest.shot(mc, "trsclient-reels-11b-hochkant-breit");
				next(15);
				return;
			case 26: {
				ui.testForceSlim(null);
				// Zurück zum ersten Post (quer fotografiert): hochkant ganz zu sehen.
				int[] screen = spot("screen");
				double gs = Mc.window().getGuiScale();
				Mc.screen().mouseScrolled((screen[0] + screen[2] / 2.0) / gs, (screen[1] + screen[3] / 2.0) / gs, 0, 1);
				check("Mausrad nach oben blättert zurück zum ersten Post", ui.testPage() == 0);
				next(15);
				return;
			}
			case 27:
				AutoTest.shot(mc, "trsclient-reels-11c-hochkant-querfoto");
				next(15);
				return;
			case 28: {
				AutoTest.key(mc, Keys.code("key.keyboard.r"));
				boolean rotated = ui.testHold() == PhoneHold.LANDSCAPE;
				check("Taste R dreht zurück auf quer", rotated);
				if (!rotated) ui.inputKey(Keys.code("key.keyboard.r"), UiKey.NONE, false);
				// Den anderen Arm-Typ auch quer zeigen (schlanker Skin: mittlere Spalte doppelt, Arm bleibt 4 breit).
				ui.testForceSlim(Boolean.valueOf(!playerSlim(mc)));
				next(30);
				return;
			}
			case 29:
				AutoTest.shot(mc, "trsclient-reels-11d-quer-" + (playerSlim(mc) ? "breit" : "slim"));
				next(15);
				return;
			case 30:
				ui.testForceSlim(null);
				// Versuch: leichter Weichzeichner hinter dem Handy (ab Werk aus).
				ReelsHooks.testBlur(true);
				next(15);
				return;
			case 31:
				AutoTest.shot(mc, "trsclient-reels-11e-quer-weichzeichner");
				next(15);
				return;
			case 32:
				ReelsHooks.testBlur(false);
				next(10);
				return;
			case 33: {
				// Wegstecken mit der Handy-Taste (echte Taste H; sie gilt, weil sie seit dem Öffnen „los“ war).
				AutoTest.key(mc, Keys.code("key.keyboard.h"));
				check("Taste H steckt das Handy weg (Schließen läuft)", ui.isClosing() || !ReelsHooks.phoneOpen());
				if (!ui.isClosing() && ReelsHooks.phoneOpen()) ui.requestClose();
				next(30);
				return;
			}
			case 34: {
				check("Handy ist zu", !ReelsHooks.phoneOpen() && Mc.screen() == null);
				check("aufgeräumt (dispose)", ui.disposed());
				check("Handy-Taste ist wieder frei (Sperre gelöst, weil die Taste los ist)", !reels.keyGate().blocked());
				Path feed = dir(reels) == null ? null : dir(reels).resolve("feed.json");
				String json = feed != null && Files.isRegularFile(feed)
						? new String(Files.readAllBytes(feed), java.nio.charset.StandardCharsets.UTF_8) : "";
				check("feed.json ist gesichert und enthält den Kommentar (" + feed + ")", json.contains(COMMENT));
				next(20);
				return;
			}
			case 35:
				AutoTest.shot(mc, "trsclient-reels-17-nachher");
				next(15);
				return;
			case 36: {
				// Aus der Außenansicht: das Handy stellt für die Dauer auf Ich-Sicht.
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				boolean opened = ReelsHooks.open();
				ui = ReelsHooks.phone();
				check("Handy geht aus der Außenansicht auf und stellt auf Ich-Sicht",
						opened && ui != null && mc.options.getCameraType() == CameraType.FIRST_PERSON);
				next(20);
				return;
			}
			case 37: {
				// Etwas anderes ersetzt den Bildschirm (wie Tod oder Verbindungsende): trotzdem aufräumen.
				PhoneUi open = ui;
				Mc.setScreen(null);
				check("fremdes Schließen räumt auf (removed → dispose)", open != null && open.disposed());
				check("Perspektive ist zurückgestellt", mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				next(10);
				return;
			}
			default:
				finish(mc);
		}
	}

	/^* Ende: Einstellungen zurück, Ergebnis ins Log, Spiel beenden. *^/
	private void finish(Minecraft mc) {
		if (stopped) return;
		stopped = true;
		try {
			ReelsHooks.testBlur(false);
			if (before != null) TrsClient.get().modules().registry.apply(before);
			guiScale(mc, guiScaleBefore);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		} catch (RuntimeException e) {
			TrsClient.LOGGER.error("[Autotest] Reels: Zurückstellen fehlgeschlagen", e);
		}
		if (failures == 0) TrsClient.LOGGER.info("[Autotest] Reels: ALLES OK ({} Prüfungen)", checks);
		else TrsClient.LOGGER.info("[Autotest] Reels: {} FEHLER bei {} Prüfungen", failures, checks);
		mc.stop();
	}

	// --- Hilfen ---

	/^* Arm-Enden beider Arme liegen unter dem Fensterrand (gerechnet; zu sehen ist es auf dem Bild). *^/
	private void armCheck(String label) {
		int w = Mc.window().getWidth(), h = Mc.window().getHeight();
		PhoneLayout l = PhoneLayout.of(w, h, ui.testHold());
		float left = ArmRig.of(l, true).lowestEndY() - h;
		float right = ArmRig.of(l, false).lowestEndY() - h;
		check(String.format(Locale.ROOT, "%s: Arm-Enden %.1f / %.1f px unter dem Fensterrand (%d×%d, Verlängerung %.0f)", label,
				left, right, w, h, ArmRig.extension(l)), left >= ArmRig.MARGIN_PX && right >= ArmRig.MARGIN_PX);
	}

	private int[] spot(String name) {
		int[] r = ui.testSpot(name);
		if (r == null) throw new IllegalStateException("Stelle fehlt: " + name);
		return r;
	}

	/^* Echter Linksklick mitten auf eine Stelle des Handys (Fensterpixel → GUI-Einheiten). *^/
	private void clickSpot(Minecraft mc, String name) {
		int[] r = spot(name);
		double gs = Mc.window().getGuiScale();
		AutoTest.click(mc, (r[0] + r[2] / 2.0) / gs, (r[1] + r[3] / 2.0) / gs);
	}

	private static ReelsPost byId(Reels reels, String id) {
		if (id == null) return null;
		for (ReelsPost p : reels.backend().feed()) if (id.equals(p.id)) return p;
		return null;
	}

	private static Path dir(Reels reels) {
		ReelsBackend b = reels.backend();
		return b instanceof LocalReelsBackend ? ((LocalReelsBackend) b).dir() : null;
	}

	private static boolean playerSlim(Minecraft mc) {
		return mc.player != null
				&& mc.player.getSkin().model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
	}

	/^* Befehl als Server, ohne Rückmeldung im Chat. *^/
	private static void silent(Minecraft mc, String command) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null) return;
		server.execute(() -> server.getCommands().performPrefixedCommand(
				server.createCommandSourceStack().withSuppressedOutput(), command));
	}

	private static void windowSize(int w, int h) {
		org.lwjgl.glfw.GLFW.glfwSetWindowSize(Mc.window().handle(), w, h);
	}

	private static void guiScale(Minecraft mc, int scale) {
		mc.options.guiScale().set(scale);
		mc.resizeGui();
	}

	/^*
	 * Drei Posts mit gemalten Testbildern: der neueste quer (eigener), der zweite hochkant (eigener), der älteste quer
	 * von jemand anderem. Alle von heute, damit der Streak 1 ist.
	 *^/
	private static void seed(Reels reels, String self) throws IOException {
		ReelsBackend backend = reels.backend();
		long now = System.currentTimeMillis();
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(now);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long dayStart = cal.getTimeInMillis();
		long[] age = {5 * 3600000L, 2 * 3600000L, 3 * 60000L};
		int[][] size = {{1280, 720}, {608, 1080}, {1280, 720}};
		String[] author = {"Steve_Baut", self, self};
		String[] caption = {"Erste Nacht überstanden – das Haus steht!", "Abendrot über dem Hügel, hochkant fotografiert.",
				"Schaut mal, wo ich gelandet bin!"};
		for (int i = 0; i < age.length; i++) {
			long at = Math.max(dayStart + i * 1000L, now - age[i]);
			String id = ReelsPost.newId(at, TimeZone.getDefault(), 0x7e50 + i);
			Path file = backend.newPhotoFile(id);
			if (file == null) throw new IOException("kein Foto-Ordner");
			Files.createDirectories(file.getParent());
			int w = size[i][0], h = size[i][1];
			Files.write(file, PngWriter.write(w, h, picture(w, h, i)));
			if (backend.post(id + ".png", w, h, h > w, false, caption[i], author[i], at) == null) {
				throw new IOException("Post nicht angelegt: " + id);
			}
		}
		reels.saveLater();
	}

	/^* Gemaltes Testbild im Klötzchen-Stil: Himmel, Sonne bzw. Mond, Hügel aus Blöcken, ein Baum, ein Haus. *^/
	private static int[] picture(int w, int h, int kind) {
		int[] px = new int[w * h];
		int[][] sky = {{0xFF0B1730, 0xFF2C4C80}, {0xFFE0703C, 0xFFFFD8A0}, {0xFF3F86E0, 0xFFC4E6FF}};
		int[] grass = {0xFF2E6B3A, 0xFF7A9A3C, 0xFF58B04A};
		int[] dirt = {0xFF4A3524, 0xFF7A5234, 0xFF8A5E3A};
		int b = Math.max(8, Math.min(w, h) / 14);
		int horizon = h * 56 / 100;
		for (int y = 0; y < h; y++) {
			int c = lerp(sky[kind][0], sky[kind][1], Math.min(1f, y / (float) Math.max(1, horizon)));
			Arrays.fill(px, y * w, (y + 1) * w, c);
		}
		// Sonne bzw. Mond
		int sun = kind == 0 ? 0xFFE8ECF4 : kind == 1 ? 0xFFFFF0B0 : 0xFFFFF7C0;
		rect(px, w, h, w * 70 / 100, h * 12 / 100, b * 2, b * 2, sun);
		// Hügel: je Spalte eine Höhe in ganzen Blöcken
		for (int bx = 0; bx * b < w; bx++) {
			int top = horizon / b + (int) Math.round(1.6 * Math.sin(bx * 0.55 + kind * 2.1) + 0.9 * Math.sin(bx * 1.3 + kind));
			for (int by = top; by * b < h; by++) {
				boolean surface = by == top;
				int base = surface ? grass[kind] : dirt[kind];
				int shade = ((bx * 7 + by * 13 + kind * 5) % 5) - 2;
				rect(px, w, h, bx * b, by * b, b, b, tone(base, shade * 6));
				if (surface) rect(px, w, h, bx * b, by * b, b, Math.max(2, b / 4), tone(grass[kind], 14));
			}
		}
		// Baum
		int tx = w * 22 / 100 / b, ty = horizon / b;
		rect(px, w, h, tx * b, (ty - 4) * b, b, 5 * b, 0xFF5A4028);
		rect(px, w, h, (tx - 2) * b, (ty - 7) * b, 5 * b, 3 * b, kind == 1 ? 0xFFB85A9A : 0xFF2F7D3A);
		rect(px, w, h, (tx - 1) * b, (ty - 8) * b, 3 * b, b, kind == 1 ? 0xFFD07AB4 : 0xFF3F9448);
		// Haus
		int hx = w * 58 / 100 / b, hy = horizon / b;
		rect(px, w, h, hx * b, (hy - 3) * b, 4 * b, 4 * b, 0xFFB08A5A);
		rect(px, w, h, (hx - 1) * b, (hy - 4) * b, 6 * b, b, 0xFF7A3A2A);
		rect(px, w, h, (hx + 1) * b, (hy - 1) * b, b, 2 * b, 0xFF4A3020);
		rect(px, w, h, (hx + 2) * b + b / 4, (hy - 2) * b, b, b, kind == 0 ? 0xFFFFD860 : 0xFF9AD0F0);
		return px;
	}

	private static void rect(int[] px, int w, int h, int x, int y, int rw, int rh, int argb) {
		int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(w, x + rw), y1 = Math.min(h, y + rh);
		for (int yy = y0; yy < y1; yy++) {
			if (x0 < x1) Arrays.fill(px, yy * w + x0, yy * w + x1, argb);
		}
	}

	private static int lerp(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	private static int tone(int argb, int delta) {
		int r = Math.max(0, Math.min(255, ((argb >> 16) & 255) + delta));
		int g = Math.max(0, Math.min(255, ((argb >> 8) & 255) + delta));
		int b = Math.max(0, Math.min(255, (argb & 255) + delta));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
*///?}
