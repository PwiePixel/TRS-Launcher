package dev.theredstonee.trsclient.core.module;

import dev.theredstonee.trsclient.core.reels.PhoneHold;

/**
 * MC-Reels (TRS Client {@link NewSince#REELS}): ein Handy im Spiel, gehalten von zwei gezeichneten Armen, mit lokalem
 * Foto-Feed (Likes, Kommentare, Streak), Kamera (Foto / Selfie) und Posten. Erste Testversion: kein Server, kein Netz –
 * alles bleibt auf diesem PC ({@code core.reels}).
 *
 * <p>Das Modul ist ab Werk aus und erscheint nur dort, wo der Baum das Handy wirklich zeichnen kann (vorerst nur
 * Fabric 26.2): dieser Baum ruft beim Start {@link #platformSupported(boolean)}; überall sonst bleibt die Kachel
 * verborgen ({@link Module#available()}).
 */
public final class ReelsModules {
	/** Nur der Baum, der das Handy zeichnen kann, schaltet das Modul frei. */
	private static volatile boolean platformSupported;

	/** Vom Baum beim Start gesetzt (Fabric 26.2) – gilt für die ganze JVM; Tests setzen es zurück. */
	public static void platformSupported(boolean supported) {
		platformSupported = supported;
	}

	public static boolean platformSupported() {
		return platformSupported;
	}

	public final Module reels;
	/** Taste „Handy öffnen“ (ohne Vanilla-Belegung; dieselbe Taste steckt es wieder weg). */
	public final KeySetting openKey;
	/** Taste „Handy drehen“ – wird nur gelesen, solange das Handy offen ist. */
	public final KeySetting rotateKey;
	/** Zuletzt gewählte Haltung. */
	public final ChoiceSetting<PhoneHold> hold;

	ReelsModules(ModuleRegistry registry) {
		reels = registry.register(new Module("mcReels", "MC-Reels",
				"Take out a phone in the game: a photo feed, a camera with selfie mode, posts with a caption and a streak. "
						+ "First test version – everything stays on this PC.", false));
		openKey = reels.add(new KeySetting("key", "Open phone", "key.keyboard.h"));
		rotateKey = reels.add(new KeySetting("rotateKey", "Rotate phone", "key.keyboard.r"));
		hold = reels.add(new ChoiceSetting<PhoneHold>("hold", "Hold", PhoneHold.class, PhoneHold.LANDSCAPE));
		reels.icon("camera").category(Category.MISC).availableWhen(new java.util.concurrent.Callable<Boolean>() {
			@Override
			public Boolean call() {
				return Boolean.valueOf(platformSupported);
			}
		});
	}
}
