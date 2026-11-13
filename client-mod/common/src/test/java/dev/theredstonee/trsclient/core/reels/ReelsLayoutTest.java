package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.ui.Affine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handy und Arme von MC-Reels als reine Geometrie: die Zahlen der freigegebenen Testbilder, „unten immer bündig“ in
 * jedem Fenster, Streifen ohne Lücken und die Skin-Ausschnitte (schlank: mittlere Spalte doppelt; Innenseite gemittelt).
 */
class ReelsLayoutTest {
	private static final int[][] WINDOWS = {
			{1920, 1080}, {1280, 720}, {854, 480}, {2560, 1080}, {3440, 1440}, {3840, 2160}, {1024, 768},
			{1280, 1024}, {1920, 1200}, {1366, 768}, {800, 1000}, {600, 1000}, {400, 900}, {320, 240}};

	private static void assertPoint(double x, double y, float[] p, String what) {
		assertEquals(x, p[0], 0.2, what + " x");
		assertEquals(y, p[1], 0.2, what + " y");
	}

	// --- Handy ---

	@Test
	void landscapePhoneMatchesTheApprovedPicture() {
		PhoneLayout l = PhoneLayout.of(1920, 1080, PhoneHold.LANDSCAPE);
		assertEquals(1f, l.k, 1e-6);
		assertEquals(0f, l.ox, 1e-6);
		assertEquals(0f, l.oy, 1e-6);
		assertEquals(0f, l.lift, 1e-6);
		assertEquals(1.26f, l.q, 1e-6);
		assertEquals(58f, l.unit, 1e-6);
		assertEquals(385, l.phoneX);
		assertEquals(128, l.phoneY);
		assertEquals(1149, l.phoneW);
		assertEquals(570, l.phoneH);
		// Bildschirm: 832×404 Handy-Pixel mit 40 bzw. 24 Rand.
		assertEquals(436, l.screenX);
		assertEquals(158, l.screenY);
		assertEquals(1048, l.screenW);
		assertEquals(509, l.screenH);
		assertEquals(50, l.px(40));
		assertEquals(1, l.px(0.1f), "nie unter einem Pixel");
		assertEquals(469.44, l.armAnchorX(true), 0.01);
		assertEquals(1450.56, l.armAnchorX(false), 0.01);
		assertEquals(567.52, l.armAnchorY(), 0.01);
		assertEquals(-16f, l.armRollDeg(true), 1e-6);
		assertEquals(16f, l.armRollDeg(false), 1e-6);
	}

	@Test
	void portraitPhoneMatchesTheApprovedPicture() {
		PhoneLayout l = PhoneLayout.of(1920, 1080, PhoneHold.PORTRAIT);
		assertEquals(0.95f, l.q, 1e-6);
		assertEquals(54f, l.unit, 1e-6);
		assertEquals(745, l.phoneX);
		assertEquals(24, l.phoneY);
		assertEquals(429, l.phoneW);
		assertEquals(866, l.phoneH);
		// Bildschirm: 404×848 Handy-Pixel, oben 40 Rand, sonst 24.
		assertEquals(768, l.screenX);
		assertEquals(62, l.screenY);
		assertEquals(384, l.screenW);
		assertEquals(806, l.screenH);
		assertEquals(649.3, l.armAnchorX(true), 0.01);
		assertEquals(1270.7, l.armAnchorX(false), 0.01);
		assertEquals(680.4, l.armAnchorY(), 0.01);
		assertEquals(-14f, l.armRollDeg(true), 1e-6);
		assertEquals(14f, l.armRollDeg(false), 1e-6);
		assertEquals(0.2236, l.designW / PhoneLayout.STAGE_W, 0.0005, "hochkant: 22,4 % der Fensterbreite");
	}

	@Test
	void phoneTakesTheSameShareOfEvery16to9Window() {
		int[][] sizes = {{1920, 1080}, {1280, 720}, {854, 480}, {3840, 2160}};
		for (int[] s : sizes) {
			PhoneLayout l = PhoneLayout.of(s[0], s[1], PhoneHold.LANDSCAPE);
			assertEquals(0.5985, l.designW * l.k / s[0], 0.001, s[0] + "×" + s[1] + ": 59,85 % der Fensterbreite");
			assertEquals(0.5985, l.phoneW / (double) s[0], 0.001, s[0] + "×" + s[1] + " (gerundet)");
			assertEquals(l.phoneX, s[0] - l.phoneX - l.phoneW, 1, "mittig");
			assertTrue(l.screenX > l.phoneX && l.screenY > l.phoneY, "Bildschirm liegt im Gehäuse");
			assertTrue(l.screenX + l.screenW < l.phoneX + l.phoneW && l.screenY + l.screenH < l.phoneY + l.phoneH);
		}
	}

