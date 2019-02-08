package es.redmic.commandslib.exceptions;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public class ItemReferencedExceptionTest extends BaseExceptionTest {

	@Test
	public void checkPattern_IsEqualToMessage_WhenNoLocaleSet() throws IOException {

		// @formatter:off

		String field = "id",
				value = "23233";
		
		// @formatter:on

		checkMessage(new ItemReferencedException(field, value), ExceptionType.ITEM_REFERENCED.toString(),
				Arrays.asList(field, value));
	}
}