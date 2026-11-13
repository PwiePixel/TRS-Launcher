package dev.theredstonee.trsclient.mixin;

// MC-Reels (erste Testversion, nur Fabric 26.2 – nur dort in der Mixin-Liste, sonst ist diese Datei leer):
// mit offener Debug-Anzeige (F3) zeichnet Minecraft ein Achsenkreuz in die Bildmitte – nicht als Teil des HUD, sondern
// am Ende von GameRenderer#renderLevel direkt in das Bild der Welt. Es läge damit mitten im Sucher des Handys und auf
// jedem Foto (beim Selfie auf dem Gesicht). Solange das Handy draußen ist, bleibt es deshalb weg – wie die Spielerhand,
// der Block-Umriss und das normale Fadenkreuz. Abgebrochen wird am Anfang von render: dort ist noch nichts begonnen
// (keine Matrix auf dem Stapel, kein Zeichendurchgang offen).
// „require = 0“: greift der Haken wegen einer anderen Mod nicht, startet das Spiel trotzdem – der Selbsttest meldet es.
//? if >=26.2 && <26.3 {
/*import dev.theredstonee.trsclient.reels.ReelsHooks;
import net.minecraft.client.renderer.DebugCrosshairRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugCrosshairRenderer.class)
public abstract class ReelsCrosshairMixin {
	@Inject(method = "render(Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V", at = @At("HEAD"),
			cancellable = true, require = 0)
	private void trsclient$reelsNoDebugCrosshair(CallbackInfo ci) {
		if (ReelsHooks.hideDebugCrosshair()) ci.cancel();
	}
}
*///?}
