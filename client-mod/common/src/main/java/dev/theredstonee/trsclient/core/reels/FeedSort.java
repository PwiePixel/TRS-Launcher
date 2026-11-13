package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.module.ChoiceSetting;

/** Reihenfolge des Feeds (MC-Reels): neueste zuerst oder die mit den meisten Likes zuerst. */
public enum FeedSort implements ChoiceSetting.Option {
	NEWEST("New"),
	POPULAR("Popular");

	private final String label;

	FeedSort(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
