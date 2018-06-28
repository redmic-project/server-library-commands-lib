package es.redmic.commandslib.jsonschema;

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

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;

import es.redmic.commandslib.config.GenerateJsonSchemaScanBean;

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
		objectMapper.registerModule(new JtsModule());

		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
	}

	@Test
	public void simpleJsonSchemaGenerate() throws JSONException, IOException {

		String result = jsonSchema.generateJsonSchema(TestDTO.class),
				expected = IOUtils.toString(getClass().getResource(jsonschemaPath).openStream());
		JSONAssert.assertEquals(result, expected, true);
	}
}