	@Test
	void sizeDependsOnTheWindowOnlyNeverOnTheGuiScale() throws Exception {
		// Strukturell: das Layout bekommt die GUI-Skalierung gar nicht erst.
		int factories = 0;
		for (Method m : PhoneLayout.class.getDeclaredMethods()) {
			if (!Modifier.isStatic(m.getModifiers()) || m.getReturnType() != PhoneLayout.class) continue;
			factories++;
			assertArrayEquals(new Class<?>[]{int.class, int.class, PhoneHold.class}, m.getParameterTypes(),
					"PhoneLayout.of kennt nur Fensterbreite, Fensterhöhe und Haltung");
		}
		assertEquals(1, factories);

		// Der Maßstab ist min(B/1920, H/1080): ein breiteres Fenster gleicher Höhe ändert die Größe nicht.
		PhoneLayout a = PhoneLayout.of(1920, 1080, PhoneHold.LANDSCAPE);
		PhoneLayout wide = PhoneLayout.of(2560, 1080, PhoneHold.LANDSCAPE);
		assertEquals(a.k, wide.k, 1e-6);
		assertEquals(a.phoneW, wide.phoneW);
		assertEquals(a.phoneH, wide.phoneH);
		assertEquals(a.phoneX + 320, wide.phoneX, "mittig im breiteren Fenster");
		// Halbes Fenster, halbes Handy.
		PhoneLayout half = PhoneLayout.of(960, 540, PhoneHold.LANDSCAPE);
		assertEquals(0.5f, half.k, 1e-6);
		assertEquals(a.phoneW / 2.0, half.phoneW, 1.0);
		// Ein schmales Fenster richtet sich nach der Breite.
		PhoneLayout narrow = PhoneLayout.of(960, 1080, PhoneHold.LANDSCAPE);
		assertEquals(0.5f, narrow.k, 1e-6);

		assertTrue(a.matches(1920, 1080, PhoneHold.LANDSCAPE));
		assertTrue(!a.matches(1920, 1080, PhoneHold.PORTRAIT) && !a.matches(1920, 1081, PhoneHold.LANDSCAPE));
		// Unsinnige Fenstergrößen werfen nicht.
		assertNotNull(ArmRig.of(PhoneLayout.of(0, 0, PhoneHold.PORTRAIT), true).strips(ArmRig.Face.FRONT, 2));
	}

	@Test
	void stageSitsOnTheBottomEdgeAndLiftsOnlyInTallWindows() {
		for (PhoneHold hold : PhoneHold.values()) {
			// 16:9 und breiter: Bühne unten, nichts angehoben.
			for (int[] s : new int[][]{{1920, 1080}, {1280, 720}, {2560, 1080}, {3440, 1440}, {3840, 2160}}) {
				PhoneLayout l = PhoneLayout.of(s[0], s[1], hold);
				assertEquals(0f, l.lift, 1e-4, hold + " " + s[0] + "×" + s[1]);
				assertEquals(s[1], l.wy(PhoneLayout.STAGE_H), 0.01, "Bühne endet am unteren Fensterrand");
			}
			// 16:10: die Bühne steht unten, das Handy ist noch über der Mitte – kein Anheben.
			assertEquals(0f, PhoneLayout.of(1920, 1200, hold).lift, 1e-4);
			// Hohe Fenster: angehoben, aber höchstens 4 Arm-Einheiten.
			for (int[] s : new int[][]{{1024, 768}, {1280, 1024}, {800, 1000}, {600, 1000}, {400, 900}}) {
				PhoneLayout l = PhoneLayout.of(s[0], s[1], hold);
				assertTrue(l.lift > 0f, hold + " " + s[0] + "×" + s[1] + " rückt zur Mitte");
				assertTrue(l.lift <= 4 * l.unit * l.k + 1e-3, "höchstens 4u");
				float centre = l.wy(l.designY + l.designH / 2f);
				assertTrue(centre >= s[1] / 2f - 0.01, "nie über die Fenstermitte hinaus");
			}
		}
		assertEquals(96.67, PhoneLayout.of(800, 1000, PhoneHold.LANDSCAPE).lift, 0.05, "4u bei k = 0,4167");
		assertEquals(28.1, PhoneLayout.of(1024, 768, PhoneHold.LANDSCAPE).lift, 0.1, "bis das Handy mittig steht");
	}

