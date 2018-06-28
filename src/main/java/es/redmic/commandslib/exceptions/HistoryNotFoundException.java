package es.redmic.commandslib.exceptions;

import java.util.Arrays;

import es.redmic.exception.common.BadRequestException;

public class HistoryNotFoundException extends BadRequestException {

	private static final long serialVersionUID = 1L;

	public HistoryNotFoundException(String cmd, String id) {

		super(ExceptionType.HISTORY_NOT_FOUND);
		setFieldErrors(Arrays.asList(cmd, id));
	}
}