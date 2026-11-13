package dev.theredstonee.trsclient.core.reels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die Geometrie eines Handy-Arms (MC-Reels): ein Quader, 4u breit, 12u lang und 4u tief (u = ein Skin-Pixel,
 * {@link PhoneLayout#unit}) – immer 4u breit, auch bei schlankem Skin. Die Hand liegt am Handy
 * ({@link PhoneLayout#armAnchorX}), der Arm läuft von dort auf den Betrachter zu und unten aus dem Bild; durch die
 * Perspektive wird er nach unten breiter. Zwei Flächen sind zu sehen: die Vorderseite und die Innenseite (zum anderen
 * Arm hin).
 *
 * <p>Lokale Koordinaten: x quer (−2u … 2u), t entlang des Arms ab der Hand (0 … Länge), z zum Betrachter (−2u … 2u).
 * Projektion wie im freigegebenen Entwurf: um {@value #TILT_DEG}° nach vorn gekippt, in der Bildebene gedreht
 * ({@link PhoneLayout#armRollDeg}), Perspektive {@value #PERSPECTIVE} um den Punkt ({@value #ORIGIN_X},
 * {@value #ORIGIN_Y}) der Bühne.
 *
 * <p><b>Unten immer bündig:</b> reicht das Arm-Ende in einem hohen Fenster nicht mindestens {@value #MARGIN_PX} Pixel
 * unter den Fensterrand, wird der Arm verlängert ({@link #extension}); die Verlängerung wiederholt die Schulter-Zeile
 * des Skins.
 *
 * <p><b>Streifen:</b> Die GUI kann nur Parallelogramme zeichnen, kein Trapez. Jede Fläche wird deshalb entlang des Arms
 * in dünne Streifen geschnitten ({@link #strips}); jeder Streifen zeigt eine Texel-Zeile des Skins und ist das
 * Parallelogramm, das seine vier projizierten Ecken am besten trifft (unter einem halben Pixel daneben bei 1080p).
 *
 * <p>Unveränderlich; die Streifen werden beim ersten Abruf gerechnet und behalten.
 */
public final class ArmRig {
	/** Die sichtbaren Flächen. */
	public enum Face {
		/** Vorderseite (zum Betrachter). */
		FRONT,
		/** Innenseite (zum anderen Arm hin). */
		INNER
	}

	public static final float PERSPECTIVE = 1800f;
	public static final float ORIGIN_X = 960f;
	public static final float ORIGIN_Y = 648f;
	public static final float TILT_DEG = 34f;
	/** So weit liegt das Arm-Ende mindestens unter dem Fensterrand (Fensterpixel). */
	public static final float MARGIN_PX = 2f;
	/** Jeder Streifen reicht so weit über seinen Nachbarn zur Hand hin (Fensterpixel) – so bleibt keine Haarlinie offen. */
	public static final float OVERLAP_PX = 0.75f;
	/** Länge des Arms in Einheiten (Texel-Zeilen). */
	static final int LENGTH_UNITS = ArmSkin.ROWS;
	/** Höchste Verlängerung in Einheiten. */
	static final int MAX_EXTENSION_UNITS = 24;

	/**
	 * Ein Streifen einer Fläche. Die Matrix bildet das Einheitsquadrat auf den Streifen ab: (0,0) = Schulter-Seite am
	 * linken Texturrand, x quer über die Fläche, y zur Hand hin –
	 * {@code x' = a·x + c·y + e}, {@code y' = b·x + d·y + f} (Fensterpixel).
	 */
	public static final class Strip {
		/** Texel-Zeile des Skins, 0 = Schulter … 11 = Hand. */
		public final int row;
		public final float a, b, c, d, e, f;
		/** Abschnitt des Arms (Entwurfspixel ab der Hand): von {@code t0} (zur Hand) bis {@code t1} (zur Schulter). */
		public final float t0, t1;

		Strip(int row, float a, float b, float c, float d, float e, float f, float t0, float t1) {
			this.row = row;
			this.a = a;
			this.b = b;
			this.c = c;
			this.d = d;
			this.e = e;
			this.f = f;
			this.t0 = t0;
			this.t1 = t1;
		}

		/**
		 * Matrix für {@code Affine.apply}, die {@code texels} Texel-Spalten (eine Zeile hoch) auf den Teil des Streifens
		 * von {@code s0} bis {@code s1} legt (0 … 1 quer über die Fläche).
		 *
		 * @param out sechs Plätze {a, b, c, d, e, f}; null = neues Feld
		 */
		public float[] span(float s0, float s1, int texels, float[] out) {
			float[] m = out != null ? out : new float[6];
			float w = (s1 - s0) / Math.max(1, texels);
			m[0] = a * w;
			m[1] = b * w;
			m[2] = c;
			m[3] = d;
			m[4] = e + a * s0;
			m[5] = f + b * s0;
			return m;
		}

		public float[] span(float s0, float s1, int texels) {
			return span(s0, s1, texels, null);
		}
	}

	private final PhoneLayout layout;
	private final boolean left;
	private final double unit;
	private final double length;
	private final double anchorX;
	private final double anchorY;
	private final double sinTilt;
	private final double cosTilt;
	private final double sinRoll;
	private final double cosRoll;
	private List<Strip> frontStrips;
	private List<Strip> innerStrips;
	private int cachedPerRow;

	private ArmRig(PhoneLayout layout, boolean left, double extension) {
		this.layout = layout;
		this.left = left;
		this.unit = layout.unit;
		this.length = LENGTH_UNITS * unit + extension;
		this.anchorX = layout.armAnchorX(left);
		this.anchorY = layout.armAnchorY();
		double tilt = Math.toRadians(TILT_DEG);
		double roll = Math.toRadians(layout.armRollDeg(left));
		sinTilt = Math.sin(tilt);
		cosTilt = Math.cos(tilt);
		sinRoll = Math.sin(roll);
		cosRoll = Math.cos(roll);
	}

	/** Der linke bzw. rechte Arm zu diesem Layout, schon so lang, dass er unten aus dem Bild läuft. */
	public static ArmRig of(PhoneLayout layout, boolean left) {
		return new ArmRig(layout, left, extension(layout));
	}

	/**
	 * Um wie viel (Entwurfspixel) die Arme über ihre 12u hinaus verlängert werden, damit beide Arm-Enden mindestens
	 * {@value #MARGIN_PX} Pixel unter dem Fensterrand liegen: in Schritten von u/2, höchstens 24u. 0 in jedem Fenster,
	 * das 16:9 oder breiter ist.
	 */
	public static float extension(PhoneLayout layout) {
		double step = layout.unit / 2.0;
		double limit = layout.windowH + MARGIN_PX;
		for (int i = 0; i <= MAX_EXTENSION_UNITS * 2; i++) {
			double extension = i * step;
			if (new ArmRig(layout, true, extension).lowestEndY() >= limit
					&& new ArmRig(layout, false, extension).lowestEndY() >= limit) {
				return (float) extension;
			}
		}
		return (float) (MAX_EXTENSION_UNITS * 2 * step);
	}

	/** Streifen je Texel-Zeile: mehr in großen Fenstern, damit die Kante glatt bleibt (2 … 8). */
	public static int stripsPerRow(float k) {
		return Math.max(2, Math.min(8, (int) Math.ceil(4.0 * k - 1e-6)));
	}

	public boolean left() {
		return left;
	}

	/** Länge des Arms in Entwurfspixeln: 12u plus Verlängerung. */
	public float length() {
		return (float) length;
	}

	/**
	 * Ein Punkt einer Fläche im Fenster.
	 *
	 * @param s quer über die Fläche, 0 = linker Texturrand … 1 = rechter
	 * @param t entlang des Arms ab der Hand (Entwurfspixel)
	 * @return {x, y} in Fensterpixeln
	 */
	public float[] point(Face face, float s, float t) {
		double[] p = project(face, s, t);
		return new float[]{(float) p[0], (float) p[1]};
	}

	private double[] project(Face face, double s, double t) {
		double x, z;
		if (face == Face.FRONT) {
			// Die Textur steht auf dem Kopf: Hand-Zeile am Handy, linke Spalte rechts im Bild.
			x = 2 * unit - 4 * unit * s;
			z = 2 * unit;
		} else if (left) {
			x = 2 * unit;
			z = -2 * unit + 4 * unit * s;
		} else {
			x = -2 * unit;
			z = 2 * unit - 4 * unit * s;
		}
		double y1 = t * cosTilt - z * sinTilt;
		double z1 = t * sinTilt + z * cosTilt;
		double x2 = x * cosRoll - y1 * sinRoll;
		double y2 = x * sinRoll + y1 * cosRoll;
		double scale = PERSPECTIVE / (PERSPECTIVE - z1);
		double stageX = ORIGIN_X + (anchorX + x2 - ORIGIN_X) * scale;
		double stageY = ORIGIN_Y + (anchorY + y2 - ORIGIN_Y) * scale;
		return new double[]{layout.ox + layout.k * stageX, layout.oy + layout.k * stageY};
	}

	/**
	 * Das obere der Ecken am Schulter-Ende beider sichtbaren Flächen (Fenster-y) – liegt es unter dem Fensterrand, ist
	 * vom Arm-Ende nichts zu sehen.
	 */
	public float lowestEndY() {
		double y = Double.MAX_VALUE;
		for (Face face : Face.values()) {
			y = Math.min(y, project(face, 0, length)[1]);
			y = Math.min(y, project(face, 1, length)[1]);
		}
		return (float) y;
	}

	/**
	 * Die Streifen einer Fläche, von der Hand zur Schulter (in dieser Reihenfolge zeichnen; die Innenseite vor der
	 * Vorderseite). Jeder Streifen überlappt seinen Vorgänger um {@value #OVERLAP_PX} Pixel.
	 *
	 * @param perRow Streifen je Texel-Zeile ({@link #stripsPerRow})
	 */
	public synchronized List<Strip> strips(Face face, int perRow) {
		int n = Math.max(1, perRow);
		if (n != cachedPerRow) {
			frontStrips = null;
			innerStrips = null;
			cachedPerRow = n;
		}
		if (face == Face.FRONT) {
			if (frontStrips == null) frontStrips = Collections.unmodifiableList(strips(face, n, OVERLAP_PX));
			return frontStrips;
		}
		if (innerStrips == null) innerStrips = Collections.unmodifiableList(strips(face, n, OVERLAP_PX));
		return innerStrips;
	}

	List<Strip> strips(Face face, int perRow, float overlapPx) {
		double step = unit / perRow;
		int count = (int) Math.ceil(length / step - 1e-6);
		List<Strip> out = new ArrayList<Strip>(count);
		for (int i = 0; i < count; i++) {
			double ta = i * step;
			double tb = Math.min(length, ta + step);
			// Ecken: erster Index = s, zweiter = Schulter-Seite (0) bzw. Hand-Seite (1).
			double[] p00 = project(face, 0, tb);
			double[] p10 = project(face, 1, tb);
			double[] p01 = project(face, 0, ta);
			double[] p11 = project(face, 1, ta);
			// Bestes Parallelogramm: gemittelte Kanten um den gemeinsamen Mittelpunkt.
			double ux = ((p10[0] - p00[0]) + (p11[0] - p01[0])) / 2;
			double uy = ((p10[1] - p00[1]) + (p11[1] - p01[1])) / 2;
			double vx = ((p01[0] - p00[0]) + (p11[0] - p10[0])) / 2;
			double vy = ((p01[1] - p00[1]) + (p11[1] - p10[1])) / 2;
			double ox = (p00[0] + p10[0] + p01[0] + p11[0]) / 4 - ux / 2 - vx / 2;
			double oy = (p00[1] + p10[1] + p01[1] + p11[1]) / 4 - uy / 2 - vy / 2;
			// Zur Hand hin etwas länger (nicht am Hand-Ende selbst, dort ist nichts zu überdecken).
			double len = Math.sqrt(vx * vx + vy * vy);
			if (i > 0 && overlapPx > 0f && len > 1e-6) {
				double grow = 1 + overlapPx / len;
				vx *= grow;
				vy *= grow;
			}
			int row = (LENGTH_UNITS - 1) - (int) Math.floor((ta + tb) / 2 / unit);
			if (row < 0) row = 0;
			out.add(new Strip(row, (float) ux, (float) uy, (float) vx, (float) vy, (float) ox, (float) oy, (float) ta, (float) tb));
		}
		return out;
	}
}