	// --- Arme: Projektion ---

	@Test
	void landscapeArmsProjectLikeTheApprovedPicture() {
		ArmRig left = ArmRig.of(PhoneLayout.of(1920, 1080, PhoneHold.LANDSCAPE), true);
		assertEquals(696f, left.length(), 1e-3, "12 × 58, keine Verlängerung bei 16:9");
		assertPoint(305.1, 530.9, left.point(ArmRig.Face.FRONT, 1f, 0f), "Vorderseite außen an der Hand");
		assertPoint(540.7, 463.3, left.point(ArmRig.Face.FRONT, 0f, 0f), "Vorderseite innen an der Hand");
		assertPoint(328.9, 1255.6, left.point(ArmRig.Face.FRONT, 1f, 696f), "Vorderseite außen an der Schulter");
		assertPoint(634.3, 1168.1, left.point(ArmRig.Face.FRONT, 0f, 696f), "Vorderseite innen an der Schulter");
		assertPoint(617.1, 600.4, left.point(ArmRig.Face.INNER, 0f, 0f), "Innenseite hinten an der Hand");
		assertPoint(718.6, 1250.7, left.point(ArmRig.Face.INNER, 0f, 696f), "Innenseite hinten an der Schulter");
		// Die Innenseite schließt an der Vorderkante an die Vorderseite an.
		assertPoint(540.7, 463.3, left.point(ArmRig.Face.INNER, 1f, 0f), "gemeinsame Kante");
		assertEquals(88.1, left.lowestEndY() - 1080, 0.2, "das Arm-Ende liegt 88 Pixel unter dem Bildrand");
	}

	@Test
	void portraitArmsProjectLikeTheApprovedPicture() {
		ArmRig left = ArmRig.of(PhoneLayout.of(1920, 1080, PhoneHold.PORTRAIT), true);
		assertEquals(648f, left.length(), 1e-3, "12 × 54");
		assertPoint(507.4, 647.9, left.point(ArmRig.Face.FRONT, 1f, 0f), "Vorderseite außen an der Hand");
		assertPoint(727.9, 592.9, left.point(ArmRig.Face.FRONT, 0f, 0f), "Vorderseite innen an der Hand");
		assertPoint(559.3, 1343.9, left.point(ArmRig.Face.FRONT, 1f, 648f), "Vorderseite außen an der Schulter");
		assertPoint(839.1, 1274.1, left.point(ArmRig.Face.FRONT, 0f, 648f), "Vorderseite innen an der Schulter");
		assertEquals(194.1, left.lowestEndY() - 1080, 0.2);
	}

	@Test
	void rightArmIsTheMirrorImage() {
		float[] ss = {0f, 0.25f, 0.5f, 1f};
		for (int[] w : WINDOWS) {
			for (PhoneHold hold : PhoneHold.values()) {
				PhoneLayout l = PhoneLayout.of(w[0], w[1], hold);
				ArmRig left = ArmRig.of(l, true);
				ArmRig right = ArmRig.of(l, false);
				assertTrue(left.left() && !right.left());
				assertEquals(left.length(), right.length(), 1e-4);
				for (ArmRig.Face face : ArmRig.Face.values()) {
					for (float s : ss) {
						for (float t = 0; t <= left.length(); t += left.length() / 4f) {
							float[] a = left.point(face, s, t);
							float[] b = right.point(face, 1f - s, t);
							assertEquals(w[0] - a[0], b[0], 0.01, "gespiegelt an der Fenstermitte");
							assertEquals(a[1], b[1], 0.01);
						}
					}
				}
			}
		}
	}

	// --- Arme: unten immer bündig ---

