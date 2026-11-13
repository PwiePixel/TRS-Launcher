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
import dev.theredstonee.trsclient.core.reels.PhotoFrame;
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.reels.ReelsBackend;
import dev.theredstonee.trsclient.core.reels.ReelsPost;
import dev.theredstonee.trsclient.core.ui.UiKey;
import dev.theredstonee.trsclient.core.ui.reels.PhoneUi;
import dev.theredstonee.trsclient.core.wardrobe.PngWriter;
import dev.theredstonee.trsclient.reels.ReelsHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
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
 * GUI-Skalierung 1/2/4 und in mehreren Fenstergrößen (auch hoch), Feed blättern, Like per echtem Klick, Kommentar, Drehen und
 * Wegstecken per echter Taste. Jede Prüfung schreibt {@code [Autotest] Reels: OK …} oder {@code FEHLER …} ins Log;
 * die Bilder liegen als {@code screenshots/trsclient-<Version>-reels-*.png} daneben und gehören angesehen (Arme,
 * Hand weg, Minimap weg, Schnellleiste da).
 *
 * <p>Kamera: „Foto“ hochkant (Sucher, Auslöser per echtem Klick, „Neuer Post“ mit Text, Posten mit Enter), „Selfie“
 * (umschalten, auslösen mit Enter – ein zweites Enter gleich danach postet nicht –, „Nochmal“ löscht das Foto, zweites
 * Selfie posten), dasselbe quer bis „Neuer Post“ und Esc. Beim Selfie steht hinter dem Handy weiter die normale Sicht
 * (Bild 15a vor und 15 nach dem Umschalten: außerhalb des Handys gleich, keine eigene Figur zwischen den Armen). Die beiden geposteten Fotos liegen danach als {@code …-reels-foto.png} und {@code …-reels-selfie.png} bei
 * den Bildern und gehören ebenfalls angesehen: darauf dürfen weder HUD noch Chat, Handy, Arme, Schleier oder ein
 * Block-Umriss sein (der Blick zeigt beim Foto auf einen Block in Reichweite, eine Chat-Zeile steht auf dem Bildschirm).
 *
 * <p>Danach: ein anderer Bildschirm sperrt die Handy-Taste, bis sie los war; Freelook im Umschalt-Modus endet beim
 * Herausholen (die Kamera folgt wieder dem Spieler, die Außenansicht von davor kommt zurück); mit offener Debug-Anzeige
 * (F3) kein Achsenkreuz im Sucher (Bild 18 ansehen); beim Selfie die Fenstergröße ändern (Bild 19: keine eigene Figur
 * hinter dem Handy) und mit der Handy-Taste wegstecken, ohne dass ein umgedrehtes Bild stehen bleibt.
 *
 * <p>Das Handy wird mit {@link ReelsHooks#open()} geöffnet: die Handy-Taste fragt den echten Tastenzustand ab, den
 * ein Test nicht setzen kann.
 *^/
public final class ReelsTest {
	private static final String WORLD = "trs-reels-" + AutoTest.MC_VERSION;
	private static final int[] SCALES = {1, 2, 4};
	private static final String[] SCALE_SHOTS = {"03-quer-gui1", "04-quer-gui2", "05-quer-gui4"};
	/^* Fenstergrößen: 16:9 klein, sehr klein, 4:3 und ein hohes Fenster (dort werden die Arme verlängert). *^/
	private static final int[][] WINDOWS = {{1280, 720}, {854, 480}, {1024, 768}, {800, 1000}};
	private static final String[] WINDOW_SHOTS = {"06-quer-1280", "07-quer-854", "08-quer-1024", "08b-quer-800x1000"};
	private static final int[][] PORTRAIT_WINDOWS = {{1024, 768}, {854, 480}, {800, 1000}};
	private static final String[] PORTRAIT_WINDOW_SHOTS = {"10b-hochkant-1024", "10c-hochkant-854", "10d-hochkant-800x1000"};
	/^* Feste Szene in der Testwelt (x, z) und Blickrichtung: eine Ebene an einem Fluss. *^/
	private static final int[] SCENE = {373, 276};
	private static final float SCENE_YAW = -60f;
	private static final String COMMENT = "Sieht super aus!";
	/^* Blickrichtungen für das Foto: {Grad zur Seite von der Szene aus, Grad nach unten}. *^/
	private static final float[][] AIMS = {{0, 24}, {-30, 24}, {-55, 26}, {35, 26}, {-90, 28}, {180, 26}, {0, 42}, {0, 58}};
	private static final String CAPTION = "Aussicht am Fluss";
	private static final String SELFIE_CAPTION = "Ich war auch da";
	/^* Nach so vielen Ticks bricht der Test ab, statt Gradle hängen zu lassen. *^/
	private static final int WATCHDOG_TICKS = 14000;

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
	private int feedBefore;
	private Path photoFile;
	private Path selfieFile;
	private Path retryFile;
	private float yawBefore;
	private boolean crosshairOn;
	private int crosshairsHidden;
	private int backdropMisses;
	private int turnedWithoutPhone;

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
				// Feste Szene: Mittag, klar, fester Blick – in einer grünen Gegend (der Startpunkt dieser Welt liegt im
				// Schnee; Weißes direkt neben den Händen soll auf den Vergleichsbildern nicht sein). Ohne Rückmeldung im Chat.
				silent(mc, "time set noon");
				silent(mc, "weather clear");
				greenScene(mc);
				modules.reels.reels.setEnabled(true);
				modules.reels.hold.set(PhoneHold.LANDSCAPE);
				modules.minimap.setEnabled(true);
				modules.fps.setEnabled(true);
				modules.coords.setEnabled(true);
				// Die Vorschau nach einem Bildschirmfoto läge sonst auf den nächsten Bildern.
				modules.comfort.shotToast.set(false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				// Die neue Gegend muss erst geladen und gezeichnet sein.
				next(240);
				return;
			}
			case 3:
				ChatLines.addMessage(Mc.text("MC-Reels-Selbsttest: Diese Chat-Zeile darf später auf keinem Foto sein."));
				next(20);
				return;
			case 4:
				TrsClient.LOGGER.info("[Autotest] Reels: Fenster {}×{}, GUI-Skalierung {}", Mc.window().getWidth(),
						Mc.window().getHeight(), Mc.window().getGuiScale());
				AutoTest.shot(mc, "trsclient-reels-01-vorher");
				next(15);
				return;
			case 5: {
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
			case 6: {
				check("Handy ist offen", ReelsHooks.phoneOpen());
				check("Haken für die Spielerhand greift (renderItemInHand)", ReelsHooks.handHookSeen());
				// Der Arm wurde vor dem Öffnen normal gezeichnet – dabei kam der tiefere Haken vorbei (mit Iris der einzige).
				check("Haken für Arm und Gegenstand greift (submitArmWithItem)", ReelsHooks.armHookSeen());
				// Der Weg von Iris mit Shaderpaket: submitHandsWithItems direkt, an renderItemInHand vorbei. Bei offenem
				// Handy muss der Arm trotzdem wegbleiben (eingereicht wird in einen Wegwerf-Speicher).
				int hiddenBefore = ReelsHooks.armsHidden();
				mc.gameRenderer.itemInHandRenderer.submitHandsWithItems(1f, new PoseStack(), new SubmitNodeStorage(), mc.player, 0xF000F0);
				check("Hand bleibt auch auf dem direkten Weg weg, den Iris nimmt (submitHandsWithItems)",
						ReelsHooks.armsHidden() > hiddenBefore);
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
			case 7:
				seed(reels, mc.getUser().getName());
				check("drei Test-Posts im Feed", reels.backend().feed().size() == 3);
				next(40);
				return;
			case 8: {
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
			case 9: {
				boolean same = phoneAtScale[0] != null;
				for (int[] r : phoneAtScale) same &= Arrays.equals(r, phoneAtScale[0]);
				check("Handy bei GUI 1, 2 und 4 gleich groß an derselben Stelle (" + Arrays.toString(phoneAtScale[0]) + ")", same);
				guiScale(mc, 0);
				next(25);
				return;
			}
			case 10:
				// Andere Fenstergrößen: die Arme laufen immer unten aus dem Bild.
				if (windows(mc, WINDOWS, WINDOW_SHOTS, "quer")) phase++;
				return;
			case 11:
				windowSize(1920, 1080);
				next(40);
				return;
			case 12: {
				check("Fenster wieder 1920×1080", Mc.window().getWidth() == 1920 && Mc.window().getHeight() == 1080);
				// Mausrad nach unten über dem Foto: nächster Post (über den Bildschirm der Version, wie ein echtes Rad).
				int[] photo = spot("screen");
				double gs = Mc.window().getGuiScale();
				Mc.screen().mouseScrolled((photo[0] + photo[2] / 2.0) / gs, (photo[1] + photo[3] / 2.0) / gs, 0, -1);
				check("Mausrad blättert zum zweiten Post", ui.testPage() == 1);
				next(15);
				return;
			}
			case 13: {
				List<ReelsPost> feed = reels.backend().feed();
				likedPost = feed.size() > 1 ? feed.get(1).id : null;
				clickSpot(mc, "heart");
				next(10);
				return;
			}
			case 14: {
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
			case 15:
				// Zweiter Post ist hochkant fotografiert: im Querformat ganz zu sehen (nicht beschnitten).
				AutoTest.shot(mc, "trsclient-reels-09-quer-feed");
				next(15);
				return;
			case 16: {
				// Enter öffnet die Kommentare (echte Taste), tippen geht nur über die Oberfläche.
				AutoTest.key(mc, Keys.code("key.keyboard.enter"));
				check("Enter öffnet die Kommentare", ui.testView() == PhoneUi.View.COMMENTS);
				for (int i = 0; i < COMMENT.length(); i++) ui.inputChar(COMMENT.charAt(i));
				next(10);
				return;
			}
			case 17:
				AutoTest.shot(mc, "trsclient-reels-09b-quer-kommentar-tippen");
				next(15);
				return;
			case 18: {
				AutoTest.key(mc, Keys.code("key.keyboard.enter"));
				ReelsPost post = byId(reels, likedPost);
				check("Enter sendet den Kommentar (1 Kommentar am Post)", post != null && post.comments.size() == 1
						&& COMMENT.equals(post.comments.get(0).text));
				next(10);
				return;
			}
			case 19:
				AutoTest.shot(mc, "trsclient-reels-09c-quer-kommentare");
				next(15);
				return;
			case 20: {
				AutoTest.key(mc, Keys.code("key.keyboard.escape"));
				check("Esc schließt nur die Kommentare, das Handy bleibt offen",
						ui.testView() == PhoneUi.View.FEED && ReelsHooks.phoneOpen() && !ui.isClosing());
				next(10);
				return;
			}
			case 21: {
				// Handy drehen: echte Taste R (Standard).
				AutoTest.key(mc, Keys.code("key.keyboard.r"));
				boolean rotated = ui.testHold() == PhoneHold.PORTRAIT;
				check("Taste R dreht das Handy auf hochkant", rotated);
				if (!rotated) ui.inputKey(Keys.code("key.keyboard.r"), UiKey.NONE, false);
				check("Haltung wird gemerkt", modules.reels.hold.get() == ui.testHold());
				next(30);
				return;
			}
			case 22: {
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
			case 23:
				ui.testForceSlim(Boolean.TRUE);
				next(10);
				return;
			case 24:
				AutoTest.shot(mc, "trsclient-reels-11-hochkant-slim");
				next(15);
				return;
			case 25:
				ui.testForceSlim(Boolean.FALSE);
				next(10);
				return;
			case 26:
				AutoTest.shot(mc, "trsclient-reels-11b-hochkant-breit");
				next(15);
				return;
			case 27: {
				ui.testForceSlim(null);
				// Zurück zum ersten Post (quer fotografiert): hochkant ganz zu sehen.
				int[] screen = spot("screen");
				double gs = Mc.window().getGuiScale();
				Mc.screen().mouseScrolled((screen[0] + screen[2] / 2.0) / gs, (screen[1] + screen[3] / 2.0) / gs, 0, 1);
				check("Mausrad nach oben blättert zurück zum ersten Post", ui.testPage() == 0);
				next(15);
				return;
			}
			case 28:
				AutoTest.shot(mc, "trsclient-reels-11c-hochkant-querfoto");
				next(15);
				return;
			case 29:
				// Hochkant in anderen Fenstergrößen.
				if (windows(mc, PORTRAIT_WINDOWS, PORTRAIT_WINDOW_SHOTS, "hochkant")) phase++;
				return;
			case 30:
				windowSize(1920, 1080);
				next(40);
				return;
			case 31:
				check("Fenster wieder 1920×1080", Mc.window().getWidth() == 1920 && Mc.window().getHeight() == 1080);
				next(5);
				return;
			case 32: {
				// --- Kamera, „Foto“ (hochkant) ---
				// Der Blick soll einen Block in Reichweite treffen (sein Umriss darf auf keinem Foto sein): ein paar
				// Richtungen durchprobieren, bis einer anvisiert ist (am Ufer trifft der Blick sonst nur Wasser).
				if (!applied) {
					look(mc, SCENE_YAW + AIMS[index][0], AIMS[index][1]);
					applied = true;
					wait = 8;
					return;
				}
				applied = false;
				if (!blockAimed(mc) && ++index < AIMS.length) return;
				TrsClient.LOGGER.info("[Autotest] Reels: Blick für das Foto: {}° zur Seite, {}° nach unten",
						AIMS[Math.min(index, AIMS.length - 1)][0], AIMS[Math.min(index, AIMS.length - 1)][1]);
				index = 0;
				// Eine frische Chat-Zeile steht auf dem Bildschirm – auch sie darf nicht auf das Foto.
				ChatLines.addMessage(Mc.text("MC-Reels-Selbsttest: Auch diese Zeile darf nicht auf dem Foto sein."));
				feedBefore = reels.backend().feed().size();
				next(20);
				return;
			}
			case 33: {
				check("der Blick trifft einen Block in Reichweite (Probe für den Umriss)", blockAimed(mc));
				clickSpot(mc, "camera");
				check("echter Klick auf den Kamera-Knopf öffnet die Kamera", ui.testView() == PhoneUi.View.CAMERA);
				if (ui.testView() != PhoneUi.View.CAMERA) {
					// Ohne Kamera sind die folgenden Schritte sinnlos.
					phase = 50;
					return;
				}
				next(10);
				return;
			}
			case 34: {
				if (!waitFor(ui.testViewfinder() != null, 80)) return;
				int[] finder = ui.testViewfinder();
				int[] screen = spot("screen");
				check("Haken vor der Oberfläche greift (GuiRenderer.render)", ReelsHooks.frameHookSeen());
				check("der Sucher zeigt ein Bild der Welt", finder != null);
				if (finder != null) {
					check("Sucher-Textur " + finder[0] + "×" + finder[1] + " ist mindestens 0,9 × so breit wie der Sucher ("
							+ screen[2] + "×" + screen[3] + " Fensterpixel) – nichts wird hochskaliert", finder[0] >= 0.9 * screen[2]
							&& finder[1] >= 0.9 * screen[3]);
				}
				check("„Foto“ dreht die Kamera des Spiels nicht", !ui.selfieActive() && !Mc.camera().isDetached());
				AutoTest.shot(mc, "trsclient-reels-12-kamera-foto");
				next(15);
				return;
			}
			case 35:
				clickSpot(mc, "camShutter");
				check("echter Klick auf den Auslöser: Aufnahme läuft", ui.testBusy() || ui.testView() == PhoneUi.View.COMPOSE);
				next(5);
				return;
			case 36: {
				if (!waitFor(ui.testView() == PhoneUi.View.COMPOSE, 100)) return;
				Reels.Shot shot = ui.testShot();
				check("nach dem Auslösen öffnet sich „Neuer Post“", ui.testView() == PhoneUi.View.COMPOSE && shot != null);
				if (shot != null) {
					photoFile = shot.file;
					int[] want = photoSize(PhoneHold.PORTRAIT);
					check("Foto ist gespeichert (" + shot.file + ")", Files.isRegularFile(shot.file));
					check("Foto hochkant ist " + want[0] + "×" + want[1] + " (mittiger Ausschnitt, nicht vergrößert) – ist "
							+ shot.width + "×" + shot.height, shot.width == want[0] && shot.height == want[1] && shot.portrait && !shot.selfie);
					check("Foto liegt im Foto-Ordner des Feeds, nicht bei den Bildschirmfotos",
							dir(reels) != null && shot.file.startsWith(dir(reels).resolve("photos")));
				}
				for (int i = 0; i < CAPTION.length(); i++) ui.inputChar(CAPTION.charAt(i));
				next(10);
				return;
			}
			case 37:
				AutoTest.shot(mc, "trsclient-reels-13-posten");
				next(15);
				return;
			case 38: {
				// Enter postet (echte Taste).
				AutoTest.key(mc, Keys.code("key.keyboard.enter"));
				List<ReelsPost> feed = reels.backend().feed();
				check("Enter postet: " + (feedBefore + 1) + " Posts im Feed (sind " + feed.size() + ")", feed.size() == feedBefore + 1);
				ReelsPost top = feed.isEmpty() ? null : feed.get(0);
				check("der neue Post steht vorn, mit Text und Namen", top != null && CAPTION.equals(top.caption)
						&& mc.getUser().getName().equals(top.author) && top.portrait && !top.selfie);
				check("Streak ist 1 (" + reels.backend().streak(System.currentTimeMillis()) + ")",
						reels.backend().streak(System.currentTimeMillis()) == 1);
				check("zurück im Feed auf Seite 1, mit Meldung „" + ui.testToast() + "“", ui.testView() == PhoneUi.View.FEED
						&& ui.testPage() == 0 && ui.testToast() != null);
				next(15);
				return;
			}
			case 39:
				AutoTest.shot(mc, "trsclient-reels-14-gepostet");
				next(15);
				return;
			case 40:
				// --- Kamera, „Selfie“ --- (wieder der Blick über den Fluss; hinter dem Spieler stehen Bäume)
				look(mc, SCENE_YAW, 4f);
				clickSpot(mc, "camera");
				check("Kamera ist wieder offen", ui.testView() == PhoneUi.View.CAMERA);
				next(15);
				return;
			case 41:
				if (!applied) {
					// Zum Vergleich: die Kamera auf „Foto“ – hinter dem Handy muss das Selfie danach genauso aussehen.
					AutoTest.shot(mc, "trsclient-reels-15a-kamera-vor-selfie");
					applied = true;
					wait = 15;
					return;
				}
				applied = false;
				clickSpot(mc, "camSelfie");
				check("echter Klick auf „Selfie“ schaltet um", ui.testSelfie() && ui.selfieActive());
				// Kamera dreht sich, der Sucher holt ein neues Bild.
				next(50);
				return;
			case 42:
				check("Haken der Kamera-Ausrichtung greift (Camera.alignWithEntity)", ReelsHooks.cameraHookSeen());
				check("Haken der Effekte vor dem Gesicht greift (ScreenEffectRenderer.submit)", ReelsHooks.effectsHookSeen());
				check("Selfie: die Kamera des Spiels ist losgelöst (die eigene Figur wird gezeichnet)", Mc.camera().isDetached());
				check("Selfie: hinter dem Handy liegt wieder die normale Sicht (keine eigene Figur zwischen den Armen)",
						ReelsHooks.backdropSeen() && !ReelsHooks.backdropBroken());
				check("die Perspektive in den Optionen bleibt die Ich-Sicht", mc.options.getCameraType() == CameraType.FIRST_PERSON);
				AutoTest.shot(mc, "trsclient-reels-15-kamera-selfie");
				next(15);
				return;
			case 43:
				// Auslösen mit Enter (echte Taste).
				AutoTest.key(mc, Keys.code("key.keyboard.enter"));
				check("Enter löst aus", ui.testBusy() || ui.testView() == PhoneUi.View.COMPOSE);
				next(0);
				return;
			case 44: {
				// In jedem Tick nachsehen: das zweite Enter soll gleich nach dem Öffnen von „Neuer Post“ kommen.
				if (ui.testView() != PhoneUi.View.COMPOSE && waited++ < 500) return;
				waited = 0;
				if (ui.testView() == PhoneUi.View.COMPOSE && !ui.testPostArmed()) {
					int posts = reels.backend().feed().size();
					AutoTest.key(mc, Keys.code("key.keyboard.enter"));
					check("ein zweites Enter gleich nach dem Auslösen postet das Foto nicht",
							ui.testView() == PhoneUi.View.COMPOSE && reels.backend().feed().size() == posts);
				} else {
					TrsClient.LOGGER.info("[Autotest] Reels: Hinweis – „Neuer Post“ war schon zu lange offen, das zweite Enter wurde nicht geprüft");
				}
				Reels.Shot shot = ui.testShot();
				check("Selfie aufgenommen", shot != null && shot.selfie && Files.isRegularFile(shot.file));
				check("in „Neuer Post“ schaut die Kamera des Spiels wieder normal", !ui.selfieActive());
				retryFile = shot == null ? null : shot.file;
				clickSpot(mc, "composeRetry");
				check("„Nochmal“ führt zurück zur Kamera (weiter auf „Selfie“)", ui.testView() == PhoneUi.View.CAMERA && ui.testSelfie());
				next(10);
				return;
			}
			case 45:
				if (!waitFor(retryFile == null || !Files.exists(retryFile), 40)) return;
				check("„Nochmal“ hat das Foto gelöscht (" + retryFile + ")", retryFile != null && !Files.exists(retryFile));
				// Der Sucher braucht wieder ein Bild von vorn.
				next(40);
				return;
			case 46:
				clickSpot(mc, "camShutter");
				next(5);
				return;
			case 47: {
				if (!waitFor(ui.testView() == PhoneUi.View.COMPOSE, 100)) return;
				Reels.Shot shot = ui.testShot();
				check("zweites Selfie aufgenommen", shot != null && shot.selfie && Files.isRegularFile(shot.file));
				selfieFile = shot == null ? null : shot.file;
				for (int i = 0; i < SELFIE_CAPTION.length(); i++) ui.inputChar(SELFIE_CAPTION.charAt(i));
				next(10);
				return;
			}
			case 48:
				AutoTest.shot(mc, "trsclient-reels-15b-selfie-posten");
				next(15);
				return;
			case 49: {
				clickSpot(mc, "composePost");
				List<ReelsPost> feed = reels.backend().feed();
				ReelsPost top = feed.isEmpty() ? null : feed.get(0);
				check("echter Klick auf „Posten“: " + (feedBefore + 2) + " Posts, das Selfie vorn", feed.size() == feedBefore + 2
						&& top != null && top.selfie && SELFIE_CAPTION.equals(top.caption));
				check("Streak bleibt 1", reels.backend().streak(System.currentTimeMillis()) == 1);
				check("nach dem Posten ist das Selfie aus (Kamera des Spiels normal)", !ui.selfieActive());
				// Auf dem nächsten Bild soll die Meldung nicht mehr stehen.
				next(90);
				return;
			}
			case 50: {
				AutoTest.key(mc, Keys.code("key.keyboard.r"));
				boolean rotated = ui.testHold() == PhoneHold.LANDSCAPE;
				check("Taste R dreht zurück auf quer", rotated);
				if (!rotated) ui.inputKey(Keys.code("key.keyboard.r"), UiKey.NONE, false);
				// Den anderen Arm-Typ auch quer zeigen (schlanker Skin: mittlere Spalte doppelt, Arm bleibt 4 breit).
				ui.testForceSlim(Boolean.valueOf(!playerSlim(mc)));
				next(30);
				return;
			}
			case 51:
				AutoTest.shot(mc, "trsclient-reels-11d-quer-" + (playerSlim(mc) ? "breit" : "slim"));
				next(15);
				return;
			case 52:
				// --- Kamera quer ---
				ui.testForceSlim(null);
				clickSpot(mc, "camera");
				check("Kamera öffnet sich auch quer (Knopf in der rechten Spalte)", ui.testView() == PhoneUi.View.CAMERA);
				next(10);
				return;
			case 53: {
				// Die Kamera merkt sich „Selfie“, solange das Handy draußen ist.
				check("die Kamera steht noch auf „Selfie“", ui.testSelfie());
				next(50);
				return;
			}
			case 54:
				AutoTest.shot(mc, "trsclient-reels-16b-quer-kamera-selfie");
				next(15);
				return;
			case 55: {
				// Tab wechselt zurück zu „Foto“ (echte Taste).
				AutoTest.key(mc, Keys.code("key.keyboard.tab"));
				check("Tab schaltet auf „Foto“", !ui.testSelfie() && !ui.selfieActive());
				// Umsehen: Ziehen im Sucher (200 Fensterpixel nach rechts = 18°) und die Pfeiltaste (5° je Druck).
				int[] finder = spot("viewfinder");
				double gs = Mc.window().getGuiScale();
				double gx = (finder[0] + finder[2] * 0.3) / gs, gy = (finder[1] + finder[3] * 0.5) / gs;
				float yaw = mc.player.getYRot();
				ui.inputClick(gx, gy, 0);
				ui.inputDrag(gx + 200 / gs, gy, 0);
				ui.inputRelease(gx + 200 / gs, gy, 0);
				float dragged = mc.player.getYRot() - yaw;
				check(String.format(Locale.ROOT, "Ziehen im Sucher dreht den Blick (%.1f° bei 200 Pixeln)", dragged),
						Math.abs(dragged - 18f) < 0.6f);
				AutoTest.key(mc, Keys.code("key.keyboard.left"));
				float keyed = mc.player.getYRot() - yaw - dragged;
				check(String.format(Locale.ROOT, "Pfeiltaste links dreht den Blick (%.1f°)", keyed), Math.abs(keyed + 5f) < 0.3f);
				look(mc, SCENE_YAW, 10f);
				next(50);
				return;
			}
			case 56: {
				int[] finder = ui.testViewfinder();
				int[] screen = spot("screen");
				check("Sucher quer: Textur " + (finder == null ? "fehlt" : finder[0] + "×" + finder[1]) + " für " + screen[2] + "×"
						+ screen[3] + " Fensterpixel", finder != null && finder[0] >= 0.9 * screen[2] && finder[1] >= 0.9 * screen[3]);
				AutoTest.shot(mc, "trsclient-reels-16-quer-kamera");
				next(15);
				return;
			}
			case 57:
				// Die Kamera in kleinen Fenstern (mit der echten Schrift): quer und hochkant, dazu ein hohes Fenster.
				windowSize(854, 480);
				next(45);
				return;
			case 58: {
				int[] finder = ui.testViewfinder();
				int[] screen = spot("screen");
				check("Fenster 854×480: Sucher-Textur " + (finder == null ? "fehlt" : finder[0] + "×" + finder[1]) + " für " + screen[2]
						+ "×" + screen[3] + " Fensterpixel", Mc.window().getWidth() == 854 && finder != null
						&& finder[0] >= 0.9 * screen[2] && finder[1] >= 0.9 * screen[3]);
				check("alle Knöpfe der Kamera sind da (quer, 854×480)", ui.testSpot("camShutter") != null
						&& ui.testSpot("camFlip") != null && ui.testSpot("camBack") != null && ui.testSpot("camSelfie") != null);
				AutoTest.shot(mc, "trsclient-reels-16d-quer-kamera-854");
				next(15);
				return;
			}
			case 59:
				AutoTest.key(mc, Keys.code("key.keyboard.r"));
				check("R dreht auch in der Kamera", ui.testHold() == PhoneHold.PORTRAIT && ui.testView() == PhoneUi.View.CAMERA);
				next(45);
				return;
			case 60:
				check("alle Knöpfe der Kamera sind da (hochkant, 854×480)", ui.testSpot("camShutter") != null
						&& ui.testSpot("camFlip") != null && ui.testSpot("camBack") != null && ui.testSpot("camSelfie") != null);
				armCheck("Kamera hochkant, Fenster 854×480");
				AutoTest.shot(mc, "trsclient-reels-16e-hochkant-kamera-854");
				next(15);
				return;
			case 61:
				windowSize(800, 1000);
				next(45);
				return;
			case 62: {
				int[] finder = ui.testViewfinder();
				int[] screen = spot("screen");
				check("Fenster 800×1000: Sucher-Textur " + (finder == null ? "fehlt" : finder[0] + "×" + finder[1]) + " für " + screen[2]
						+ "×" + screen[3] + " Fensterpixel", finder != null && finder[0] >= 0.9 * screen[2] && finder[1] >= 0.9 * screen[3]);
				AutoTest.shot(mc, "trsclient-reels-16f-hochkant-kamera-800x1000");
				next(15);
				return;
			}
			case 63:
				AutoTest.key(mc, Keys.code("key.keyboard.r"));
				windowSize(1920, 1080);
				next(50);
				return;
			case 64:
				check("zurück: Fenster 1920×1080, Handy quer, Kamera offen", Mc.window().getWidth() == 1920
						&& Mc.window().getHeight() == 1080 && ui.testHold() == PhoneHold.LANDSCAPE && ui.testView() == PhoneUi.View.CAMERA);
				next(10);
				return;
			case 65:
				clickSpot(mc, "camShutter");
				next(5);
				return;
			case 66: {
				if (!waitFor(ui.testView() == PhoneUi.View.COMPOSE, 100)) return;
				Reels.Shot shot = ui.testShot();
				int[] want = photoSize(PhoneHold.LANDSCAPE);
				check("Foto quer ist " + want[0] + "×" + want[1] + (shot == null ? "" : " – ist " + shot.width + "×" + shot.height),
						shot != null && shot.width == want[0] && shot.height == want[1] && !shot.portrait);
				retryFile = shot == null ? null : shot.file;
				for (int i = 0; i < CAPTION.length(); i++) ui.inputChar(CAPTION.charAt(i));
				next(10);
				return;
			}
			case 67:
				AutoTest.shot(mc, "trsclient-reels-16c-quer-posten");
				next(15);
				return;
			case 68:
				// Esc in „Neuer Post“: Foto verwerfen, zurück zur Kamera (echte Taste).
				AutoTest.key(mc, Keys.code("key.keyboard.escape"));
				check("Esc in „Neuer Post“ führt zurück zur Kamera, das Handy bleibt offen",
						ui.testView() == PhoneUi.View.CAMERA && ReelsHooks.phoneOpen() && !ui.isClosing());
				next(10);
				return;
			case 69: {
				if (!waitFor(retryFile == null || !Files.exists(retryFile), 40)) return;
				check("Esc hat das Foto gelöscht", retryFile != null && !Files.exists(retryFile));
				AutoTest.key(mc, Keys.code("key.keyboard.escape"));
				check("Esc in der Kamera führt zurück zum Feed, das Handy bleibt offen",
						ui.testView() == PhoneUi.View.FEED && ReelsHooks.phoneOpen() && !ui.isClosing());
				check("im Feed stehen weiter " + (feedBefore + 2) + " Posts", reels.backend().feed().size() == feedBefore + 2);
				next(15);
				return;
			}
			case 70:
				ui.testForceSlim(null);
				// Versuch: leichter Weichzeichner hinter dem Handy (ab Werk aus).
				ReelsHooks.testBlur(true);
				next(15);
				return;
			case 71:
				AutoTest.shot(mc, "trsclient-reels-11e-quer-weichzeichner");
				next(15);
				return;
			case 72:
				ReelsHooks.testBlur(false);
				next(10);
				return;
			case 73: {
				// Wegstecken mit der Handy-Taste (echte Taste, ab Werk K; sie gilt, weil sie seit dem Öffnen „los“ war).
				String openKey = dev.theredstonee.trsclient.core.input.KeyCombo.main(modules.reels.openKey.get());
				check("die Handy-Taste ist ab Werk K (H gehört der Schaltungs-Bibliothek)", "key.keyboard.k".equals(openKey)
						&& !openKey.equals(dev.theredstonee.trsclient.core.input.KeyCombo.main(modules.circuits.hideKey.get())));
				AutoTest.key(mc, Keys.code(openKey));
				check("die Handy-Taste steckt das Handy weg (Schließen läuft)", ui.isClosing() || !ReelsHooks.phoneOpen());
				if (!ui.isClosing() && ReelsHooks.phoneOpen()) ui.requestClose();
				next(30);
				return;
			}
			case 74: {
				check("Handy ist zu", !ReelsHooks.phoneOpen() && Mc.screen() == null);
				check("aufgeräumt (dispose)", ui.disposed());
				check("Handy-Taste ist wieder frei (Sperre gelöst, weil die Taste los ist)", !reels.keyGate().blocked());
				Path feed = dir(reels) == null ? null : dir(reels).resolve("feed.json");
				String json = feed != null && Files.isRegularFile(feed)
						? new String(Files.readAllBytes(feed), java.nio.charset.StandardCharsets.UTF_8) : "";
				check("feed.json ist gesichert und enthält den Kommentar (" + feed + ")", json.contains(COMMENT));
				check("feed.json enthält beide neuen Posts", json.contains(CAPTION) && json.contains(SELFIE_CAPTION));
				check("beide geposteten Fotos liegen noch im Foto-Ordner", photoFile != null && Files.isRegularFile(photoFile)
						&& selfieFile != null && Files.isRegularFile(selfieFile));
				// Die beiden Fotos neben die Bilder des Tests legen (zum Ansehen: kein HUD, kein Chat, kein Handy, keine Arme).
				copyPhoto(mc, photoFile, "foto");
				copyPhoto(mc, selfieFile, "selfie");
				next(20);
				return;
			}
			case 75:
				AutoTest.shot(mc, "trsclient-reels-17-nachher");
				next(15);
				return;
			case 76: {
				// Aus der Außenansicht: das Handy stellt für die Dauer auf Ich-Sicht.
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				boolean opened = ReelsHooks.open();
				ui = ReelsHooks.phone();
				check("Handy geht aus der Außenansicht auf und stellt auf Ich-Sicht",
						opened && ui != null && mc.options.getCameraType() == CameraType.FIRST_PERSON);
				next(20);
				return;
			}
			case 77: {
				// Etwas anderes ersetzt den Bildschirm (wie Tod oder Verbindungsende): trotzdem aufräumen.
				PhoneUi open = ui;
				Mc.setScreen(null);
				check("fremdes Schließen räumt auf (removed → dispose)", open != null && open.disposed());
				check("Perspektive ist zurückgestellt", mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				next(10);
				return;
			}
			case 78:
				// Eine Außenansicht, die eine TRS-Funktion hält (hier Freelook), merkt sich das Handy nicht.
				TrsClient.get().pvp().forceFreelook(40F);
				next(10);
				return;
			case 79: {
				boolean third = mc.options.getCameraType() != CameraType.FIRST_PERSON;
				check("Freelook hat die Außenansicht eingestellt", third && TrsClient.get().pvp().freelook().active());
				boolean opened = ReelsHooks.open();
				ui = ReelsHooks.phone();
				check("Handy geht während des Freelooks auf, in der Ich-Sicht, und merkt sich dessen Außenansicht nicht",
						opened && ui != null && mc.options.getCameraType() == CameraType.FIRST_PERSON && ReelsHooks.viewBefore() == null);
				next(10);
				return;
			}
			case 80:
				// Der Freelook endet, während das Handy draußen ist.
				TrsClient.get().pvp().forceFreelook(Float.NaN);
				next(10);
				return;
			case 81: {
				check("Freelook ist aus, das Handy ist noch draußen (Ich-Sicht)", !TrsClient.get().pvp().freelook().active()
						&& ReelsHooks.phoneOpen() && mc.options.getCameraType() == CameraType.FIRST_PERSON);
				Mc.setScreen(null);
				check("nach dem Wegstecken bleibt die Ich-Sicht (keine veraltete Außenansicht)",
						mc.options.getCameraType() == CameraType.FIRST_PERSON);
				next(10);
				return;
			}
			case 82:
				// --- Ein anderer Bildschirm (wie der Chat) sperrt die Handy-Taste, bis sie einmal los war ---
				// Sonst ginge das Handy von selbst auf, wenn die Taste beim Schließen noch unten ist (das „k“ am Ende einer
				// Chat-Nachricht). Den echten Tastenzustand kann der Test nicht setzen – geprüft wird die Sperre.
				Mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
				next(4);
				return;
			case 83:
				check("ein anderer offener Bildschirm sperrt die Handy-Taste", Mc.screen() != null && reels.keyGate().blocked());
				Mc.setScreen(null);
				next(4);
				return;
			case 84:
				check("nach dessen Schließen ist die Taste wieder frei (sie ist los), das Handy bleibt in der Tasche",
						!reels.keyGate().blocked() && !ReelsHooks.phoneOpen() && Mc.screen() == null);
				next(5);
				return;
			case 85:
				// --- Freelook im Umschalt-Modus: bliebe ohne Weiteres unter dem Handy an ---
				// Der Spieler steht in der Außenansicht (F5) und schaltet Freelook ein (Taste kurz gedrückt).
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				modules.freelook.setEnabled(true);
				modules.freelookToggle.set(true);
				dev.theredstonee.trsclient.TrsKeys.freelook.setDown(true);
				next(3);
				return;
			case 86:
				dev.theredstonee.trsclient.TrsKeys.freelook.setDown(false);
				next(5);
				return;
			case 87: {
				check("Freelook im Umschalt-Modus läuft weiter, obwohl die Taste los ist", TrsClient.get().pvp().freelook().active());
				boolean opened = ReelsHooks.open();
				ui = ReelsHooks.phone();
				check("das Handy beendet den Freelook beim Herausholen", opened && ui != null && !TrsClient.get().pvp().freelook().active());
				check("Ich-Sicht; die Außenansicht des Spielers von vor dem Freelook ist gemerkt (" + ReelsHooks.viewBefore() + ")",
						mc.options.getCameraType() == CameraType.FIRST_PERSON && ReelsHooks.viewBefore() == CameraType.THIRD_PERSON_BACK);
				if (ui == null) {
					phase = 91;
					return;
				}
				next(15);
				return;
			}
			case 88:
				check("der Freelook bleibt aus, solange das Handy draußen ist", !TrsClient.get().pvp().freelook().active()
						&& ReelsHooks.phoneOpen() && mc.options.getCameraType() == CameraType.FIRST_PERSON);
				clickSpot(mc, "camera");
				next(10);
				return;
			case 89:
				// Die Kamera des Spiels folgt wieder dem Spieler: die Pfeiltaste dreht beide.
				yawBefore = Mc.camera().yRot();
				AutoTest.key(mc, Keys.code("key.keyboard.right"));
				next(12);
				return;
			case 90: {
				float turned = net.minecraft.util.Mth.wrapDegrees(Mc.camera().yRot() - yawBefore);
				check(String.format(Locale.ROOT, "Pfeiltaste in der Kamera dreht das Bild des Spiels mit (%.1f°)", turned),
						ui.testView() == PhoneUi.View.CAMERA && Math.abs(turned - 5f) < 0.3f);
				Mc.setScreen(null);
				check("nach dem Wegstecken ist die Außenansicht des Spielers zurück, der Freelook bleibt aus",
						mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK && !TrsClient.get().pvp().freelook().active());
				modules.freelookToggle.set(false);
				modules.freelook.setEnabled(false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				look(mc, SCENE_YAW, 4f);
				next(10);
				return;
			}
			case 91:
				// --- Debug-Anzeige (F3): ihr Achsenkreuz liegt im Bild der Welt, also auch im Sucher und auf dem Foto ---
				crosshairsHidden = ReelsHooks.debugCrosshairsHidden();
				mc.debugEntries.setOverlayVisible(true);
				next(15);
				return;
			case 92: {
				crosshairOn = mc.debugEntries.isCurrentlyEnabled(
						net.minecraft.client.gui.components.debug.DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR);
				if (crosshairOn) {
					check("Haken des Debug-Achsenkreuzes greift (DebugCrosshairRenderer.render)", ReelsHooks.debugCrosshairHookSeen());
					check("ohne Handy bleibt das Achsenkreuz von F3, wie es ist", ReelsHooks.debugCrosshairsHidden() == crosshairsHidden);
				} else {
					TrsClient.LOGGER.info("[Autotest] Reels: Hinweis – das Achsenkreuz ist im Debug-Profil dieser Instanz aus, nicht geprüft");
				}
				crosshairsHidden = ReelsHooks.debugCrosshairsHidden();
				boolean opened = ReelsHooks.open();
				ui = ReelsHooks.phone();
				check("Handy geht mit offener Debug-Anzeige auf", opened && ui != null);
				if (ui == null) {
					finish(mc);
					return;
				}
				next(15);
				return;
			}
			case 93:
				clickSpot(mc, "camera");
				next(30);
				return;
			case 94:
				if (crosshairOn) {
					check("mit dem Handy draußen zeichnet Minecraft das Achsenkreuz nicht (" + (ReelsHooks.debugCrosshairsHidden()
							- crosshairsHidden) + " Bilder)", ReelsHooks.debugCrosshairsHidden() > crosshairsHidden);
				}
				// Zum Ansehen: in der Mitte des Suchers darf kein rot-grün-blaues Kreuz sein.
				AutoTest.shot(mc, "trsclient-reels-18-kamera-f3");
				next(15);
				return;
			case 95:
				mc.debugEntries.setOverlayVisible(false);
				// --- Selfie und Fenstergröße: kein umgedrehtes Bild ohne die normale Sicht dahinter ---
				clickSpot(mc, "camSelfie");
				next(40);
				return;
			case 96:
				check("Selfie läuft (Kamera losgelöst, normale Sicht gesichert)", ui.selfieActive() && Mc.camera().isDetached()
						&& ReelsHooks.backdropValid() && !ReelsHooks.backdropBroken());
				backdropMisses = ReelsHooks.backdropMisses();
				windowSize(1280, 720);
				next(40);
				return;
			case 97:
				check("Fenster 1280×720 beim Selfie: die normale Sicht ist neu gesichert, kein Bild blieb umgedreht stehen ("
						+ (ReelsHooks.backdropMisses() - backdropMisses) + ")", Mc.window().getWidth() == 1280
						&& Mc.camera().isDetached() && ReelsHooks.backdropValid() && !ReelsHooks.backdropBroken()
						&& ReelsHooks.backdropMisses() == backdropMisses);
				AutoTest.shot(mc, "trsclient-reels-19-selfie-1280");
				next(15);
				return;
			case 98:
				windowSize(1920, 1080);
				next(40);
				return;
			case 99: {
				check("zurück auf 1920×1080 beim Selfie: ebenso", Mc.window().getWidth() == 1920 && Mc.camera().isDetached()
						&& ReelsHooks.backdropValid() && !ReelsHooks.backdropBroken() && ReelsHooks.backdropMisses() == backdropMisses);
				// --- Wegstecken mit der Handy-Taste, während „Selfie“ gewählt ist ---
				turnedWithoutPhone = ReelsHooks.turnedWithoutPhone();
				AutoTest.key(mc, Keys.code(dev.theredstonee.trsclient.core.input.KeyCombo.main(modules.reels.openKey.get())));
				check("die Handy-Taste steckt auch vom Selfie aus weg; die Kamera des Spiels dreht sich dabei nicht mehr um",
						ui.isClosing() && ui.testSelfie() && !ui.selfieActive());
				if (!ui.isClosing()) ui.requestClose();
				next(30);
				return;
			}
			case 100:
				check("Handy ist zu, die Kamera des Spiels schaut normal", !ReelsHooks.phoneOpen() && Mc.screen() == null
						&& ui.disposed() && !Mc.camera().isDetached());
				check("kein Bild war noch umgedreht, als das Handy verschwand (" + (ReelsHooks.turnedWithoutPhone()
						- turnedWithoutPhone) + ")", ReelsHooks.turnedWithoutPhone() == turnedWithoutPhone && !ReelsHooks.backdropValid());
				next(10);
				return;
			case 101:
				AutoTest.shot(mc, "trsclient-reels-20-nach-selfie-weg");
				next(15);
				return;
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
			TrsClient.get().pvp().forceFreelook(Float.NaN);
			dev.theredstonee.trsclient.TrsKeys.freelook.setDown(false);
			mc.debugEntries.setOverlayVisible(false);
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

	/^*
	 * Eine Fenstergröße nach der anderen: setzen, warten, prüfen (Handy ganz im Fenster, Arm-Enden darunter), Bild.
	 * Rückgabe: alle Größen sind durch.
	 *^/
	private boolean windows(Minecraft mc, int[][] sizes, String[] shots, String label) {
		int[] size = sizes[index];
		if (!applied) {
			windowSize(size[0], size[1]);
			applied = true;
			wait = 40;
			return false;
		}
		applied = false;
		int w = Mc.window().getWidth(), h = Mc.window().getHeight();
		check("Fenster ist " + size[0] + "×" + size[1] + " (ist " + w + "×" + h + ")", w == size[0] && h == size[1]);
		int[] phone = ui.testSpot("phone");
		check("Handy " + label + " liegt ganz im Fenster " + w + "×" + h + " (" + Arrays.toString(phone) + ")",
				phone != null && phone[0] >= 0 && phone[1] >= 0 && phone[0] + phone[2] <= w && phone[1] + phone[3] <= h);
		armCheck(label + ", Fenster " + w + "×" + h);
		AutoTest.shot(mc, "trsclient-reels-" + shots[index]);
		wait = 15;
		if (++index < sizes.length) return false;
		index = 0;
		return true;
	}

	/^* Arm-Enden beider Arme liegen unter dem Fensterrand (gerechnet; zu sehen ist es auf dem Bild). *^/
	private void armCheck(String label) {
		int w = Mc.window().getWidth(), h = Mc.window().getHeight();
		PhoneLayout l = PhoneLayout.of(w, h, ui.testHold());
		float left = ArmRig.of(l, true).lowestEndY() - h;
		float right = ArmRig.of(l, false).lowestEndY() - h;
		check(String.format(Locale.ROOT, "%s: Arm-Enden %.1f / %.1f px unter dem Fensterrand (%d×%d, Verlängerung %.0f)", label,
				left, right, w, h, ArmRig.extension(l)), left >= ArmRig.MARGIN_PX && right >= ArmRig.MARGIN_PX);
	}

	/^* Blick des Spielers: Richtung {@code yaw}, {@code pitch} Grad nach unten – ohne Übergang, der Körper dreht mit. *^/
	private static void look(Minecraft mc, float yaw, float pitch) {
		net.minecraft.client.player.LocalPlayer p = mc.player;
		if (p == null) return;
		p.setYRot(yaw);
		p.yRotO = yaw;
		p.setXRot(pitch);
		p.xRotO = pitch;
		p.yHeadRot = yaw;
		p.yHeadRotO = yaw;
		p.yBodyRot = yaw;
		p.yBodyRotO = yaw;
	}

	private static boolean blockAimed(Minecraft mc) {
		return mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
	}

	/^* So groß muss ein Foto dieser Haltung sein: mittiger Ausschnitt aus dem Bild des Spiels, höchstens 1280 lang. *^/
	private static int[] photoSize(PhoneHold hold) {
		int[] aspect = PhotoFrame.aspect(hold);
		int[] crop = PhotoFrame.cropRect(Mc.mainRenderTarget().width, Mc.mainRenderTarget().height, aspect[0], aspect[1]);
		return PhotoFrame.outSize(crop[2], crop[3], PhotoFrame.MAX_LONG_SIDE);
	}

	/^* Legt ein gespeichertes Foto als {@code trsclient-<Version>-reels-<name>.png} zu den Bildern des Tests. *^/
	private static void copyPhoto(Minecraft mc, Path photo, String name) {
		if (photo == null || !Files.isRegularFile(photo)) return;
		try {
			Path dir = mc.gameDirectory.toPath().resolve("screenshots");
			Files.createDirectories(dir);
			Files.copy(photo, dir.resolve("trsclient-" + AutoTest.MC_VERSION + "-reels-" + name + ".png"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException e) {
			TrsClient.LOGGER.warn("[Autotest] Reels: Foto nicht kopiert", e);
		}
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

	/^*
	 * Stellt den Spieler an die feste Stelle der Testwelt – oder, wenn sie nicht mehr trocken und grün ist, in die
	 * nächste grüne Gegend (Ebene, Wiese, Wald, Kirschhain), ein Stück hinein und mit Blick hinein. Läuft im
	 * Server-Thread; findet sich nichts, bleibt er am Startpunkt.
	 *^/
	private static void greenScene(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null) return;
		server.execute(() -> {
			try {
				net.minecraft.server.level.ServerLevel level = server.overworld();
				if (server.getPlayerList().getPlayers().isEmpty()) return;
				java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> green = h ->
						h.is(net.minecraft.world.level.biome.Biomes.PLAINS) || h.is(net.minecraft.world.level.biome.Biomes.MEADOW)
								|| h.is(net.minecraft.world.level.biome.Biomes.FOREST)
								|| h.is(net.minecraft.world.level.biome.Biomes.FLOWER_FOREST)
								|| h.is(net.minecraft.world.level.biome.Biomes.BIRCH_FOREST)
								|| h.is(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE)
								|| h.is(net.minecraft.world.level.biome.Biomes.SUNFLOWER_PLAINS);
				// Eine Stelle dieser Welt (Startwert 4242), die sich bewährt hat: Ebene an einem Fluss. Passt sie nicht
				// mehr (andere Weltgenerierung), wird von einem festen Punkt aus gesucht – so oder so jedes Mal dieselbe
				// Szene, unabhängig davon, wo der Spieler gerade gelandet ist.
				int x = SCENE[0], z = SCENE[1];
				float yaw = SCENE_YAW;
				int y = dryGreenHeight(level, green, x, z);
				if (y == Integer.MIN_VALUE) {
					net.minecraft.core.BlockPos from = new net.minecraft.core.BlockPos(0, 64, 0);
					com.mojang.datafixers.util.Pair<net.minecraft.core.BlockPos, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> hit =
							level.findClosestBiome3d(green, from, 4096, 32, 64);
					if (hit == null) {
						TrsClient.LOGGER.info("[Autotest] Reels: keine grüne Gegend in der Nähe – Szene bleibt am Startpunkt");
						return;
					}
					double dx = hit.getFirst().getX() - from.getX(), dz = hit.getFirst().getZ() - from.getZ();
					double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
					dx /= len;
					dz /= len;
					// Trockenes, grünes Land: zuerst geradeaus weiter hinein, dann schräg und quer dazu.
					double[][] turns = {{1, 0}, {0.7, 0.7}, {0.7, -0.7}, {0, 1}, {0, -1}};
					for (int t = 0; t < turns.length && y == Integer.MIN_VALUE; t++) {
						double ax = dx * turns[t][0] - dz * turns[t][1], az = dz * turns[t][0] + dx * turns[t][1];
						for (int d = 64; d <= 384 && y == Integer.MIN_VALUE; d += 32) {
							x = hit.getFirst().getX() + (int) Math.round(ax * d);
							z = hit.getFirst().getZ() + (int) Math.round(az * d);
							y = dryGreenHeight(level, green, x, z);
							// Blick weiter in die Gegend hinein.
							yaw = (float) Math.toDegrees(Math.atan2(-ax, az));
						}
					}
					if (y == Integer.MIN_VALUE) {
						TrsClient.LOGGER.info("[Autotest] Reels: kein trockenes grünes Land gefunden – Szene bleibt am Startpunkt");
						return;
					}
				}
				String tp = String.format(Locale.ROOT, "tp @p %.1f %d %.1f %.1f 6", x + 0.5, y, z + 0.5, yaw);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), tp);
				TrsClient.LOGGER.info("[Autotest] Reels: Szene bei {} {} {}, Blick {}° ({})", x, y, z, Math.round(yaw),
						level.getBiome(new net.minecraft.core.BlockPos(x, y, z)).unwrapKey().map(k -> k.toString()).orElse("?"));
			} catch (RuntimeException | LinkageError e) {
				TrsClient.LOGGER.warn("[Autotest] Reels: Szene bleibt am Startpunkt", e);
			}
		});
	}

	/^*
	 * Höhe der Oberfläche an dieser Stelle, wenn sie trocken (kein Wasser obenauf – sonst lägen die beiden Höhenkarten
	 * auseinander) und grün ist; sonst {@code Integer.MIN_VALUE}. Lädt den Chunk; nur im Server-Thread.
	 *^/
	private static int dryGreenHeight(net.minecraft.server.level.ServerLevel level,
			java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> green, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
		boolean dry = y == level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z);
		return dry && green.test(level.getBiome(new net.minecraft.core.BlockPos(x, y, z))) ? y : Integer.MIN_VALUE;
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
