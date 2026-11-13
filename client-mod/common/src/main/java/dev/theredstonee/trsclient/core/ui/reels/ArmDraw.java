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
 * <p><b>Saubere Kanten:</b> Die Grafikkarte rundet die Ecken eines Streifens auf Bruchteile eines Pixels; ein Pixel
 * direkt an seiner Kante würde sonst hin und wieder die Nachbarzeile des Skins treffen – einzelne falsche Punkte
 * mitten im Arm. Deshalb liest jeder Blit ein Stück innerhalb seiner Texel ({@link #INSET_U}, {@link #INSET_V}): der
 * Skin wird dafür in {@value #SUB}tel-Texeln adressiert. Und die Innenseite reicht {@value #EDGE_OVERLAP_PX} Pixel
 * unter die Vorderseite ({@link #under}), damit an der gemeinsamen Kante kein Loch bleibt.
 *
 * <p>Genau zwei Arme, keine Daumen, keine Finger.
 */
final class ArmDraw {
	/** Teile je Skin-Texel, in denen die Blits den Skin adressieren. */
	static final int SUB = 64;
	/** So weit innerhalb ihrer Texel lesen die Blits quer (1/64 Texel – verschiebt die Spalten um weniger als ein Pixel). */
	static final int INSET_U = 1;
	/** … und entlang des Arms (1/16 Texel; jede Zeile ist dort ohnehin eine Farbe). */
	static final int INSET_V = 4;
	/** So weit reicht die Innenseite unter die Vorderseite (Fensterpixel, senkrecht zur gemeinsamen Kante). */
	static final float EDGE_OVERLAP_PX = 1.5f;

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
		// Derselbe Skin, in SUBtel-Texeln adressiert (die Größe dient nur der Umrechnung in Anteile).
		TextureRef fine = new TextureRef(skin.id, skin.width * SUB, skin.height * SUB);
		float[] m = new float[6];
		int blits = 0;
		blits += arm(c, leftArm, perRow, fine, slim, sleeveLeft, m);
		blits += arm(c, rightArm, perRow, fine, slim, sleeveRight, m);
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

	/**
	 * Um wie viel (Anteil der Flächenbreite) ein Streifen der Innenseite breiter gezeichnet wird, damit er
	 * {@value #EDGE_OVERLAP_PX} Pixel <i>senkrecht</i> zur gemeinsamen Kante unter die Vorderseite reicht. Die Innenseite
	 * liegt sehr schräg im Bild – quer über die Fläche gemessen braucht es dafür deutlich mehr als diese Pixel. Höchstens
	 * die halbe Flächenbreite.
	 */
	static float under(ArmRig.Strip strip) {
		double area = Math.abs((double) strip.a * strip.d - (double) strip.b * strip.c);
		double along = Math.sqrt((double) strip.c * strip.c + (double) strip.d * strip.d);
		if (area < 1e-6 || along < 1e-6) return 0f;
		return (float) Math.min(0.5, EDGE_OVERLAP_PX * along / area);
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
			// Die Innenseite reicht an der Kante zur Vorderseite etwas darunter (links ist das s = 1, rechts s = 0).
			float under = face == ArmRig.Face.INNER ? under(strip) : 0f;
			for (int layer = 0; layer < 2; layer++) {
				int[] uv = layer == 0 ? base : over;
				if (uv == null) continue;
				for (ArmSkin.Span span : spans) {
					float s0 = span.s0 > 0f ? span.s0 - lap : span.s0;
					float s1 = span.s1;
					if (under > 0f) {
						if (rig.left()) s1 += under;
						else s0 -= under;
					}
					strip.span(s0, s1, span.texels, m);
					// Quelle: die Texel des Blits, ringsum ein Stück nach innen genommen (in SUBtel-Texeln).
					int w = span.texels * SUB - 2 * INSET_U, h = SUB - 2 * INSET_V;
					float fx = span.texels / (float) w, fy = 1f / h;
					c.push();
					if (Affine.apply(c, m[0] * fx, m[1] * fx, m[2] * fy, m[3] * fy, m[4], m[5])) {
						c.image(skin, (uv[0] + span.column) * SUB + INSET_U, (uv[1] + strip.row) * SUB + INSET_V, w, h,
								ArmSkin.tint(face, span.alpha));
						blits++;
					}
					c.pop();
				}
			}
		}
		return blits;
	}
}
