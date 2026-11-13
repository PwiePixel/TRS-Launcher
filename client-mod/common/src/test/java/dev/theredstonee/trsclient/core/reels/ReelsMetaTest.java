package dev.theredstonee.trsclient.core.reels;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/** MC-Reels: die Angaben am Foto (Version, Loader, Shader, Instanz). */
class ReelsMetaTest {
	@Test
	void loaderLabelsAreProperNames() {
		assertEquals("Fabric", ReelsMeta.loaderLabel("fabric"));
		assertEquals("Quilt", ReelsMeta.loaderLabel("quilt"));
		assertEquals("Forge", ReelsMeta.loaderLabel("forge"));
		assertEquals("NeoForge", ReelsMeta.loaderLabel("neoforge"));
		assertEquals("NeoForge", ReelsMeta.loaderLabel("NEOFORGE"));
		assertEquals("Vanilla", ReelsMeta.loaderLabel("vanilla"));
		assertEquals("X", ReelsMeta.loaderLabel("x"));
		assertNull(ReelsMeta.loaderLabel(null));
		assertNull(ReelsMeta.loaderLabel(""));
	}

	@Test
	void normalizedReturnsNullForAllEmpty() {
		assertNull(ReelsMeta.normalized(null));
		assertNull(ReelsMeta.normalized(new ReelsMeta()));
		assertNull(ReelsMeta.normalized(new ReelsMeta("", " ", "\n", "§")));
		assertNull(ReelsMeta.normalized(new ReelsMeta("26.2!", "fabric-loader", null, null)), "nur Ungültiges");

		ReelsMeta in = new ReelsMeta(" 26.2 ", " Fabric ", " BSL ", " Pack ");
		ReelsMeta out = ReelsMeta.normalized(in);
		assertNotSame(in, out, "ein neues Objekt – die Eingabe bleibt, wie sie ist");
		assertEquals(" 26.2 ", in.mc);
		assertEquals("26.2", out.mc);
		assertEquals("fabric", out.loader);
		assertEquals("BSL", out.shader);
		assertEquals("Pack", out.pack);
		assertEquals("BSL", ReelsMeta.normalized(new ReelsMeta(null, null, "BSL", null)).shader);
		assertEquals("quilt", ReelsMeta.normalized(new ReelsMeta(null, "QUILT", null, null)).loader);
		assertEquals("1.21 Pre-Rel+1_x", ReelsMeta.normalized(new ReelsMeta("1.21 Pre-Rel+1_x", null, null, null)).mc, "16 Zeichen");
		assertNull(ReelsMeta.normalized(new ReelsMeta("1.21.11 Pre-Rel+1", null, null, null)), "17 Zeichen sind zu lang");
		assertNull(ReelsMeta.normalized(new ReelsMeta("26.2/x", null, null, null)));
		ReelsMeta copy = out.copy();
		assertNotSame(out, copy);
		assertEquals("26.2 · Fabric", copy.versionLabel());
	}

	@Test
	void versionLabelUsesOnlyKnownParts() {
		assertEquals("26.2 · Fabric", new ReelsMeta("26.2", "fabric", null, null).versionLabel());
		assertEquals("1.21.4 · NeoForge", new ReelsMeta("1.21.4", "neoforge", "BSL", "Pack").versionLabel());
		assertEquals("26.2", new ReelsMeta("26.2", null, null, null).versionLabel());
		assertEquals("Quilt", new ReelsMeta(null, "quilt", null, null).versionLabel());
		assertNull(new ReelsMeta(null, null, "BSL", "Pack").versionLabel());
		assertNull(new ReelsMeta().versionLabel());
	}
}
