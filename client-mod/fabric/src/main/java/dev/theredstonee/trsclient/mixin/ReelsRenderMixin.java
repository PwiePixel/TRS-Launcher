package dev.theredstonee.trsclient.mixin;

// MC-Reels (erste Testversion, nur Fabric 26.2 – nur dort in der Mixin-Liste, sonst ist diese Datei leer):
// solange das Handy draußen ist, zeichnet Minecraft weder die normale Spielerhand (die gezeichneten Arme ersetzen
// sie) noch den Umriss des anvisierten Blocks (er läge hinter dem Handy und auf den Fotos). Dazu der Punkt, an dem die
// Kamera des Handys ihr Bild holt: direkt bevor die Oberfläche gezeichnet wird – die Welt ist fertig (samt
// Leucht-Umrissen und Effekten), HUD, Chat, Handy und Arme fehlen noch.
// „require = 0“: greift ein Haken wegen einer anderen Mod nicht, startet das Spiel trotzdem – der Selbsttest meldet es.
//? if >=26.2 && <26.3 {
/*import dev.theredstonee.trsclient.reels.ReelsHooks;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class ReelsRenderMixin {
	@Inject(method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void trsclient$reelsHideHand(CallbackInfo ci) {
		if (ReelsHooks.hideHand()) ci.cancel();
	}

	@Inject(method = "shouldRenderBlockOutline()Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void trsclient$reelsNoOutline(CallbackInfoReturnable<Boolean> cir) {
		if (ReelsHooks.hideBlockOutline()) cir.setReturnValue(false);
	}

	@Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
	private void trsclient$reelsFrame(CallbackInfo ci) {
		ReelsHooks.beforeGui();
	}
}
*///?}
