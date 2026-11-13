package dev.theredstonee.trsclient.core.reels;

/**
 * Wo Handy und Arme im Fenster stehen (MC-Reels). Alles hängt nur an der Fenstergröße in Pixeln und an der Haltung –
 * die GUI-Skalierung kommt hier bewusst nicht vor: Handy und Arme sind bei jeder Skalierung gleich groß.
 *
 * <p>Gerechnet wird auf einer Bühne von {@value #STAGE_W}×{@value #STAGE_H} „Entwurfspixeln“ (die freigegebenen
 * Testbilder). Die Bühne wird als Ganzes ins Fenster eingepasst und steht auf dem unteren Fensterrand, damit die Arme
 * immer unten aus dem Bild laufen. In Fenstern, die höher sind als 16:9, rückt sie ein Stück zur Mitte (höchstens
 * {@value #MAX_LIFT_UNITS} Arm-Einheiten); was dann unten fehlt, gleicht {@link ArmRig#extension} mit längeren Armen
 * aus.
 *
 * <p>Unveränderlich. Die Werte für „quer“ sind die vom Besitzer freigegebenen; die für „hochkant“ sind an das
 * freigegebene Hochkant-Bild angepasst.
 */
public final class PhoneLayout {
	public static final float STAGE_W = 1920f;
	public static final float STAGE_H = 1080f;

	/** Gehäuse des Handys in Handy-Pixeln: lange und kurze Seite. */
	static final float BODY_LONG = 912f;
	static final float BODY_SHORT = 452f;

	// Quer (freigegeben: ps=1.26, py=128, u=58, ax=84, ay=130, rz=16).
	static final float LAND_SCALE = 1.26f;
	static final float LAND_TOP = 128f;
	static final float LAND_UNIT = 58f;
	static final float LAND_ANCHOR_X = 84f;
	static final float LAND_ANCHOR_Y = 130f;
	static final float LAND_ROLL_DEG = 16f;
	// Hochkant (ps=0.95, py=24; u, ax, ay, rz an das freigegebene Bild angepasst).
	static final float PORT_SCALE = 0.95f;
	static final float PORT_TOP = 24f;
	static final float PORT_UNIT = 54f;
	static final float PORT_ANCHOR_X = 96f;
	static final float PORT_ANCHOR_Y = 210f;
	static final float PORT_ROLL_DEG = 14f;

	/** Rand um den Bildschirm des Handys (Handy-Pixel): an den kurzen Enden bzw. oben breiter. */
	static final float INSET_WIDE = 40f;
	static final float INSET_NARROW = 24f;
	/** So weit rückt die Bühne in hohen Fenstern höchstens zur Mitte (Arm-Einheiten). */
	static final int MAX_LIFT_UNITS = 4;

	public final PhoneHold hold;
	public final int windowW;
	public final int windowH;
	/** Maßstab der Bühne: Entwurfspixel → Fensterpixel. */
	public final float k;
	/** Versatz der Bühne im Fenster (Fensterpixel). */
	public final float ox;
	public final float oy;
	/** So weit ist die Bühne vom unteren Rand zur Mitte gerückt (Fensterpixel, 0 bei 16:9 und breiter). */
	public final float lift;
	/** Handy-Pixel → Fensterpixel (Größe des Handys mal {@link #k}). */
	public final float q;
	/** Arm-Einheit u in Entwurfspixeln (ein Skin-Pixel des Arms; der Arm ist 4u breit und 12u lang). */
	public final float unit;
	/** Gehäuse in Entwurfspixeln. */
	public final float designX;
	public final float designY;
	public final float designW;
	public final float designH;
	/** Gehäuse in Fensterpixeln. */
	public final int phoneX;
	public final int phoneY;
	public final int phoneW;
	public final int phoneH;
	/** Bildschirm des Handys in Fensterpixeln. */
	public final int screenX;
	public final int screenY;
	public final int screenW;
	public final int screenH;

	private final float anchorDx;
	private final float anchorDy;
	private final float rollDeg;

