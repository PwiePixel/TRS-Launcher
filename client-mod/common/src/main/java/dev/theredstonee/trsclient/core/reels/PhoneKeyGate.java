package dev.theredstonee.trsclient.core.reels;

/**
 * Sperre gegen sofortiges Wieder-Öffnen: Die Handy-Taste steckt das Handy auch weg. Solange ein Bildschirm offen ist,
 * vergisst die Tastenabfrage ihre Zustände ({@code KeyPresses.releaseAll()}), eine noch gehaltene Taste sähe im ersten
 * Tick danach wie ein neuer Druck aus und das Handy ginge gleich wieder auf. Nach dem Schließen wird deshalb gesperrt,
 * bis die Taste einmal losgelassen war.
 */
public final class PhoneKeyGate {
	private boolean blocked;

	/** Beim Schließen des Handys aufrufen. */
	public void block() {
		blocked = true;
	}

	public boolean blocked() {
		return blocked;
	}

	/**
	 * Je Tick ohne offenen Bildschirm.
	 *
	 * @param pressedEdge die Taste wurde in diesem Tick neu gedrückt
	 * @param keyDown     die Taste ist gerade unten
	 * @return Handy jetzt öffnen
	 */
	public boolean shouldOpen(boolean pressedEdge, boolean keyDown) {
		if (blocked) {
			if (!keyDown) blocked = false;
			return false;
		}
		return pressedEdge;
	}
}
