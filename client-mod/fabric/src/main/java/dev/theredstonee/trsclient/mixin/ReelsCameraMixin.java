package dev.theredstonee.trsclient.mixin;

// MC-Reels (erste Testversion, nur Fabric 26.2 – nur dort in der Mixin-Liste, sonst ist diese Datei leer):
// „Selfie“ in der Kamera des Handys. Am Ende der Kamera-Ausrichtung dreht sich die Kamera des Spiels um und rückt ein
// Stück vor das Gesicht des Spielers – dieselbe Rechnung wie Minecrafts Ansicht von vorn (F5), nur näher. Wände
// schieben die Kamera wie dort heran. „detached“ lässt Minecraft die eigene Figur zeichnen. Die Perspektive in den
// Optionen bleibt die Ich-Sicht (das Handy stellt sie beim Öffnen ein), deshalb startet die Rechnung immer am Auge.
// Während einer Kamera-Fahrt passiert nichts: sie bestimmt das Bild allein.
// „require = 0“: greift der Haken wegen einer anderen Mod nicht, startet das Spiel trotzdem – der Selbsttest meldet es.
//? if >=26.2 && <26.3 {
/*import dev.theredstonee.trsclient.cinematic.CinematicHooks;
import dev.theredstonee.trsclient.reels.ReelsHooks;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class ReelsCameraMixin {
	@Shadow
	private float xRot;

	@Shadow
	private float yRot;

	@Shadow
	private boolean detached;

	@Shadow
	private float getMaxZoom(float cameraDistance) {
		throw new AssertionError();
	}

	@Shadow
	protected abstract void move(float forwards, float up, float right);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity(F)V", at = @At("TAIL"), require = 0)
	private void trsclient$reelsSelfie(CallbackInfo ci) {
		float distance = ReelsHooks.selfieDistance();
		if (distance <= 0f || CinematicHooks.frame() != null) return;
		setRotation(yRot + 180.0F, -xRot);
		move(-getMaxZoom(distance), 0.0F, 0.0F);
		detached = true;
	}
}
*///?}
