package dev.theredstonee.trsclient.reels;

// MC-Reels (erste Testversion): Anbindung des Handys an Minecraft – vorerst nur Fabric 26.2. Auf jeder anderen Version
// ist diese Datei leer; das Modul bleibt dort über ReelsModules.platformSupported verborgen.
//? if >=26.2 && <26.3 {
/*import dev.theredstonee.trsclient.TrsClient;
import dev.theredstonee.trsclient.compat.Keys;
import dev.theredstonee.trsclient.compat.Mc;
import dev.theredstonee.trsclient.core.module.ReelsModules;
import dev.theredstonee.trsclient.core.module.TrsModules;
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

/^*
 * MC-Reels auf Fabric 26.2: Start (Speicherort, Modul freischalten), Handy öffnen, und was die Render-Haken wissen
 * müssen (Hand und Block-Umriss aus, solange das Handy draußen ist). Aussehen, Bedienung und Speicher stehen in
 * {@code core.reels} und {@code core.ui.reels}.
 *
 * <p>Alles hier läuft im Render-/Spiel-Thread.
 *^/
public final class ReelsHooks {
	/^* Ordner im Spielordner; für Tests mit {@code -Dtrsclient.reels.dir=<Ordner>} ersetzbar. *^/
	static final String FOLDER = "trs-reels";

	/^* Perspektive vor dem Öffnen (das Handy gibt es nur aus der Ich-Sicht); null = nichts zurückzustellen. *^/
	private static CameraType viewBefore;
	private static boolean handHookSeen;
	private static boolean outlineHookSeen;
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
		restoreView();
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
		public void close() {
			if (phoneOpen()) Mc.setScreen(null);
		}
	}
}
*///?}
