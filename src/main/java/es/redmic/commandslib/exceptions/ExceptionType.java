package es.redmic.commandslib.exceptions;

import es.redmic.exception.common.ExceptionTypeItfc;

public enum ExceptionType implements ExceptionTypeItfc {

	// @formatter:off
	
	// EventSource
	HISTORY_NOT_FOUND(Constants.HISTORY_NOT_FOUND),
	CONFIRMATION_TIMEOUT(Constants.CONFIRMATION_TIMEOUT),
	ITEM_LOCKED(Constants.ITEM_LOCKED),
	ITEM_REFERENCED(Constants.ITEM_REFERENCED);
	
	// @formatter:on

	final String type;

	ExceptionType(String type) {
		this.type = type;
	}

	public static ExceptionType fromString(String text) {
		if (text != null) {
			for (ExceptionType b : ExceptionType.values()) {
				if (text.equalsIgnoreCase(b.type)) {
					return b;
				}
			}
		}
		throw new IllegalArgumentException(text + " has no corresponding value");
	}

	@Override
	public String toString() {
		return type;
	}

	private static class Constants {

		// @formatter:off
		public static final String HISTORY_NOT_FOUND = "HistoryNotFound",
				CONFIRMATION_TIMEOUT = "ConfirmationTimeout",
				ITEM_LOCKED = "ItemLocked",
				ITEM_REFERENCED = "ItemReferenced";
		// @formatter:on
	}

}