	@Test
	void armsAlwaysRunOutOfTheBottomEdge() {
		for (int[] w : WINDOWS) {
			for (PhoneHold hold : PhoneHold.values()) {
				PhoneLayout l = PhoneLayout.of(w[0], w[1], hold);
				String where = hold + " " + w[0] + "×" + w[1];
				for (boolean left : new boolean[]{true, false}) {
					ArmRig rig = ArmRig.of(l, left);
					assertTrue(rig.lowestEndY() >= w[1] + ArmRig.MARGIN_PX,
							where + ": Arm-Ende bei " + rig.lowestEndY() + ", Fenster endet bei " + w[1]);
					// Jeder Punkt der Schulter-Kante beider Flächen liegt unter dem Rand.
					for (ArmRig.Face face : ArmRig.Face.values()) {
						for (float s = 0f; s <= 1f; s += 0.125f) {
							assertTrue(rig.point(face, s, rig.length())[1] >= w[1] + ArmRig.MARGIN_PX - 0.01, where);
						}
					}
					// Und die Hand liegt im Bild (das Handy ist zu sehen).
					assertTrue(rig.point(ArmRig.Face.FRONT, 0.5f, 0f)[1] < w[1], where);
				}
				float ext = ArmRig.extension(l);
				assertEquals(0f, ext % (l.unit / 2f), 1e-3, where + ": Verlängerung in Schritten von u/2");
				assertEquals(12 * l.unit + ext, ArmRig.of(l, true).length(), 1e-3);
			}
		}
	}

	@Test
	void armsAreOnlyLengthenedInTallWindows() {
		// 16:9 und breiter (und 16:10): die 12u reichen.
		for (int[] w : new int[][]{{1920, 1080}, {1280, 720}, {854, 480}, {2560, 1080}, {3440, 1440}, {3840, 2160},
				{1366, 768}, {1920, 1200}, {1024, 768}, {320, 240}}) {
			for (PhoneHold hold : PhoneHold.values()) {
				assertEquals(0f, ArmRig.extension(PhoneLayout.of(w[0], w[1], hold)), 1e-6, hold + " " + w[0] + "×" + w[1]);
			}
		}
		assertEquals(0.5f * 58, ArmRig.extension(PhoneLayout.of(1280, 1024, PhoneHold.LANDSCAPE)), 1e-3);
		assertEquals(0f, ArmRig.extension(PhoneLayout.of(1280, 1024, PhoneHold.PORTRAIT)), 1e-3);
		for (int[] w : new int[][]{{800, 1000}, {600, 1000}, {400, 900}}) {
			assertEquals(2f * 58, ArmRig.extension(PhoneLayout.of(w[0], w[1], PhoneHold.LANDSCAPE)), 1e-3, w[0] + "×" + w[1]);
			assertEquals(0.5f * 54, ArmRig.extension(PhoneLayout.of(w[0], w[1], PhoneHold.PORTRAIT)), 1e-3, w[0] + "×" + w[1]);
		}
		// Nicht länger als nötig: ein halbes u weniger würde nicht reichen.
		PhoneLayout tall = PhoneLayout.of(800, 1000, PhoneHold.LANDSCAPE);
		ArmRig rig = ArmRig.of(tall, true);
		assertTrue(rig.point(ArmRig.Face.FRONT, 0f, rig.length() - tall.unit / 2f)[1] < 1000 + ArmRig.MARGIN_PX);
		// Selbst in einem absurd hohen Fenster bleibt es bei wenigen Einheiten: die Bühne rückt höchstens 4u nach oben.
		PhoneLayout absurd = PhoneLayout.of(100, 5000, PhoneHold.LANDSCAPE);
		float ext = ArmRig.extension(absurd);
		assertTrue(ext > 0f && ext <= 4f * 58, "Verlängerung " + ext);
		assertTrue(ArmRig.of(absurd, true).lowestEndY() >= 5000 + ArmRig.MARGIN_PX);
	}

	// --- Arme: Streifen ---

	@Test
	void stripsPerRowGrowWithTheWindow() {
		assertEquals(4, ArmRig.stripsPerRow(1f));
		assertEquals(3, ArmRig.stripsPerRow(1280 / 1920f));
		assertEquals(2, ArmRig.stripsPerRow(0.4f));
		assertEquals(2, ArmRig.stripsPerRow(0.05f));
		assertEquals(8, ArmRig.stripsPerRow(2f));
		assertEquals(8, ArmRig.stripsPerRow(4f));
	}

