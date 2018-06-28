package es.redmic.commandslib.exceptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.MessageFormat;
import java.util.List;

import es.redmic.exception.common.BaseException;
import es.redmic.exception.common.PatternUtils;

public abstract class BaseExceptionTest {

	private final String resourcePath = "classpath*:i18n/messages_es_ES.properties";

	protected void checkMessage(BaseException ex, String i18nKey, List<String> fields) {

		// @formatter:off
		String message = ex.getMessage(),
			pattern = PatternUtils.getPattern(i18nKey, resourcePath),
			expectedMessage = replaceFields(pattern, fields);
			
		// @formatter:on

		assertEquals(expectedMessage, message);

		if (fields != null && fields.size() > 0) {
			for (String field : fields) {
				assertTrue(message.contains(field));
			}
		}
	}

	private String replaceFields(String pattern, List<String> fields) {

		if (fields == null || fields.size() == 0)
			return pattern;

		Object[] fieldsArray = fields.toArray();
		MessageFormat format = new MessageFormat(pattern);
		return format.format(fieldsArray);
	}
}
