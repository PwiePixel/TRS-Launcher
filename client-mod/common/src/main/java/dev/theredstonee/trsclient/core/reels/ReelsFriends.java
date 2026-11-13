package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.online.Friends;
import dev.theredstonee.trsclient.core.online.FriendsView;
import dev.theredstonee.trsclient.core.online.TrsOnline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Namen der TRS-Freunde für den Filter „Freunde“ und die Namensvorschläge (MC-Reels). Gelesen wird nur, was TRS Online
 * für einen anderen Bildschirm schon geladen hat: hier wird nie etwas angefordert ({@code want}, {@code refresh}) –
 * MC-Reels selbst löst also keine Anfrage ans Netz aus.
 */
public final class ReelsFriends {
	private ReelsFriends() {
	}

	/** Die Namen der Freunde – null, solange die Liste unbekannt ist (nicht angemeldet oder noch nie geladen). */
	public static Collection<String> names() {
		try {
			return names(TrsOnline.current());
		} catch (RuntimeException e) {
			return null;
		}
	}

	static Collection<String> names(TrsOnline online) {
		if (online == null || !online.online()) return null;
		Friends friends = online.friends();
		return friends == null ? null : names(friends.snapshot());
	}

	/** Die Namen aus einem Stand der Freundesliste – null, solange er noch nie geladen wurde. */
	public static Collection<String> names(Friends.Snapshot snapshot) {
		FriendsView view = snapshot == null ? null : snapshot.view;
		if (view == null || view.friends == null) return null;
		List<String> out = new ArrayList<String>(view.friends.size());
		for (FriendsView.Friend f : view.friends) {
			if (f != null && f.name != null && !f.name.isEmpty()) out.add(f.name);
		}
		return out;
	}
}
