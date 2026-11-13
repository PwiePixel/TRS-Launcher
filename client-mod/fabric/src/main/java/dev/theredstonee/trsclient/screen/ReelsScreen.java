package dev.theredstonee.trsclient.screen;

// MC-Reels (erste Testversion): Bildschirm des Handys – vorerst nur Fabric 26.2, sonst ist diese Datei leer.
//? if >=26.2 && <26.3 {
/*import dev.theredstonee.trsclient.TrsClient;
import dev.theredstonee.trsclient.core.i18n.I18n;
import dev.theredstonee.trsclient.core.ui.reels.PhoneUi;
import dev.theredstonee.trsclient.reels.ReelsHooks;
import dev.theredstonee.trsclient.ui.Gfx;

/^*
 * Das Handy (MC-Reels) als Minecraft-Bildschirm. Aussehen und Bedienung stehen in {@link PhoneUi}; hier nur:
 * kein Vanilla-Hintergrund (das Spiel bleibt sichtbar, das Handy dunkelt selbst ab) und Aufräumen, wenn der
 * Bildschirm verschwindet.
 *
 * <p>Weil {@code overlay()} des Handys false bleibt, ruht das TRS-HUD samt Minimap, solange es offen ist; die
 * Schnellleiste von Minecraft bleibt.
 *^/
public final class ReelsScreen extends TrsUiScreen {
	private static boolean blurBroken;
	private final PhoneUi phone;

	public ReelsScreen(PhoneUi phone) {
		super(I18n.tr("module.mcReels"), phone);
		this.phone = phone;
	}

	public PhoneUi phone() {
		return phone;
	}

	@Override
	protected boolean customBackground() {
		return true;
	}

	@Override
	protected void drawBackground(Gfx g, float partialTick) {
		// Das Handy dunkelt selbst ab. Der Weichzeichner ist ein Versuch und ab Werk aus (ReelsHooks.blur).
		if (blurBroken || !ReelsHooks.blur()) return;
		try {
			((net.minecraft.client.gui.GuiGraphicsExtractor) g.raw()).blurBeforeThisStratum();
		} catch (RuntimeException | LinkageError e) {
			blurBroken = true;
			TrsClient.LOGGER.warn("MC-Reels: Weichzeichner abgeschaltet", e);
		}
	}

	/^* Auch wenn etwas anderes den Bildschirm ersetzt (Tod, Verbindung getrennt): Texturen frei, Feed gesichert. *^/
	@Override
	public void removed() {
		super.removed();
		ReelsHooks.closed(phone);
	}
}
*///?}