	@Test
	void stripsRunFromHandToShoulderOneTexelRowEach() {
		PhoneLayout l = PhoneLayout.of(1920, 1080, PhoneHold.LANDSCAPE);
		ArmRig rig = ArmRig.of(l, true);
		for (ArmRig.Face face : ArmRig.Face.values()) {
			List<ArmRig.Strip> strips = rig.strips(face, ArmRig.stripsPerRow(l.k));
			assertEquals(48, strips.size(), "12 Zeilen × 4 Streifen");
			assertSame(strips, rig.strips(face, 4), "einmal gerechnet, dann behalten");
			for (int i = 0; i < strips.size(); i++) {
				ArmRig.Strip st = strips.get(i);
				assertEquals(11 - i / 4, st.row, "Hand-Zeile (11) zuerst, Schulter-Zeile (0) zuletzt");
				assertEquals(i * 14.5f, st.t0, 1e-3);
				assertEquals((i + 1) * 14.5f, st.t1, 1e-3);
			}
		}
		assertEquals(24, rig.strips(ArmRig.Face.FRONT, 2).size(), "andere Feinheit: neu gerechnet");

		// Verlängerter Arm: die Verlängerung wiederholt die Schulter-Zeile.
		PhoneLayout tall = PhoneLayout.of(800, 1000, PhoneHold.LANDSCAPE);
		List<ArmRig.Strip> longer = ArmRig.of(tall, false).strips(ArmRig.Face.FRONT, 2);
		assertEquals((12 + 2) * 2, longer.size());
		for (int i = 22; i < longer.size(); i++) assertEquals(0, longer.get(i).row, "Streifen " + i);
		assertEquals(11, longer.get(0).row);
		// Verlängerung um ein halbes u bei drei Streifen je Zeile: der letzte Streifen endet genau am Arm-Ende.
		PhoneLayout odd = PhoneLayout.of(1280, 1024, PhoneHold.LANDSCAPE);
		ArmRig oddRig = ArmRig.of(odd, true);
		List<ArmRig.Strip> oddStrips = oddRig.strips(ArmRig.Face.INNER, 3);
		assertEquals(38, oddStrips.size(), "12,5 Zeilen × 3, aufgerundet");
		assertEquals(oddRig.length(), oddStrips.get(oddStrips.size() - 1).t1, 1e-3);
	}

