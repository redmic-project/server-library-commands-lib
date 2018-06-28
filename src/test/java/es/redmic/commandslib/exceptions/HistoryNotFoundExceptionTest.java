package es.redmic.commandslib.exceptions;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public class HistoryNotFoundExceptionTest extends BaseExceptionTest {

	@Test
	public void checkPattern_IsEqualToMessage_WhenNoLocaleSet() throws IOException {

		// @formatter:off

		String cmd = "update",
				id = "23233";
		
		// @formatter:on

		checkMessage(new HistoryNotFoundException(cmd, id), ExceptionType.HISTORY_NOT_FOUND.toString(),
				Arrays.asList(cmd, id));
	}
}