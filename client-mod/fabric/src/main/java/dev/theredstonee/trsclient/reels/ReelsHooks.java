package dev.theredstonee.trsclient.reels;

// MC-Reels (erste Testversion): Anbindung des Handys an Minecraft – vorerst nur Fabric 26.2. Auf jeder anderen Version
// ist diese Datei leer; das Modul bleibt dort über ReelsModules.platformSupported verborgen.
//? if >=26.2 && <26.3 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.theredstonee.trsclient.TrsClient;
import dev.theredstonee.trsclient.compat.Keys;
import dev.theredstonee.trsclient.compat.Mc;
import dev.theredstonee.trsclient.core.cinematic.Cinematic;
import dev.theredstonee.trsclient.core.emote.EmoteController;
import dev.theredstonee.trsclient.core.module.ReelsModules;
import dev.theredstonee.trsclient.core.module.TrsModules;
import dev.theredstonee.trsclient.core.reels.PhotoFrame;
import dev.theredstonee.trsclient.core.reels.Reels;
import dev.theredstonee.trsclient.core.ui.TextureRef;
import dev.theredstonee.trsclient.core.ui.reels.PhoneUi;
import dev.theredstonee.trsclient.online.EmoteHooks;
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
 * <p><b>Selfie:</b> die Kamera des Spiels dreht sich für Sucher und Foto zum Spieler um. Hinter dem Handy bliebe dann
 * die eigene Figur zwischen den gezeichneten Armen stehen – deshalb merkt sich {@link #beforeGui()} beim Umschalten das
 * letzte Bild der normalen Sicht (eine Kopie auf der Grafikkarte) und legt es in jedem Selfie-Bild wieder über die
 * Welt, nachdem das Bild für das Handy ausgelesen ist und bevor Minecraft die Oberfläche zeichnet. Hinter dem Handy
 * steht so die normale Sicht (eingefroren, solange „Selfie“ gewählt ist), die Schnellleiste liegt wie immer darüber.
 *
 * <p><b>Perspektive:</b> das Handy gibt es nur aus der Ich-Sicht. Eine Außenansicht, die der Spieler selbst gewählt hat
 * (F5), kommt nach dem Wegstecken zurück. Eine, die gerade eine TRS-Funktion hält (Emote-Kamera, Freelook), merkt sich
 * das Handy nicht – die Funktion stellt selbst zurück, und das Handy würde sonst nach deren Ende eine veraltete
 * Außenansicht wiederherstellen. Während einer Kamera-Fahrt oder der freien Kamera bleibt das Handy in der Tasche.
 *
 * <p>Alles hier läuft im Render-/Spiel-Thread – bis auf die Rückgabe der ausgelesenen Pixel, deren Thread nicht
 * festgelegt ist (deshalb die Warteschlange).
 *^/
public final class ReelsHooks {
	/^*
	 * Ordner unter {@code config/trsclient} – wie alle Daten des TRS Clients, und damit nie in einem exportierten
	 * Modpack (der Launcher lässt {@code config/trsclient} immer weg). Für Tests mit
	 * {@code -Dtrsclient.reels.dir=<Ordner im Spielordner>} ersetzbar.
	 *^/
	static final String FOLDER = "reels";

	/^* Vom Spieler gewählte Außenansicht vor dem Öffnen bzw. währenddessen; null = nichts zurückzustellen. *^/
	private static CameraType viewBefore;
	private static boolean handHookSeen;
	private static boolean outlineHookSeen;
	private static boolean frameHookSeen;
	private static boolean cameraHookSeen;
	private static boolean effectsHookSeen;
	private static boolean backdropSeen;

	/^*
	 * Abstand der Selfie-Kamera vor dem Gesicht (Blöcke): Kopf und Oberkörper im Bild, der Kopf nimmt hochkant gut die
	 * halbe Breite des Suchers ein – wie auf dem freigegebenen Kamera-Bild.
	 *^/
	static final float SELFIE_DISTANCE = 1.65f;
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
	/^* Zählt mit jedem Öffnen und Schließen weiter: ein Bild, das für ein früheres Handy ausgelesen wurde, verfällt. *^/
	private static volatile int generation;

	/^* Selfie: Kopie des letzten Bilds der normalen Sicht (auf der Grafikkarte) für den Platz hinter dem Handy. *^/
	private static GpuTexture backdrop;
	private static boolean backdropValid;
	/^* Die Kopie ließ sich nicht anlegen oder zurückspielen: dann zeigt das Selfie wie früher auch hinter dem Handy. *^/
	private static boolean backdropBroken;
	/^* Hat sich die Kamera des Spiels in diesem Bild zum Spieler umgedreht? (Gesetzt bei der Kamera-Ausrichtung.) *^/
	private static boolean selfieFrame;
	/^* Leichter Weichzeichner hinter dem Handy (Versuch, ab Werk aus): {@code -Dtrsclient.reels.blur=true}. *^/
	private static boolean blur = Boolean.getBoolean("trsclient.reels.blur");

	private ReelsHooks() {
	}

	/^*
	 * Beim Start: Feed laden und das Modul freischalten. Schlägt etwas fehl, bleibt das Modul verborgen – der Start
	 * des Spiels hängt nicht daran.
	 *^/
	public static void init(TrsModules modules, Path gameDir, Path configDir) {
		try {
			String custom = System.getProperty("trsclient.reels.dir");
			Path dir = custom != null && !custom.trim().isEmpty() ? gameDir.resolve(custom.trim())
					: configDir.resolve("trsclient").resolve(FOLDER);
			Reels.init(modules, dir, message -> TrsClient.LOGGER.info(message));
			ReelsModules.platformSupported(true);
		} catch (RuntimeException | LinkageError e) {
			TrsClient.LOGGER.warn("MC-Reels ist nicht verfügbar", e);
		}
	}

	/^*
	 * Handy herausholen. Nur im Spiel, ohne offenen Bildschirm und nicht während einer Kamera-Fahrt oder der freien
	 * Kamera; Rückgabe: ist es jetzt draußen?
	 *^/
	public static boolean open() {
		Minecraft mc = Mc.mc();
		Reels reels = Reels.get();
		if (reels == null || mc.player == null || mc.level == null || Mc.screen() != null) return false;
		if (cameraRide()) return false;
		try {
			dropFrames();
			PhoneUi phone = new PhoneUi(reels, new Host());
			viewBefore = null;
			// Die Arme sind die eigenen: aus der Außenansicht geht es für die Dauer in die Ich-Sicht.
			keepFirstPerson();
			Mc.setScreen(new ReelsScreen(phone));
			return true;
		} catch (RuntimeException | LinkageError e) {
			TrsClient.LOGGER.warn("MC-Reels: Handy lässt sich nicht öffnen", e);
			restoreView();
			return false;
		}
	}

	/^* Läuft eine Kamera-Fahrt oder die freie Kamera? Dann bestimmt sie Bild und Perspektive allein. *^/
	private static boolean cameraRide() {
		try {
			Cinematic c = Cinematic.get();
			return c != null && c.overridden();
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/^* Hält gerade eine TRS-Funktion die Außenansicht (Emote-Kamera, Freelook) und stellt sie selbst zurück? *^/
	private static boolean viewHeldByFeature() {
		try {
			EmoteController emotes = EmoteHooks.emotes();
			if (emotes != null && emotes.cameraHeld()) return true;
			TrsClient client = TrsClient.get();
			return client != null && client.pvp().freelook().active();
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/^*
	 * Solange das Handy draußen ist (beim Öffnen und in jedem Tick): Ich-Sicht. Eine Außenansicht merkt sich das Handy
	 * nur, wenn sie dem Spieler gehört – nicht, wenn eine TRS-Funktion sie gerade hält (die stellt selbst zurück; das
	 * Handy würde sonst nach deren Ende eine veraltete Außenansicht wiederherstellen). Während einer Kamera-Fahrt
	 * passiert nichts.
	 *^/
	public static void keepFirstPerson() {
		try {
			Minecraft mc = Mc.mc();
			CameraType view = mc.options.getCameraType();
			if (view == CameraType.FIRST_PERSON || cameraRide()) return;
			if (!viewHeldByFeature()) viewBefore = view;
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		} catch (RuntimeException | LinkageError e) {
			TrsClient.LOGGER.warn("MC-Reels: Perspektive nicht umgestellt", e);
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
		releaseBackdrop();
		restoreView();
	}

	/^*
	 * Offene Bildwünsche und noch nicht abgeholte Bilder verwerfen (Handy auf bzw. zu). Ein Auslesen, das noch läuft,
	 * gehört danach zu keinem Handy mehr und wird bei der Ankunft verworfen.
	 *^/
	private static void dropFrames() {
		generation++;
		wantPhoto = false;
		wantFinder = false;
		FRAMES.clear();
	}

	private static void restoreView() {
		CameraType view = viewBefore;
		viewBefore = null;
		if (view == null) return;
		try {
			// Nur aus der Ich-Sicht des Handys zurück – hat inzwischen etwas anderes die Perspektive gestellt, bleibt die.
			if (Mc.mc().options.getCameraType() != CameraType.FIRST_PERSON || cameraRide()) return;
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
		selfieFrame = false;
		PhoneUi phone = phone();
		if (phone == null || !phone.selfieActive()) return 0f;
		// Erst umdrehen, wenn das Bild der normalen Sicht für den Platz hinter dem Handy gesichert ist (beforeGui).
		// Während einer Kamera-Fahrt gar nicht: sie bestimmt das Bild allein.
		if ((!backdropValid && !backdropBroken) || cameraRide()
				|| dev.theredstonee.trsclient.cinematic.CinematicHooks.frame() != null) return 0f;
		selfieFrame = true;
		return SELFIE_DISTANCE;
	}

	/^*
	 * Effekte „vor dem Gesicht“ (Feuer, Wasser, im Block steckend) zeichnet Minecraft nur in der Ich-Sicht. Beim Selfie
	 * steht die Kamera vor dem Spieler – dort gehören sie nicht ins Bild.
	 *^/
	public static boolean faceEffects(boolean firstPerson) {
		effectsHookSeen = true;
		return firstPerson && !selfieFrame;
	}

	/^*
	 * Direkt bevor Minecraft die Oberfläche zeichnet: wünscht das Handy ein Bild der Welt, wird das Hauptbild jetzt
	 * ausgelesen – ein Foto in voller Auflösung, ein Sucherbild so weit verkleinert, wie es der Sucher erlaubt
	 * ({@link PhotoFrame#readbackFactor}). Schlägt etwas fehl, bleibt nur das Bild aus; das Spiel läuft weiter.
	 *^/
	public static void beforeGui() {
		frameHookSeen = true;
		boolean turned = selfieFrame;
		selfieFrame = false;
		PhoneUi phone = phone();
		boolean selfie = phone != null && phone.selfieActive();
		// Kein Selfie mehr: beim nächsten Umschalten wird die normale Sicht neu gesichert.
		if (!selfie && !turned) backdropValid = false;
		if (!turned && !selfie && !wantPhoto && !wantFinder) return;
		try {
			Minecraft mc = Mc.mc();
			if (mc.level == null || phone == null) {
				dropFrames();
				return;
			}
			RenderTarget main = Mc.mainRenderTarget();
			if (selfie && !turned) {
				// „Selfie“ ist gewählt, dieses Bild zeigt aber noch die normale Sicht: genau das Bild, das hinter dem
				// Handy stehen bleibt. Für Sucher und Foto zählt erst das nächste, umgedrehte Bild.
				if (!backdropBroken) saveBackdrop(main);
				return;
			}
			if (turned && !selfie) {
				// Gerade auf „Foto“ zurückgeschaltet, das Bild ist aber noch umgedreht: hinter dem Handy bleibt die
				// normale Sicht, für Sucher und Foto zählt erst das nächste Bild.
				restoreBackdrop(main);
				backdropValid = false;
				return;
			}
			try {
				readback(main);
			} finally {
				// Nach dem Auslesen: hinter dem Handy steht wieder die normale Sicht.
				if (turned) restoreBackdrop(main);
			}
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

	/^* Das Hauptbild auslesen, wenn das Handy eines wünscht (immer nur eines zugleich). *^/
	private static void readback(RenderTarget target) {
		if (!wantPhoto && !wantFinder) return;
		if (readbackInFlight) {
			if (System.nanoTime() - readbackSince < READBACK_LOST_NS) return;
			// Verloren gegangen: nicht für immer warten.
			readbackInFlight = false;
		}
		if (target == null || target.width <= 0 || target.height <= 0) return;
		final boolean photo = wantPhoto;
		final int wanted = generation;
		int factor = photo ? 1 : PhotoFrame.readbackFactor(target.width, target.height, finderW, finderH, finderW, finderH);
		if (photo) wantPhoto = false;
		else wantFinder = false;
		readbackInFlight = true;
		readbackSince = System.nanoTime();
		net.minecraft.client.Screenshot.takeScreenshot(target, factor, image -> {
			try {
				// Nur für das Handy, das das Bild wollte: ist es inzwischen weg, bliebe das Bild sonst liegen.
				if (wanted == generation) {
					FRAMES.add(new PhoneUi.Frame(image.getPixels(), image.getWidth(), image.getHeight(), photo));
				}
			} catch (RuntimeException | OutOfMemoryError e) {
				TrsClient.LOGGER.warn("MC-Reels: Bild der Kamera verworfen: {}", e.toString());
			} finally {
				image.close();
				readbackInFlight = false;
			}
		});
	}

	// --- Selfie: die normale Sicht hinter dem Handy ---

	/^* Das Hauptbild (normale Sicht, noch ohne Oberfläche) auf der Grafikkarte beiseitelegen. *^/
	private static void saveBackdrop(RenderTarget main) {
		try {
			GpuTexture source = main == null ? null : main.getColorTexture();
			if (source == null || source.isClosed()) return;
			int w = source.getWidth(0), h = source.getHeight(0);
			if (w <= 0 || h <= 0) return;
			if (backdrop == null || backdrop.isClosed() || backdrop.getWidth(0) != w || backdrop.getHeight(0) != h
					|| backdrop.getFormat() != source.getFormat()) {
				releaseBackdrop();
				backdrop = RenderSystem.getDevice().createTexture("TRS MC-Reels: Sicht hinter dem Handy",
						GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
						source.getFormat(), w, h, 1, 1);
			}
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, backdrop, 0, 0, 0, 0, 0, w, h);
			backdropValid = true;
		} catch (RuntimeException | LinkageError e) {
			backdropFailed(e);
		}
	}

	/^* Die beiseitegelegte normale Sicht wieder über das (umgedrehte) Hauptbild legen. *^/
	private static void restoreBackdrop(RenderTarget main) {
		if (backdropBroken) return;
		try {
			GpuTexture target = main == null ? null : main.getColorTexture();
			GpuTexture saved = backdrop;
			if (target == null || target.isClosed() || saved == null || saved.isClosed() || !backdropValid
					|| saved.getWidth(0) != target.getWidth(0) || saved.getHeight(0) != target.getHeight(0)
					|| saved.getFormat() != target.getFormat()) {
				// Fenstergröße geändert: das nächste Bild zeigt die normale Sicht und wird neu gesichert.
				backdropValid = false;
				return;
			}
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(saved, target, 0, 0, 0, 0, 0,
					saved.getWidth(0), saved.getHeight(0));
			backdropSeen = true;
		} catch (RuntimeException | LinkageError e) {
			backdropFailed(e);
		}
	}

	private static void backdropFailed(Throwable e) {
		backdropBroken = true;
		backdropValid = false;
		releaseBackdrop();
		TrsClient.LOGGER.warn("MC-Reels: Sicht hinter dem Handy lässt sich beim Selfie nicht halten", e);
	}

	private static void releaseBackdrop() {
		GpuTexture old = backdrop;
		backdrop = null;
		backdropValid = false;
		if (old == null) return;
		try {
			if (!old.isClosed()) old.close();
		} catch (RuntimeException | LinkageError e) {
			// Schon weg (z. B. Grafikgerät neu angelegt).
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

	/^* Hat der Haken der Effekte vor dem Gesicht schon einmal gegriffen (Selfie ohne Feuer-/Wasser-Schleier)? *^/
	public static boolean effectsHookSeen() {
		return effectsHookSeen;
	}

	/^* Lag beim Selfie schon einmal die normale Sicht wieder hinter dem Handy? *^/
	public static boolean backdropSeen() {
		return backdropSeen;
	}

	/^* Ließ sich die normale Sicht nicht halten (dann zeigt das Selfie auch hinter dem Handy)? *^/
	public static boolean backdropBroken() {
		return backdropBroken;
	}

	/^* Selbsttest: die vom Spieler gewählte Außenansicht, die nach dem Wegstecken zurückkommt, oder null. *^/
	public static CameraType viewBefore() {
		return viewBefore;
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
