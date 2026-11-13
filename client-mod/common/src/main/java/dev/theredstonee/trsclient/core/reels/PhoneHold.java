package dev.theredstonee.trsclient.core.reels;

import dev.theredstonee.trsclient.core.module.ChoiceSetting;

/** Wie das Handy gehalten wird (MC-Reels): quer mit beiden Händen oder hochkant. */
public enum PhoneHold implements ChoiceSetting.Option {
	LANDSCAPE("Landscape"),
	PORTRAIT("Portrait");

	private final String label;

	PhoneHold(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}

	/** Die andere Haltung (Taste „Handy drehen“). */
	public PhoneHold other() {
		return this == LANDSCAPE ? PORTRAIT : LANDSCAPE;
	}
}
