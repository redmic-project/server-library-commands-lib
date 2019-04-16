package es.redmic.commandslib.service;

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
