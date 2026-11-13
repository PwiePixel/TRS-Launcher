package dev.theredstonee.trsclient.mixin;

// MC-Reels (erste Testversion, nur Fabric 26.2 – nur dort in der Mixin-Liste, sonst ist diese Datei leer):
// solange das Handy draußen ist, reicht Minecraft weder Arm noch Gegenstand der Ich-Sicht ein. ReelsRenderMixin hält
// schon GameRenderer#renderItemInHand an – das ist aber nur Minecrafts eigener Weg. Mit einem Shaderpaket zeichnet
// Iris die Hand selbst (HandRenderer → ItemInHandRenderer#submitHandsWithItems, an renderItemInHand vorbei), und die
// Spielerhand blieb neben den gezeichneten Armen stehen. Deshalb hier eine Stufe tiefer, an der Stelle, durch die jeder
// Weg kommt: submitArmWithItem je Hand. submitHandsWithItems selbst läuft bewusst bis zum Ende durch – Iris räumt an
// dessen RETURN auf (endRender), ein Abbruch am Anfang ließe das aus.
// „require = 0“: greift der Haken wegen einer anderen Mod nicht, startet das Spiel trotzdem – der Selbsttest meldet es.
//? if >=26.2 && <26.3 {
/*import dev.theredstonee.trsclient.reels.ReelsHooks;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ReelsHandMixin {
	@Inject(method = "submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void trsclient$reelsNoArm(CallbackInfo ci) {
		if (ReelsHooks.hideArm()) ci.cancel();
	}
}
*///?}