	@Test
	void everyStripIsDrawableAndHitsItsCorners() {
		for (int[] w : WINDOWS) {
			for (PhoneHold hold : PhoneHold.values()) {
				PhoneLayout l = PhoneLayout.of(w[0], w[1], hold);
				int perRow = ArmRig.stripsPerRow(l.k);
				for (boolean left : new boolean[]{true, false}) {
					ArmRig rig = ArmRig.of(l, left);
					for (ArmRig.Face face : ArmRig.Face.values()) {
						String where = hold + " " + w[0] + "×" + w[1] + (left ? " links " : " rechts ") + face;
						double worst = 0;
						// Ohne Überlappung gemessen: wie weit liegt das Parallelogramm neben den echten Ecken?
						for (ArmRig.Strip st : rig.strips(face, perRow, 0f)) {
							assertNotNull(Affine.decompose(st.a / 4f, st.b / 4f, st.c, st.d),
									where + ": Streifen darf nicht gespiegelt oder entartet sein");
							worst = Math.max(worst, dist(rig.point(face, 0f, st.t1), st.e, st.f));
							worst = Math.max(worst, dist(rig.point(face, 1f, st.t1), st.e + st.a, st.f + st.b));
							worst = Math.max(worst, dist(rig.point(face, 0f, st.t0), st.e + st.c, st.f + st.d));
							worst = Math.max(worst, dist(rig.point(face, 1f, st.t0), st.e + st.a + st.c, st.f + st.b + st.d));
						}
						assertTrue(worst < 0.75, where + ": Ecken um " + worst + " Pixel verfehlt");
						if (w[0] == 1920 && w[1] == 1080) assertTrue(worst < 0.5, where + ": bei 1080p unter einem halben Pixel");
						// Mit Überlappung: gleiche Schulter-Kante, zur Hand hin länger – nur nicht am Hand-Ende. Die 0,75 Pixel
						// gelten senkrecht zur Streifenkante; die schräg liegende Innenseite wird dafür entlang des Arms
						// entsprechend mehr verlängert (höchstens 4 Pixel bzw. ihre eigene Länge).
						List<ArmRig.Strip> plain = rig.strips(face, perRow, 0f);
						List<ArmRig.Strip> drawn = rig.strips(face, perRow);
						assertEquals(plain.size(), drawn.size());
						for (int i = 0; i < drawn.size(); i++) {
							ArmRig.Strip a = plain.get(i), b = drawn.get(i);
							assertEquals(a.e, b.e, 1e-4);
							assertEquals(a.f, b.f, 1e-4);
							assertEquals(a.a, b.a, 1e-4);
							assertEquals(a.b, b.b, 1e-4);
							double len = Math.hypot(a.c, a.d);
							double extra = Math.hypot(b.c, b.d) - len;
							if (i == 0) {
								assertEquals(0.0, extra, 1e-3, where + " Streifen 0");
							} else {
								double sin = Math.abs(a.a * (double) a.d - a.b * (double) a.c) / (Math.hypot(a.a, a.b) * len);
								double limit = Math.max(ArmRig.OVERLAP_PX, Math.min(ArmRig.MAX_OVERLAP_PX, len));
								assertTrue(extra >= ArmRig.OVERLAP_PX - 1e-3 && extra <= limit + 1e-3, where + " Streifen " + i + ": " + extra);
								if (extra < limit - 1e-3) {
									assertEquals(ArmRig.OVERLAP_PX, extra * sin, 2e-3, where + " Streifen " + i + ": senkrecht gemessen");
								}
								if (face == ArmRig.Face.FRONT && w[0] == 1920 && w[1] == 1080) {
									assertEquals(ArmRig.OVERLAP_PX, extra, 0.05, where + ": die Vorderseite liegt fast gerade");
								}
							}
							assertNotNull(Affine.decompose(b.a / 4f, b.b / 4f, b.c, b.d), where);
						}
					}
				}
			}
		}
	}

	private static double dist(float[] p, float x, float y) {
		return Math.hypot(p[0] - x, p[1] - y);
	}

	// --- Skin ---

	@Test
	void skinRectanglesOfBothArms() {
		// Rechter Arm: Grundschicht (40, 16), Ärmel (40, 32).
		assertArrayEquals(new int[]{44, 20, 4, 12}, ArmSkin.uv(false, ArmRig.Face.FRONT, false, false));
		assertArrayEquals(new int[]{44, 36, 4, 12}, ArmSkin.uv(false, ArmRig.Face.FRONT, true, false));
		assertArrayEquals(new int[]{48, 20, 4, 12}, ArmSkin.uv(false, ArmRig.Face.INNER, false, false));
		assertArrayEquals(new int[]{48, 36, 4, 12}, ArmSkin.uv(false, ArmRig.Face.INNER, true, false));
		// Linker Arm: Grundschicht (32, 48), Ärmel (48, 48).
		assertArrayEquals(new int[]{36, 52, 4, 12}, ArmSkin.uv(true, ArmRig.Face.FRONT, false, false));
		assertArrayEquals(new int[]{52, 52, 4, 12}, ArmSkin.uv(true, ArmRig.Face.FRONT, true, false));
		assertArrayEquals(new int[]{32, 52, 4, 12}, ArmSkin.uv(true, ArmRig.Face.INNER, false, false));
		assertArrayEquals(new int[]{48, 52, 4, 12}, ArmSkin.uv(true, ArmRig.Face.INNER, true, false));
		// Schlank: die Vorderseite ist 3 Texel breit, die Innenseite des rechten Arms rückt um eins nach links.
		assertArrayEquals(new int[]{44, 20, 3, 12}, ArmSkin.uv(false, ArmRig.Face.FRONT, false, true));
		assertArrayEquals(new int[]{44, 36, 3, 12}, ArmSkin.uv(false, ArmRig.Face.FRONT, true, true));
		assertArrayEquals(new int[]{47, 20, 4, 12}, ArmSkin.uv(false, ArmRig.Face.INNER, false, true));
		assertArrayEquals(new int[]{47, 36, 4, 12}, ArmSkin.uv(false, ArmRig.Face.INNER, true, true));
		assertArrayEquals(new int[]{36, 52, 3, 12}, ArmSkin.uv(true, ArmRig.Face.FRONT, false, true));
		assertArrayEquals(new int[]{52, 52, 3, 12}, ArmSkin.uv(true, ArmRig.Face.FRONT, true, true));
		assertArrayEquals(new int[]{32, 52, 4, 12}, ArmSkin.uv(true, ArmRig.Face.INNER, false, true));
		assertArrayEquals(new int[]{48, 52, 4, 12}, ArmSkin.uv(true, ArmRig.Face.INNER, true, true));
	}

