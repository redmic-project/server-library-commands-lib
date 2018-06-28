package es.redmic.commandslib.exceptions;

import java.util.Arrays;

import es.redmic.exception.common.BadRequestException;

public class ItemLockedException extends BadRequestException {

	private static final long serialVersionUID = 1L;

	public ItemLockedException(String field, String value) {

		super(ExceptionType.ITEM_LOCKED);
		setFieldErrors(Arrays.asList(field, value));
	}
}