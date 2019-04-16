package es.redmic.commandslib.exceptions;

/*-
 * #%L
 * commands-lib
 * %%
 * Copyright (C) 2019 REDMIC Project / Server
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