	@Test
	void slimFrontDoublesTheMiddleColumnInsteadOfStretching() {
		ArmSkin.Span[] wide = ArmSkin.spans(ArmRig.Face.FRONT, false);
		assertEquals(1, wide.length, "breiter Skin: ein Blit mit allen vier Spalten");
		assertEquals(0, wide[0].column);
		assertEquals(4, wide[0].texels);
		assertEquals(0f, wide[0].s0, 1e-6);
		assertEquals(1f, wide[0].s1, 1e-6);
		assertEquals(1f, wide[0].alpha, 1e-6);

		ArmSkin.Span[] slim = ArmSkin.spans(ArmRig.Face.FRONT, true);
		assertEquals(3, slim.length, "schlanker Skin: drei Blits je Streifen");
		float[] widthInUnits = {1f, 2f, 1f};
		float at = 0f;
		for (int i = 0; i < 3; i++) {
			assertEquals(i, slim[i].column, "Spalte " + i + " des schlanken Arms");
			assertEquals(1, slim[i].texels);
			assertEquals(at, slim[i].s0, 1e-6, "lückenlos aneinander");
			assertEquals(widthInUnits[i], (slim[i].s1 - slim[i].s0) * 4f, 1e-6, "Breiten 1u, 2u, 1u – die Mitte doppelt");
			assertEquals(1f, slim[i].alpha, 1e-6);
			at = slim[i].s1;
		}
		assertEquals(1f, at, 1e-6);
		int[] uv = ArmSkin.uv(true, ArmRig.Face.FRONT, false, true);
		assertEquals(uv[2], slim[2].column + slim[2].texels, "alle Spalten des Ausschnitts kommen vor, keine daneben");

		// Auf einem echten Streifen: jede Spalte genau so breit wie ein Arm-Pixel des breiten Skins.
		PhoneLayout l = PhoneLayout.of(1920, 1080, PhoneHold.LANDSCAPE);
		ArmRig.Strip st = ArmRig.of(l, true).strips(ArmRig.Face.FRONT, 4).get(0);
		float[] whole = st.span(wide[0].s0, wide[0].s1, wide[0].texels);
		assertEquals(st.a / 4f, whole[0], 1e-5);
		assertEquals(st.b / 4f, whole[1], 1e-5);
		assertEquals(st.e, whole[4], 1e-5);
		float[] reuse = new float[6];
		for (int i = 0; i < 3; i++) {
			float[] m = st.span(slim[i].s0, slim[i].s1, slim[i].texels, reuse);
			assertSame(reuse, m);
			assertEquals(widthInUnits[i] * whole[0], m[0], 1e-4, "ein Texel wird 1u bzw. 2u breit");
			assertEquals(widthInUnits[i] * whole[1], m[1], 1e-4);
			assertEquals(st.c, m[2], 1e-6);
			assertEquals(st.d, m[3], 1e-6);
			assertEquals(st.e + st.a * slim[i].s0, m[4], 1e-4);
			assertEquals(st.f + st.b * slim[i].s0, m[5], 1e-4);
			assertNotNull(Affine.decompose(m[0], m[1], m[2], m[3]));
		}
		// Die Innenseite ist bei schlankem Skin dieselbe wie bei breitem.
		assertSame(ArmSkin.spans(ArmRig.Face.INNER, false), ArmSkin.spans(ArmRig.Face.INNER, true));
	}

