package es.redmic.commandslib.exceptions;

import java.io.IOException;

import org.junit.Test;

public class ConfirmationTimeoutExceptionTest extends BaseExceptionTest {

	@Test
	public void checkPattern_IsEqualToMessage_WhenNoLocaleSet() throws IOException {

		checkMessage(new ConfirmationTimeoutException(), ExceptionType.CONFIRMATION_TIMEOUT.toString(), null);
	}
}