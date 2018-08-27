package es.redmic.commandslib.exceptions;

import java.util.Arrays;

import es.redmic.exception.common.BadRequestException;

public class ItemReferencedException extends BadRequestException {

	private static final long serialVersionUID = 1L;

	public ItemReferencedException(String field, String value) {

		super(ExceptionType.ITEM_REFERENCED);
		setFieldErrors(Arrays.asList(field, value));
	}
}