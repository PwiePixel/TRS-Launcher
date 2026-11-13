package dev.theredstonee.trsclient.core.online;

import dev.theredstonee.trsclient.core.reels.ReelsFriends;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-Reels liest die Freundesliste nur aus dem Stand, den TRS Online schon geladen hat – hier ohne Netz, mit einem von
 * Hand gebauten Stand (darum liegt der Test in diesem Paket).
 */
class ReelsFriendsSourceTest {
	private static Friends.Snapshot snapshot(FriendsView view) {
		return new Friends.Snapshot(view, null, false, null, null, null, null, false, 0L, null, 0, 0, null, 0);
	}

	@Test
	void namesComeOnlyFromAnAlreadyLoadedList() {
		assertNull(ReelsFriends.names((Friends.Snapshot) null));
		assertNull(ReelsFriends.names(snapshot(null)), "noch nie geladen: unbekannt, nicht leer");

		Collection<String> none = ReelsFriends.names(snapshot(FriendsView.EMPTY));
		assertNotNull(none, "geladen und leer ist bekannt");
		assertTrue(none.isEmpty());

		FriendsView view = new FriendsView(Arrays.asList(
				new FriendsView.Friend("u1", "Steve_Baut", "in-game", "26.2", "fabric", null),
				new FriendsView.Friend("u2", "Bea", null, null, null, null),
				new FriendsView.Friend("u3", null, null, null, null, null),
				new FriendsView.Friend("u4", "", "online", null, null, null)),
				Collections.<FriendsView.User>emptyList(), Collections.<FriendsView.User>emptyList());
		assertEquals(Arrays.asList("Steve_Baut", "Bea"), new ArrayList<String>(ReelsFriends.names(snapshot(view))),
				"online oder nicht – ohne Einträge ohne Namen");
	}

	@Test
	void withoutTrsOnlineTheListIsUnknown() {
		if (TrsOnline.current() == null) assertNull(ReelsFriends.names());
		// Läuft in dieser JVM schon ein TRS Online aus einem anderen Test, darf der Aufruf nur nichts werfen.
		ReelsFriends.names();
	}
}
