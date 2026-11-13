package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.config.TrsConfig;
import dev.theredstonee.trsclient.core.module.Category;
import dev.theredstonee.trsclient.core.module.KeySetting;
import dev.theredstonee.trsclient.core.module.NewSince;
import dev.theredstonee.trsclient.core.module.ReelsModules;
import dev.theredstonee.trsclient.core.module.TrsModules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MC-Reels als Modul: ab Werk aus, nur auf der freigeschalteten Plattform sichtbar, Tasten und Haltung. */
class ReelsModulesTest {
	@AfterEach
	void reset() {
		// Der Schalter gilt für die ganze JVM – andere Tests sollen das Modul wieder verborgen sehen.
		ReelsModules.platformSupported(false);
	}

	@Test
	void hiddenUnlessThePlatformSupportsIt() {
		TrsModules m = new TrsModules();
		assertFalse(ReelsModules.platformSupported(), "ohne Baum, der das Handy zeichnet, ist nichts freigeschaltet");
		assertFalse(m.reels.reels.available(), "Kachel bleibt auf allen anderen Plattformen verborgen");

		ReelsModules.platformSupported(true);
		assertTrue(m.reels.reels.available(), "Fabric 26.2 schaltet die Kachel frei");
		assertTrue(new TrsModules().reels.reels.available(), "der Schalter gilt für jede Modul-Instanz");

		ReelsModules.platformSupported(false);
		assertFalse(m.reels.reels.available());
	}

	@Test
	void offByDefaultWithKeysAndHold() {
		TrsModules m = new TrsModules();
		assertEquals("mcReels", m.reels.reels.id());
		assertFalse(m.reels.reels.isEnabled(), "MC-Reels ist ab Werk aus");
		assertSame(m.reels.reels, m.registry.byId("mcReels"));
		assertEquals(Category.MISC, m.reels.reels.category());
		assertEquals("key.keyboard.k", m.reels.openKey.get());
		assertEquals("key.keyboard.r", m.reels.rotateKey.get());
		assertFalse(m.reels.openKey.isLinked(), "eigene Modul-Taste, keine Vanilla-Belegung");
		assertSame(PhoneHold.LANDSCAPE, m.reels.hold.get());
		assertEquals(NewSince.REELS, NewSince.of("mcReels"));
	}

	@Test
	void holdAndKeysSurviveSaveAndLoad() {
		TrsModules m = new TrsModules();
		m.reels.reels.setEnabled(true);
		m.reels.hold.set(PhoneHold.PORTRAIT);
		m.reels.openKey.set("key.keyboard.j");
		m.reels.rotateKey.set(KeySetting.NONE);
		TrsConfig saved = m.registry.capture();

		TrsModules again = new TrsModules();
		again.registry.apply(saved);
		assertTrue(again.reels.reels.isEnabled());
		assertSame(PhoneHold.PORTRAIT, again.reels.hold.get());
		assertEquals("key.keyboard.j", again.reels.openKey.get());
		assertFalse(again.reels.rotateKey.isBound());
	}

	@Test
	void holdTogglesBetweenTheTwo() {
		assertSame(PhoneHold.PORTRAIT, PhoneHold.LANDSCAPE.other());
		assertSame(PhoneHold.LANDSCAPE, PhoneHold.PORTRAIT.other());
		assertEquals("Landscape", PhoneHold.LANDSCAPE.label());
		assertEquals("Portrait", PhoneHold.PORTRAIT.label());
	}
}
