package dev.theredstonee.trsclient.core.reels;

/** Welcher Post gerade zu sehen ist: ein Post je Seite, Mausrad und Tasten blättern, an beiden Enden ist Schluss. */
public final class FeedPager {
	private int index;
	private int size;
	/** Angesammelte Bruchteile des Mausrads (Touchpads liefern weniger als eine Raste je Ereignis). */
	private double wheel;

	/** Aktuelle Seite, 0 = neuester Post. */
	public int index() {
		return index;
	}

	public int size() {
		return size;
	}

	/** Anzahl der Posts setzen; die Seite bleibt, soweit es sie noch gibt. */
	public void size(int n) {
		size = Math.max(0, n);
		if (index > size - 1) index = Math.max(0, size - 1);
	}

	/** Um {@code delta} Seiten blättern (positiv = ältere Posts). Rückgabe: Seite hat sich geändert. */
	public boolean step(int delta) {
		int next = Math.max(0, Math.min(Math.max(0, size - 1), index + delta));
		if (next == index) return false;
		index = next;
		return true;
	}

	/**
	 * Mausrad: {@code amount} wie von Minecraft (positiv = nach oben). Rad nach unten = nächster Post; je volle Raste
	 * eine Seite. Rückgabe: Seite hat sich geändert.
	 */
	public boolean wheel(double amount) {
		if (Double.isNaN(amount) || Double.isInfinite(amount)) return false;
		wheel += amount;
		boolean changed = false;
		while (wheel <= -1.0) {
			wheel += 1.0;
			if (!step(1)) {
				// Am Ende nichts ansparen, sonst bräuchte der Weg zurück erst so viele Rasten.
				wheel = 0;
				break;
			}
			changed = true;
		}
		while (wheel >= 1.0) {
			wheel -= 1.0;
			if (!step(-1)) {
				wheel = 0;
				break;
			}
			changed = true;
		}
		return changed;
	}

	/** Zurück zum neuesten Post. */
	public void first() {
		to(0);
	}

	/** Zu einer bestimmten Seite (nach dem Posten: zum neuen Post); außerhalb des Feeds zur nächstgelegenen. */
	public void to(int page) {
		index = Math.max(0, Math.min(Math.max(0, size - 1), page));
		wheel = 0;
	}
}
