package es.redmic.commandslib.service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class JsonSchemaService {

	public JsonSchemaService() {
	}

	@Value("${property.path.media_storage.JSONSCHEMA}")
	private String jsonschemaPath;

	@Autowired
	ObjectMapper jacksonMapper;

	public HashMap<String, Object> getJsonSchema(String name) {

		File jsonSchemaFile = new File(jsonschemaPath + "/" + name + ".json");

		TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
		};
		HashMap<String, Object> jsonMap = new HashMap<>();
		try {
			jsonMap = jacksonMapper.readValue(jsonSchemaFile, typeRef);
		} catch (IOException e) {
			return jsonMap;
		}

		return jsonMap;
	}
}
