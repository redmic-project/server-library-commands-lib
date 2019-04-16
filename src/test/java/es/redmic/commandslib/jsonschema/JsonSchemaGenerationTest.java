package es.redmic.commandslib.jsonschema;

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

import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;

import es.redmic.commandslib.config.GenerateJsonSchemaScanBean;
import es.redmic.jts4jackson.module.JTSModule;

@RunWith(MockitoJUnitRunner.class)
public class JsonSchemaGenerationTest {

	@Spy
	ObjectMapper objectMapper;

	@Mock
	private Environment env;

	@InjectMocks
	GenerateJsonSchemaScanBean jsonSchema;

	private String jsonschemaPath = "/jsonschema/dtoJsonSchema.json";

	@Before
	public void init() throws Exception {

		HashMap<String, Object> properties = new HashMap<>();
		properties.put("controller.mapping.testtype", "/api/vessels/commands/testtype");

		Whitebox.setInternalState(jsonSchema, HashMap.class, properties);
		Whitebox.invokeMethod(jsonSchema, "jsonSchemaGeneratorInit");
		objectMapper.registerModule(new JodaModule());
		objectMapper.registerModule(new JTSModule());

		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
	}

	@Test
	public void simpleJsonSchemaGenerate() throws JSONException, IOException {

		String result = jsonSchema.generateJsonSchema(TestDTO.class),
				expected = IOUtils.toString(getClass().getResource(jsonschemaPath).openStream());
		JSONAssert.assertEquals(result, expected, true);
	}
}
