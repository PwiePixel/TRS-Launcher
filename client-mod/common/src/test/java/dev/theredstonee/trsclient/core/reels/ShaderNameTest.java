package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.perf.ShaderPacks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MC-Reels: Name des Shaderpacks – nur mit erfundenen Eingaben; hier läuft weder Iris noch ein Shaderpack. */
class ShaderNameTest {
	private static final ClassLoader LOADER = ShaderNameTest.class.getClassLoader();
	private static final String FAKE = FakeShaderMod.class.getName();

	@BeforeEach
	@AfterEach
	void reset() {
		ShaderPacks.force(false);
		ShaderName.resetForTest();
		FakeShaderMod.name = null;
		FakeShaderMod.fail = false;
		FakeShaderMod.calls = 0;
	}

	@Test
	void offInternalAndEmptyAreNoName() {
		assertNull(ShaderName.clean(null));
		assertNull(ShaderName.clean(""));
		assertNull(ShaderName.clean("   "));
		assertNull(ShaderName.clean("(off)"));
		assertNull(ShaderName.clean("(internal)"));
		assertNull(ShaderName.clean(" (off) "));
		assertNull(ShaderName.clean(".zip"), "nur die Endung ist kein Name");
		assertEquals("BSL_v8.2.09", ShaderName.clean("BSL_v8.2.09"));
		assertEquals("(official)", ShaderName.clean("(official)"));
	}

	@Test
	void zipEndingIsStripped() {
		assertEquals("ComplementaryReimagined_r5.5.1", ShaderName.clean("ComplementaryReimagined_r5.5.1.zip"));
		assertEquals("BSL", ShaderName.clean("BSL.ZIP"));
		assertEquals("BSL", ShaderName.clean("  BSL.Zip  "));
		assertEquals("Ordner.zip.alt", ShaderName.clean("Ordner.zip.alt"), "nur am Ende");
		assertEquals("Sildurs Vibrant", ShaderName.clean("Sildurs\nVibrant§.zip"), "gesäubert wie jeder Text im Feed");
	}

	@Test
	void longNamesAreCapped() {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 90; i++) b.append((char) ('a' + i % 26));
		String name = ShaderName.clean(b + ".zip");
		assertEquals(ReelsMeta.MAX_SHADER, name.length());
		assertTrue(b.toString().startsWith(name));
		assertEquals(40, ReelsMeta.MAX_SHADER);
	}

	@Test
	void withoutIrisThereIsNoName() {
		assertNull(ShaderName.current(), "ohne aktives Shaderpack wird gar nicht erst gefragt");
		assertFalse(ShaderName.failedForTest());
		// Als liefe ein Shaderpack: die Iris-Klasse gibt es hier nicht – der Aufruf scheitert still.
		ShaderPacks.force(true);
		assertNull(ShaderName.current());
		assertTrue(ShaderName.failedForTest(), "nach dem ersten Fehlschlag wird nie wieder gefragt");
		assertNull(ShaderName.current());
		assertNull(ShaderName.read(LOADER, FAKE), "auch nicht bei einer Klasse, die es gäbe");
		assertEquals(0, FakeShaderMod.calls);
	}

	@Test
	void readsTheNameFromTheStaticMethodAndStopsAfterTheFirstFailure() {
		FakeShaderMod.name = "BSL_v8.2.09.zip";
		assertEquals("BSL_v8.2.09.zip", ShaderName.read(LOADER, FAKE), "roh, wie die Methode ihn liefert");
		assertEquals("BSL_v8.2.09", ShaderName.clean(ShaderName.read(LOADER, FAKE)));
		FakeShaderMod.name = "(off)";
		assertNull(ShaderName.clean(ShaderName.read(LOADER, FAKE)));
		FakeShaderMod.name = null;
		assertNull(ShaderName.read(LOADER, FAKE));
		assertFalse(ShaderName.failedForTest(), "kein Name ist kein Fehlschlag");
		assertEquals(4, FakeShaderMod.calls);

		FakeShaderMod.fail = true;
		assertNull(ShaderName.read(LOADER, FAKE), "ein Fehler in der fremden Methode kommt nicht durch");
		assertTrue(ShaderName.failedForTest());
		FakeShaderMod.fail = false;
		FakeShaderMod.name = "BSL";
		assertNull(ShaderName.read(LOADER, FAKE));
		assertEquals(5, FakeShaderMod.calls, "danach wird die Methode nicht mehr angefasst");
	}

	@Test
	void aMethodOfTheWrongShapeIsNotCalled() {
		assertNull(ShaderName.read(LOADER, FakeShaderMod.WrongShape.class.getName()));
		assertTrue(ShaderName.failedForTest());
		ShaderName.resetForTest();
		assertNull(ShaderName.read(LOADER, "dev.theredstonee.trsclient.core.reels.GibtEsNicht"));
		assertTrue(ShaderName.failedForTest());
	}
}
