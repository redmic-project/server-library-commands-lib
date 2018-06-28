package es.redmic.commandslib.exceptions;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public class ItemLockedExceptionTest extends BaseExceptionTest {

	@Test
	public void checkPattern_IsEqualToMessage_WhenNoLocaleSet() throws IOException {

		// @formatter:off

		String field = "id",
				value = "23233";
		
		// @formatter:on

		checkMessage(new ItemLockedException(field, value), ExceptionType.ITEM_LOCKED.toString(),
				Arrays.asList(field, value));
	}
}