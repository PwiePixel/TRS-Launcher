package dev.theredstonee.trsclient.reels;

// MC-Reels (erste Testversion): Anbindung des Handys an Minecraft – vorerst nur Fabric 26.2. Auf jeder anderen Version
// ist diese Datei leer; das Modul bleibt dort über ReelsModules.platformSupported verborgen.
//? if >=26.2 && <26.3 {
/*import dev.theredstonee.trsclient.TrsClient;
import dev.theredstonee.trsclient.compat.Keys;
import dev.theredstonee.trsclient.compat.Mc;
import dev.theredstonee.trsclient.core.module.ReelsModules;
import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.reels.PhotoFrame;
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.ui.TextureRef;
import dev.theredstonee.trsclient.core.ui.reels.PhoneUi;
import dev.theredstonee.trsclient.screen.ReelsScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

/^*
 * MC-Reels auf Fabric 26.2: Start (Speicherort, Modul freischalten), Handy öffnen, und was die Render-Haken wissen
 * müssen (Hand und Block-Umriss aus, solange das Handy draußen ist; Selfie-Kamera; Bilder der Welt für die Kamera des
 * Handys). Aussehen, Bedienung und Speicher stehen in {@code core.reels} und {@code core.ui.reels}.
 *
 * <p><b>Bilder für die Kamera:</b> {@link #beforeGui()} läuft in jedem Bild direkt bevor Minecraft die Oberfläche
 * zeichnet. Zu diesem Zeitpunkt enthält das Hauptbild die fertige Welt und sonst nichts – kein HUD, kein Chat, kein
 * Handy, keine Arme, kein Schleier (die Spielerhand und der Block-Umriss sind ohnehin aus). Wünscht das Handy ein
 * Bild, wird es hier ausgelesen; die Pixel kommen etwas später über eine Warteschlange zurück.
 *
 * <p>Alles hier läuft im Render-/Spiel-Thread – bis auf die Rückgabe der ausgelesenen Pixel, deren Thread nicht
 * festgelegt ist (deshalb die Warteschlange).
 *^/
public final class ReelsHooks {
	/^* Ordner im Spielordner; für Tests mit {@code -Dtrsclient.reels.dir=<Ordner>} ersetzbar. *^/
	static final String FOLDER = "trs-reels";

	/^* Perspektive vor dem Öffnen (das Handy gibt es nur aus der Ich-Sicht); null = nichts zurückzustellen. *^/
	private static CameraType viewBefore;
	private static boolean handHookSeen;
	private static boolean outlineHookSeen;
	private static boolean frameHookSeen;
	private static boolean cameraHookSeen;

	/^* Abstand der Selfie-Kamera vor dem Gesicht (Blöcke) – etwas mehr als eine Armlänge, Kopf und Schultern im Bild. *^/
	static final float SELFIE_DISTANCE = 1.3f;
	/^* Nach so vielen Nanosekunden gilt ein Auslesen als verloren (der Grafiktreiber hat nicht geantwortet). *^/
	private static final long READBACK_LOST_NS = 3_000_000_000L;

	/^* Gewünschte Bilder (vom Handy im Render-Thread gesetzt, in {@link #beforeGui()} bedient). *^/
	private static boolean wantPhoto;
	private static boolean wantFinder;
	private static int finderW;
	private static int finderH;
	/^* Es läuft gerade ein Auslesen (immer nur eines zugleich). *^/
	private static volatile boolean readbackInFlight;
	private static long readbackSince;
	private static boolean readbackWarned;
	private static final ConcurrentLinkedQueue<PhoneUi.Frame> FRAMES = new ConcurrentLinkedQueue<>();
	/^* Leichter Weichzeichner hinter dem Handy (Versuch, ab Werk aus): {@code -Dtrsclient.reels.blur=true}. *^/
	private static boolean blur = Boolean.getBoolean("trsclient.reels.blur");

	private ReelsHooks() {
	}

	/^*
	 * Beim Start: Feed laden und das Modul freischalten. Schlägt etwas fehl, bleibt das Modul verborgen – der Start
	 * des Spiels hängt nicht daran.
	 *^/
	public static void init(TrsModules modules, Path gameDir) {
		try {
			String custom = System.getProperty("trsclient.reels.dir");
			Path dir = gameDir.resolve(custom != null && !custom.trim().isEmpty() ? custom.trim() : FOLDER);
			Reels.init(modules, dir, message -> TrsClient.LOGGER.info(message));
			ReelsModules.platformSupported(true);
		} catch (RuntimeException | LinkageError e) {
			TrsClient.LOGGER.warn("MC-Reels ist nicht verfügbar", e);
		}
	}

	/^* Handy herausholen. Nur im Spiel und ohne offenen Bildschirm; Rückgabe: ist es jetzt draußen? *^/
	public static boolean open() {
		Minecraft mc = Mc.mc();
		Reels reels = Reels.get();
		if (reels == null || mc.player == null || mc.level == null || Mc.screen() != null) return false;
		try {
			dropFrames();
			PhoneUi phone = new PhoneUi(reels, new Host());
			// Die Arme sind die eigenen: aus der Außenansicht (F5) geht es für die Dauer in die Ich-Sicht.
			CameraType view = mc.options.getCameraType();
			if (view != CameraType.FIRST_PERSON) {
				viewBefore = view;
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			}
			Mc.setScreen(new ReelsScreen(phone));
			return true;
		} catch (RuntimeException | LinkageError e) {
			TrsClient.LOGGER.warn("MC-Reels: Handy lässt sich nicht öffnen", e);
			restoreView();
			return false;
		}
	}

	/^* Das offene Handy oder null. *^/
	public static PhoneUi phone() {
		net.minecraft.client.gui.screens.Screen s = Mc.screen();
		return s instanceof ReelsScreen ? ((ReelsScreen) s).phone() : null;
	}

	/^* Ist das Handy draußen (auch noch während es weggesteckt wird)? *^/
	public static boolean phoneOpen() {
		return Mc.screen() instanceof ReelsScreen;
	}

	/^*
	 * Der Bildschirm des Handys ist weg – egal wodurch (weggesteckt, Tod, Verbindung getrennt): aufräumen und die
	 * Perspektive zurückstellen. Mehrfach rufen schadet nicht.
	 *^/
	public static void closed(PhoneUi phone) {
		try {
			if (phone != null) phone.dispose();
		} catch (RuntimeException | LinkageError e) {
			TrsClient.LOGGER.warn("MC-Reels: Aufräumen fehlgeschlagen", e);
		}
		dropFrames();
		restoreView();
	}

	/^* Offene Bildwünsche und noch nicht abgeholte Bilder verwerfen (Handy auf bzw. zu). *^/
	private static void dropFrames() {
		wantPhoto = false;
		wantFinder = false;
		FRAMES.clear();
	}

	private static void restoreView() {
		CameraType view = viewBefore;
		viewBefore = null;
		if (view == null) return;
		try {
			Mc.mc().options.setCameraType(view);
		} catch (RuntimeException e) {
			TrsClient.LOGGER.warn("MC-Reels: Perspektive nicht zurückgestellt", e);
		}
	}

	// --- Für die Render-Haken (ReelsRenderMixin) ---

	/^* Die normale Spielerhand ist weg, solange das Handy draußen ist – die gezeichneten Arme ersetzen sie. *^/
	public static boolean hideHand() {
		handHookSeen = true;
		return phoneOpen();
	}

	/^* Kein Block-Umriss hinter dem Handy (und später auf keinem Foto). *^/
	public static boolean hideBlockOutline() {
		outlineHookSeen = true;
		return phoneOpen();
	}

	/^*
	 * Abstand der Selfie-Kamera vor dem Gesicht des Spielers in Blöcken, oder 0, solange kein Selfie läuft. Gelesen von
	 * {@code ReelsCameraMixin} bei jeder Kamera-Ausrichtung.
	 *^/
	public static float selfieDistance() {
		cameraHookSeen = true;
		PhoneUi phone = phone();
		return phone != null && phone.selfieActive() ? SELFIE_DISTANCE : 0f;
	}

	/^*
	 * Direkt bevor Minecraft die Oberfläche zeichnet: wünscht das Handy ein Bild der Welt, wird das Hauptbild jetzt
	 * ausgelesen – ein Foto in voller Auflösung, ein Sucherbild so weit verkleinert, wie es der Sucher erlaubt
	 * ({@link PhotoFrame#readbackFactor}). Schlägt etwas fehl, bleibt nur das Bild aus; das Spiel läuft weiter.
	 *^/
	public static void beforeGui() {
		frameHookSeen = true;
		if (!wantPhoto && !wantFinder) return;
		try {
			Minecraft mc = Mc.mc();
			if (mc.level == null || !phoneOpen()) {
				dropFrames();
				return;
			}
			if (readbackInFlight) {
				if (System.nanoTime() - readbackSince < READBACK_LOST_NS) return;
				// Verloren gegangen: nicht für immer warten.
				readbackInFlight = false;
			}
			com.mojang.blaze3d.pipeline.RenderTarget target = Mc.mainRenderTarget();
			if (target == null || target.width <= 0 || target.height <= 0) return;
			final boolean photo = wantPhoto;
			int factor = photo ? 1 : PhotoFrame.readbackFactor(target.width, target.height, finderW, finderH, finderW, finderH);
			if (photo) wantPhoto = false;
			else wantFinder = false;
			readbackInFlight = true;
			readbackSince = System.nanoTime();
			net.minecraft.client.Screenshot.takeScreenshot(target, factor, image -> {
				try {
					FRAMES.add(new PhoneUi.Frame(image.getPixels(), image.getWidth(), image.getHeight(), photo));
				} catch (RuntimeException | OutOfMemoryError e) {
					TrsClient.LOGGER.warn("MC-Reels: Bild der Kamera verworfen: {}", e.toString());
				} finally {
					image.close();
					readbackInFlight = false;
				}
			});
		} catch (RuntimeException | LinkageError e) {
			readbackInFlight = false;
			wantPhoto = false;
			wantFinder = false;
			if (!readbackWarned) {
				readbackWarned = true;
				TrsClient.LOGGER.warn("MC-Reels: Bild der Kamera lässt sich nicht auslesen", e);
			}
		}
	}

	/^* Weichzeichner hinter dem Handy an? (Versuch – die Stärke ist die Menü-Unschärfe des Spielers.) *^/
	public static boolean blur() {
		return blur;
	}

	// --- Für den Selbsttest ---

	/^* Hat der Haken der Hand schon einmal gegriffen (sonst fehlt er, z. B. wegen einer anderen Mod)? *^/
	public static boolean handHookSeen() {
		return handHookSeen;
	}

	public static boolean outlineHookSeen() {
		return outlineHookSeen;
	}

	/^* Hat der Haken vor der Oberfläche schon einmal gegriffen (Bilder für die Kamera)? *^/
	public static boolean frameHookSeen() {
		return frameHookSeen;
	}

	/^* Hat der Haken der Kamera-Ausrichtung schon einmal gegriffen (Selfie)? *^/
	public static boolean cameraHookSeen() {
		return cameraHookSeen;
	}

	public static void testBlur(boolean on) {
		blur = on;
	}

	/^* Was das Handy vom Spiel braucht. *^/
	private static final class Host implements PhoneUi.Host {
		@Override
		public int windowWidth() {
			return Mc.window().getWidth();
		}

		@Override
		public int windowHeight() {
			return Mc.window().getHeight();
		}

		@Override
		public int guiScale() {
			return Math.max(1, Mc.window().getGuiScale());
		}

		@Override
		public TextureRef skin() {
			LocalPlayer p = Mc.mc().player;
			if (p == null) return null;
			try {
				return new TextureRef(p.getSkin().body().texturePath(), 64, 64);
			} catch (RuntimeException e) {
				return null;
			}
		}

		@Override
		public boolean slimSkin() {
			LocalPlayer p = Mc.mc().player;
			if (p == null) return false;
			try {
				return p.getSkin().model() == PlayerModelType.SLIM;
			} catch (RuntimeException e) {
				return false;
			}
		}

		@Override
		public boolean sleeve(boolean left) {
			LocalPlayer p = Mc.mc().player;
			if (p == null) return true;
			try {
				return p.isModelPartShown(left ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE);
			} catch (RuntimeException e) {
				return true;
			}
		}

		@Override
		public String playerName() {
			return Mc.mc().getUser().getName();
		}

		@Override
		public String keyName(int rawKey) {
			return Keys.name(rawKey);
		}

		@Override
		public boolean keyDown(String keyName) {
			return Keys.isDown(keyName);
		}

		@Override
		public String keyLabel(String keyName) {
			return Keys.display(keyName);
		}

		@Override
		public String paste() {
			try {
				return Mc.mc().keyboardHandler.getClipboard();
			} catch (RuntimeException e) {
				return null;
			}
		}

		@Override
		public void click() {
			Mc.mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}

		@Override
		public void turn(float yawDeg, float pitchDeg) {
			LocalPlayer p = Mc.mc().player;
			// Entity.turn rechnet in Maus-Schritten zu 0,15 Grad.
			if (p != null) p.turn(yawDeg / 0.15, pitchDeg / 0.15);
		}

		@Override
		public void requestFrame(boolean photo, int needW, int needH) {
			if (photo) {
				wantPhoto = true;
			} else {
				wantFinder = true;
				finderW = Math.max(1, needW);
				finderH = Math.max(1, needH);
			}
		}

		@Override
		public PhoneUi.Frame pollFrame() {
			return FRAMES.poll();
		}

		@Override
		public void close() {
			if (phoneOpen()) Mc.setScreen(null);
		}
	}
}
*///?}
