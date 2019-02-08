package es.redmic.commandslib.config;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import com.kjetland.jackson.jsonSchema.JsonSchemaResources;

import es.redmic.commandslib.controller.CommandBaseController;
import es.redmic.exception.mediastorage.MSFileUploadException;

public class GenerateJsonSchemaScanBean implements ApplicationContextAware {

	@Autowired
	ObjectMapper objectMapper;

	HashMap<String, Object> properties = new HashMap<>();

	@Autowired
	private Environment env;

	JsonSchemaGenerator jsonSchemaGenerator;

	private ApplicationContext applicationContext;

	@Value("${property.path.media_storage.JSONSCHEMA}")
	private String jsonschemaPath;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		jsonSchemaGeneratorInit();
		addAllControllerBeans();
	}

	/**
	 * Recorre todos los beans de tipo Lectura-Escritura para generar los esquemas
	 */
	public void addAllControllerBeans() {
		/*
		 * Genera los esquemas de edición para todos los controladores de tipo
		 * CommandController
		 */
		@SuppressWarnings("rawtypes")
		final Map<String, CommandBaseController> controllers = applicationContext
				.getBeansOfType(CommandBaseController.class);
		for (@SuppressWarnings("rawtypes")
		final CommandBaseController controller : controllers.values()) {

			try {
				Class<?> typeOfTDTO = (Class<?>) ((ParameterizedType) controller.getClass().getGenericSuperclass())
						.getActualTypeArguments()[0];

				generateAndSaveJsonSchema(typeOfTDTO);

			} catch (Exception e) {
				System.err.println("Error al generar el jsonSchema " + controller.getClass());
				e.printStackTrace();
			}
		}
	}

	/**
	 * Genera y guarda el json esquema para el dto del controlador pasado
	 * 
	 * @param typeOfTDTO
	 *            Clase del dto del cual se generará el esquema
	 * @throws JsonProcessingException
	 */
	private void generateAndSaveJsonSchema(Class<?> typeOfTDTO) throws JsonProcessingException {

		String result = generateJsonSchema(typeOfTDTO);
		if (result != null)
			saveJsonSchema(result, typeOfTDTO.getName());
	}

	public String generateJsonSchema(Class<?> typeOfTDTO) throws JsonProcessingException {
		JsonNode jsonSchema = jsonSchemaGenerator.generateJsonSchema(typeOfTDTO);

		return objectMapper.writeValueAsString(jsonSchema);
	}

	private void saveJsonSchema(String jsonSchema, String name) {

		File directory = new File(jsonschemaPath);

		if (!directory.exists()) {
			directory.mkdirs();
		}

		try {

			BufferedOutputStream buffStream = new BufferedOutputStream(
					new FileOutputStream(new File(jsonschemaPath + "/" + name + ".json")));
			buffStream.write(jsonSchema.getBytes());
			buffStream.close();
		} catch (IOException e) {
			throw new MSFileUploadException(e);
		}
	}

	private void jsonSchemaGeneratorInit() {

		jsonSchemaGenerator = new JsonSchemaGenerator(objectMapper, JsonSchemaResources.setResources(getProperties()));
	}

	@SuppressWarnings({ "rawtypes" })
	public Map<String, Object> getProperties() {

		if (properties.isEmpty()) {
			String serverPath = env.getProperty("server.servlet.context-path")
					+ env.getProperty("spring.mvc.servlet.path");
			for (Iterator it = ((AbstractEnvironment) env).getPropertySources().iterator(); it.hasNext();) {
				PropertySource propertySource = (PropertySource) it.next();
				if (propertySource instanceof MapPropertySource) {
					Map<String, Object> envProperties = ((MapPropertySource) propertySource).getSource();
					for (Map.Entry<String, Object> entry : envProperties.entrySet()) {
						if (entry.getKey().toString().contains("controller.mapping"))
							properties.put(entry.getKey(),
									serverPath + applicationContext.getEnvironment().getProperty(entry.getKey()));
					}
				}
			}
		}

		return properties;
	}

}