	@Test
	void innerFaceIsAveragedPerRow() {
		assertTrue(ArmSkin.INNER_AVERAGED, "freigegeben ist die gemittelte Innenseite");
		ArmSkin.Span[] spans = ArmSkin.spans(ArmRig.Face.INNER, false);
		assertEquals(4, spans.length, "vier Blits über die ganze Breite");
		for (int i = 0; i < 4; i++) {
			assertEquals(i, spans[i].column);
			assertEquals(1, spans[i].texels);
			assertEquals(0f, spans[i].s0, 1e-6);
			assertEquals(1f, spans[i].s1, 1e-6);
			assertEquals(1f / (i + 1), spans[i].alpha, 1e-6, "Deckkraft 1, 1/2, 1/3, 1/4");
		}
		// Zum Vergleichen: die rohen Texel der Seite in einem Blit.
		ArmSkin.Span[] raw = ArmSkin.spans(ArmRig.Face.INNER, false, false);
		assertEquals(1, raw.length);
		assertEquals(4, raw[0].texels);

		// Nachgerechnet mit 8-Bit-Deckkraft, wie die GUI mischt: Hand-Zeile „weiß, grün, grün, weiß“ des Skins aus den
		// Testbildern wird zu 7fb8a4 – eine Farbe, keine hellen Ecken.
		int[] row = {0xFFFFFF, 0x00724A, 0x00724A, 0xFFFFFF};
		double[] mix = {30, 30, 30}; // irgendein Hintergrund
		for (int i = 0; i < 4; i++) {
			double alpha = (ArmSkin.tint(ArmRig.Face.INNER, spans[i].alpha) >>> 24) / 255.0;
			for (int ch = 0; ch < 3; ch++) {
				int texel = (row[i] >> (16 - 8 * ch)) & 0xFF;
				mix[ch] = mix[ch] * (1 - alpha) + texel * alpha;
			}
		}
		assertEquals(0x7F, mix[0], 2.0);
		assertEquals(0xB8, mix[1], 2.0);
		assertEquals(0xA4, mix[2], 2.0);

		assertEquals(0xFFFFFFFF, ArmSkin.tint(ArmRig.Face.FRONT, 1f));
		assertEquals(0xFFC7C7C7, ArmSkin.tint(ArmRig.Face.INNER, 1f), "Innenseite auf 78 % abgedunkelt");
		assertEquals(0x80C7C7C7, ArmSkin.tint(ArmRig.Face.INNER, 0.5f));
		assertEquals(0x55C7C7C7, ArmSkin.tint(ArmRig.Face.INNER, 1f / 3f));
		assertEquals(0x40C7C7C7, ArmSkin.tint(ArmRig.Face.INNER, 0.25f));
		assertEquals(0x00FFFFFF, ArmSkin.tint(ArmRig.Face.FRONT, -1f));
	}

	@Test
	void averagedRowsFromSkinPixels() {
		int[] skin = new int[64 * 64];
		// Linker Arm, Innenseite der Grundschicht bei (32, 52): Hand-Zeile (11) „weiß, grün, grün, weiß“.
		int[] hand = {0xFFFFFFFF, 0xFF00724A, 0xFF00724A, 0xFFFFFFFF};
		for (int x = 0; x < 4; x++) {
			skin[(52 + 11) * 64 + 32 + x] = hand[x];
			skin[52 * 64 + 32 + x] = 0xFF06BF62;
		}
		int[] rows = ArmSkin.averagedRows(skin, 64, 64, true, false, false);
		assertEquals(12, rows.length);
		assertEquals(0xFF06BF62, rows[0], "einfarbige Zeile bleibt");
		assertEquals(0xFF80B9A5, rows[11], "Mittel der vier Texel (gerundet)");
		assertEquals(0, rows[5], "leere Zeile bleibt durchsichtig");

		// Ärmel des rechten Arms bei schlankem Skin (47, 36): halb durchsichtig – Farbe nach Deckkraft gewichtet.
		skin[36 * 64 + 47] = 0xFFFF0000;
		skin[36 * 64 + 48] = 0xFFFF0000;
		int[] sleeve = ArmSkin.averagedRows(skin, 64, 64, false, true, true);
		assertEquals(0x80FF0000, sleeve[0], "zwei von vier Texeln: halbe Deckkraft, volle Farbe");
		assertNull(ArmSkin.averagedRows(skin, 64, 32, true, false, false), "alter 64×32-Skin: kein linker Arm in der Textur");
		assertNull(ArmSkin.averagedRows(null, 64, 64, true, false, false));
	}
}
