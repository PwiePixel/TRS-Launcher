package dev.theredstonee.trsclient.core.ui.reels;

import dev.theredstonee.trsclient.core.reels.ArmRig;
import dev.theredstonee.trsclient.core.reels.ArmSkin;
import dev.theredstonee.trsclient.core.ui.Affine;
import dev.theredstonee.trsclient.core.ui.Canvas;
import dev.theredstonee.trsclient.core.ui.TextureRef;

import java.util.List;

/**
 * Zeichnet die beiden Handy-Arme (MC-Reels) aus dem Skin des Spielers: je Arm die Innenseite, dann die Vorderseite,
 * jede als dünne Streifen von der Hand zur Schulter ({@link ArmRig#strips}). Jeder Streifen ist eine Texel-Zeile des
 * Skins, als Parallelogramm gelegt ({@link Affine}).
 *
 * <ul>
 * <li><b>Schlanker Skin:</b> der Arm bleibt 4 Pixel breit; die mittlere der drei Spalten wird verdoppelt
 * (drei Blits je Streifen: 1u, 2u, 1u – {@link ArmSkin#spans}).</li>
 * <li><b>Innenseite:</b> je Zeile eine Farbe (das Mittel der vier Texel: vier Blits über die ganze Breite mit
 * Deckkraft 1, 1/2, 1/3, 1/4) und auf 78 % abgedunkelt – keine hellen Ecken neben dem Handy.</li>
 * <li><b>Ärmel:</b> die zweite Skin-Schicht kommt direkt über die Grundschicht – aber nur, wenn der Spieler den Ärmel
 * dieses Arms in den Skin-Einstellungen nicht ausgeschaltet hat.</li>
 * </ul>
 *
 * Genau zwei Arme, keine Daumen, keine Finger.
 */
final class ArmDraw {
	private ArmDraw() {
	}

	/**
	 * @param perRow      Streifen je Texel-Zeile ({@link ArmRig#stripsPerRow})
	 * @param skin        Skin des Spielers (64×64) – null: es wird nichts gezeichnet
	 * @param slim        schlanker Skin
	 * @param sleeveLeft  Ärmel-Schicht des linken Arms zeichnen
	 * @param sleeveRight Ärmel-Schicht des rechten Arms zeichnen
	 * @return Anzahl der Blits (für Tests)
	 */
	static int draw(Canvas c, ArmRig leftArm, ArmRig rightArm, int perRow, TextureRef skin, boolean slim, boolean sleeveLeft,
			boolean sleeveRight) {
		if (skin == null || !c.images()) return 0;
		float[] m = new float[6];
		int blits = 0;
		blits += arm(c, leftArm, perRow, skin, slim, sleeveLeft, m);
		blits += arm(c, rightArm, perRow, skin, slim, sleeveRight, m);
		return blits;
	}

	private static int arm(Canvas c, ArmRig rig, int perRow, TextureRef skin, boolean slim, boolean sleeve, float[] m) {
		if (rig == null) return 0;
		int blits = 0;
		// Die Innenseite zuerst: die Vorderseite überdeckt die gemeinsame Kante.
		blits += face(c, rig, ArmRig.Face.INNER, perRow, skin, slim, sleeve, m);
		blits += face(c, rig, ArmRig.Face.FRONT, perRow, skin, slim, sleeve, m);
		return blits;
	}

	private static int face(Canvas c, ArmRig rig, ArmRig.Face face, int perRow, TextureRef skin, boolean slim, boolean sleeve,
			float[] m) {
		List<ArmRig.Strip> strips = rig.strips(face, perRow);
		ArmSkin.Span[] spans = ArmSkin.spans(face, slim);
		int[] base = ArmSkin.uv(rig.left(), face, false, slim);
		int[] over = sleeve ? ArmSkin.uv(rig.left(), face, true, slim) : null;
		int blits = 0;
		for (int i = 0; i < strips.size(); i++) {
			ArmRig.Strip strip = strips.get(i);
			// Nebeneinander liegende Blits (schlanker Skin) überlappen sich um einen Bruchteil eines Pixels – sonst
			// könnte zwischen ihnen eine Haarlinie offen bleiben.
			float width = (float) Math.sqrt(strip.a * strip.a + strip.b * strip.b);
			float lap = width > 1e-3f ? ArmRig.OVERLAP_PX / width : 0f;
			for (int layer = 0; layer < 2; layer++) {
				int[] uv = layer == 0 ? base : over;
				if (uv == null) continue;
				for (ArmSkin.Span span : spans) {
					strip.span(span.s0 > 0f ? span.s0 - lap : span.s0, span.s1, span.texels, m);
					c.push();
					if (Affine.apply(c, m[0], m[1], m[2], m[3], m[4], m[5])) {
						c.image(skin, uv[0] + span.column, uv[1] + strip.row, span.texels, 1, ArmSkin.tint(face, span.alpha));
						blits++;
					}
					c.pop();
				}
			}
		}
		return blits;
	}
}
