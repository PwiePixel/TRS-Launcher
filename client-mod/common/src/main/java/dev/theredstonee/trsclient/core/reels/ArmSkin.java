package dev.theredstonee.trsclient.core.reels;

/**
 * Welche Stücke des Spieler-Skins (64×64) auf die beiden sichtbaren Flächen der Handy-Arme kommen – und wie:
 *
 * <ul>
 * <li><b>Vorderseite, schlanker Skin:</b> der Arm bleibt 4 Pixel breit (so ist es freigegeben). Die drei Spalten des
 * schlanken Arms werden nicht gestreckt, sondern die <em>mittlere Spalte wird verdoppelt</em> (Spalten 0, 1, 1, 2) –
 * jede Spalte bleibt ein Arm-Pixel breit, die hellen Ecken der Manschette werden nicht breiter.</li>
 * <li><b>Innenseite:</b> jede Texel-Zeile wird zu <em>einer</em> Farbe, dem Mittel ihrer vier Texel. Rohe Texel würden
 * helle Ecken direkt neben dem Handy zeigen, die wie ein Daumen wirken. Ohne die Pixel des Skins auf der CPU geht das
 * mit vier Blits über die ganze Breite mit Deckkraft 1, 1/2, 1/3, 1/4 – das ergibt genau das Mittel
 * ({@link #spans}). Mit den Pixeln geht es direkt ({@link #averagedRows}).</li>
 * </ul>
 *
 * Reine Daten; gezeichnet wird in {@code core.ui.reels}.
 */
public final class ArmSkin {
	/** Texel-Zeilen eines Arms (Länge). */
	public static final int ROWS = 12;
	/** Farbe der Vorderseite (unverändert). */
	public static final int TINT_FRONT = 0xFFFFFFFF;
	/** Farbe der Innenseite: auf 78 % abgedunkelt. */
	public static final int TINT_INNER = 0xFFC7C7C7;
	/**
	 * Innenseite je Zeile gemittelt (der freigegebene Look). {@code false} zeigt die rohen Texel der Skin-Seite – nur
	 * zum Vergleichen.
	 */
	public static final boolean INNER_AVERAGED = true;

	/** Ein Blit innerhalb eines Streifens: welche Texel-Spalten wohin, mit welcher Deckkraft. */
	public static final class Span {
		/** Erste Texel-Spalte, gezählt ab dem {@code u} aus {@link #uv}. */
		public final int column;
		/** Anzahl Texel-Spalten. */
		public final int texels;
		/** Von wo bis wo quer über die Fläche (0 = Texturrand links, 1 = rechts). */
		public final float s0;
		public final float s1;
		/** Deckkraft dieses Blits. */
		public final float alpha;

		Span(int column, int texels, float s0, float s1, float alpha) {
			this.column = column;
			this.texels = texels;
			this.s0 = s0;
			this.s1 = s1;
			this.alpha = alpha;
		}
	}

	private static final Span[] WHOLE = {new Span(0, 4, 0f, 1f, 1f)};
	private static final Span[] SLIM_FRONT = {
			new Span(0, 1, 0f, 0.25f, 1f),
			new Span(1, 1, 0.25f, 0.75f, 1f),
			new Span(2, 1, 0.75f, 1f, 1f)};
	private static final Span[] AVERAGED = {
			new Span(0, 1, 0f, 1f, 1f),
			new Span(1, 1, 0f, 1f, 1f / 2f),
			new Span(2, 1, 0f, 1f, 1f / 3f),
			new Span(3, 1, 0f, 1f, 1f / 4f)};

	private ArmSkin() {
	}

	/**
	 * Ausschnitt des Skins für eine Fläche.
	 *
	 * @param left    linker Arm (links im Bild) – sonst der rechte
	 * @param overlay zweite Skin-Schicht (Ärmel) statt der Grundschicht
	 * @param slim    schlanker Skin (Arm-Vorderseite 3 statt 4 Texel breit)
	 * @return {u, v, Breite, Höhe} in Texeln
	 */
	public static int[] uv(boolean left, ArmRig.Face face, boolean overlay, boolean slim) {
		int w = slim ? 3 : 4;
		if (left) {
			// Linker Arm: Grundschicht bei (32, 48), Ärmel bei (48, 48); die Innenseite steht links von der Vorderseite.
			int u = overlay ? 48 : 32;
			return face == ArmRig.Face.FRONT ? new int[]{u + 4, 52, w, ROWS} : new int[]{u, 52, 4, ROWS};
		}
		// Rechter Arm: Grundschicht bei (40, 16), Ärmel bei (40, 32); die Innenseite steht rechts von der Vorderseite.
		int v = overlay ? 36 : 20;
		return face == ArmRig.Face.FRONT ? new int[]{44, v, w, ROWS} : new int[]{44 + w, v, 4, ROWS};
	}

	/**
	 * Die Blits eines Streifens dieser Fläche, in Zeichenreihenfolge. Je Blit: {@code image(skin, u + column, v + Zeile,
	 * texels, 1, tint(face, alpha))} mit der Matrix {@code strip.span(s0, s1, texels)}.
	 */
	public static Span[] spans(ArmRig.Face face, boolean slim) {
		return spans(face, slim, INNER_AVERAGED);
	}

	static Span[] spans(ArmRig.Face face, boolean slim, boolean innerAveraged) {
		if (face == ArmRig.Face.FRONT) return slim ? SLIM_FRONT : WHOLE;
		return innerAveraged ? AVERAGED : WHOLE;
	}

	/** Farbe (ARGB) für einen Blit der Fläche mit dieser Deckkraft. */
	public static int tint(ArmRig.Face face, float alpha) {
		int rgb = (face == ArmRig.Face.FRONT ? TINT_FRONT : TINT_INNER) & 0xFFFFFF;
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		return a << 24 | rgb;
	}

	/**
	 * Die Innenseite gemittelt, wenn die Pixel des Skins vorliegen: je Texel-Zeile eine Farbe (ARGB, ungetönt). Die
	 * Farbe ist das nach Deckkraft gewichtete Mittel der vier Texel, die Deckkraft ihr Mittel (eine ganz durchsichtige
	 * Ärmel-Zeile bleibt durchsichtig).
	 *
	 * @param argb Skin, Zeilen von oben, {@code texW}×{@code texH} (64×64)
	 * @return 12 Farben, Schulter zuerst – oder null, wenn der Skin zu klein ist
	 */
	public static int[] averagedRows(int[] argb, int texW, int texH, boolean left, boolean overlay, boolean slim) {
		int[] r = uv(left, ArmRig.Face.INNER, overlay, slim);
		if (argb == null || texW < r[0] + r[2] || texH < r[1] + r[3] || argb.length < texW * texH) return null;
		int[] out = new int[ROWS];
		for (int row = 0; row < ROWS; row++) {
			long a = 0, red = 0, green = 0, blue = 0;
			for (int x = 0; x < r[2]; x++) {
				int p = argb[(r[1] + row) * texW + r[0] + x];
				int pa = p >>> 24;
				a += pa;
				red += (long) ((p >> 16) & 0xFF) * pa;
				green += (long) ((p >> 8) & 0xFF) * pa;
				blue += (long) (p & 0xFF) * pa;
			}
			if (a == 0) continue;
			int alpha = (int) ((a + r[2] / 2) / r[2]);
			out[row] = alpha << 24 | (int) ((red + a / 2) / a) << 16 | (int) ((green + a / 2) / a) << 8 | (int) ((blue + a / 2) / a);
		}
		return out;
	}
}