	private PhoneLayout(int windowW, int windowH, PhoneHold hold) {
		boolean land = hold != PhoneHold.PORTRAIT;
		this.hold = land ? PhoneHold.LANDSCAPE : PhoneHold.PORTRAIT;
		this.windowW = Math.max(1, windowW);
		this.windowH = Math.max(1, windowH);
		float scale = land ? LAND_SCALE : PORT_SCALE;
		unit = land ? LAND_UNIT : PORT_UNIT;
		anchorDx = land ? LAND_ANCHOR_X : -PORT_ANCHOR_X;
		anchorDy = land ? LAND_ANCHOR_Y : PORT_ANCHOR_Y;
		rollDeg = land ? LAND_ROLL_DEG : PORT_ROLL_DEG;
		designW = (land ? BODY_LONG : BODY_SHORT) * scale;
		designH = (land ? BODY_SHORT : BODY_LONG) * scale;
		designX = (STAGE_W - designW) / 2f;
		designY = land ? LAND_TOP : PORT_TOP;

		k = Math.min(this.windowW / STAGE_W, this.windowH / STAGE_H);
		q = scale * k;
		ox = (this.windowW - STAGE_W * k) / 2f;
		float oyBottom = this.windowH - STAGE_H * k;
		// Nur in Fenstern, die höher sind als 16:9: das Handy Richtung Mitte holen.
		float centre = oyBottom + (designY + designH / 2f) * k;
		lift = Math.min(Math.max(0f, centre - this.windowH / 2f), MAX_LIFT_UNITS * unit * k);
		oy = oyBottom - lift;

		phoneX = Math.round(wx(designX));
		phoneY = Math.round(wy(designY));
		phoneW = Math.max(1, Math.round(designW * k));
		phoneH = Math.max(1, Math.round(designH * k));

		float left = land ? INSET_WIDE : INSET_NARROW;
		float top = land ? INSET_NARROW : INSET_WIDE;
		float right = land ? INSET_WIDE : INSET_NARROW;
		float bottom = INSET_NARROW;
		int sx0 = Math.round(wx(designX + left * scale));
		int sy0 = Math.round(wy(designY + top * scale));
		int sx1 = Math.round(wx(designX + designW - right * scale));
		int sy1 = Math.round(wy(designY + designH - bottom * scale));
		screenX = sx0;
		screenY = sy0;
		screenW = Math.max(1, sx1 - sx0);
		screenH = Math.max(1, sy1 - sy0);
	}

	/**
	 * Layout für ein Fenster von {@code windowW}×{@code windowH} Pixeln (Framebuffer, nicht GUI-Einheiten) und die
	 * Haltung.
	 */
	public static PhoneLayout of(int windowW, int windowH, PhoneHold hold) {
		return new PhoneLayout(windowW, windowH, hold);
	}

	/** Gilt dieses Layout für das Fenster und die Haltung (dann muss nichts neu gerechnet werden)? */
	public boolean matches(int windowW, int windowH, PhoneHold hold) {
		return this.windowW == Math.max(1, windowW) && this.windowH == Math.max(1, windowH) && this.hold == hold;
	}

	/** Entwurfs-x → Fenster-x. */
	public float wx(float designX) {
		return ox + k * designX;
	}

	/** Entwurfs-y → Fenster-y. */
	public float wy(float designY) {
		return oy + k * designY;
	}

	/** Eine Größe aus dem Handy-Entwurf (Handy-Pixel) in Fensterpixeln, mindestens 1. */
	public int px(float phoneLocal) {
		return Math.max(1, Math.round(phoneLocal * q));
	}

	/** Wo die Hand des linken bzw. rechten Arms am Handy liegt (Entwurfspixel). */
	public float armAnchorX(boolean left) {
		return left ? designX + anchorDx : designX + designW - anchorDx;
	}

	public float armAnchorY() {
		return designY + designH - anchorDy;
	}

	/** Drehung des Arms in der Bildebene (Grad): die Arme laufen nach unten leicht auseinander. */
	public float armRollDeg(boolean left) {
		return left ? -rollDeg : rollDeg;
	}
}
