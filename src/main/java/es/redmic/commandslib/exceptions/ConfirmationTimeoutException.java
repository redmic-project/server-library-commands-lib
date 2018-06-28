package es.redmic.commandslib.exceptions;

import es.redmic.exception.common.RequestTimeoutException;

public class ConfirmationTimeoutException extends RequestTimeoutException {

	private static final long serialVersionUID = 1L;

	public ConfirmationTimeoutException() {

		super(ExceptionType.CONFIRMATION_TIMEOUT);
	}
}