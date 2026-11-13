package dev.theredstonee.trsclient.core.reels;

/** Für {@link ShaderNameTest}: sieht für die Reflection aus wie die Iris-Klasse mit {@code getCurrentPackName()}. */
public final class FakeShaderMod {
	static volatile String name;
	static volatile boolean fail;
	static volatile int calls;

	private FakeShaderMod() {
	}

	public static String getCurrentPackName() {
		calls++;
		if (fail) throw new IllegalStateException("kaputt");
		return name;
	}

	/** Dieselbe Methode, aber nicht statisch und mit anderem Ergebnis – so etwas wird nicht aufgerufen. */
	public static final class WrongShape {
		public Object getCurrentPackName() {
			return "falsch";
		}
	}
}
