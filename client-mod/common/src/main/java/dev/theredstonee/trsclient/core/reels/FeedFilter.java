package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.module.ChoiceSetting;

/** Welche Posts der Feed zeigt (MC-Reels): alle, die von Freunden oder nur die eigenen. */
public enum FeedFilter implements ChoiceSetting.Option {
	ALL("All"),
	FRIENDS("Friends"),
	MINE("Me");

	private final String label;

	FeedFilter(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
