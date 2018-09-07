package es.redmic.commandslib.controller;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import es.redmic.brokerlib.avro.common.CommonDTO;
import es.redmic.commandslib.service.JsonSchemaService;

public abstract class CommandBaseController<TDTO extends CommonDTO> {

	protected static Logger logger = LogManager.getLogger();

	protected Class<TDTO> typeOfTDTO;

	@Autowired
	JsonSchemaService jsonSchemaService;

	@SuppressWarnings("unchecked")
	public CommandBaseController() {

		this.typeOfTDTO = (Class<TDTO>) (((ParameterizedType) getClass().getGenericSuperclass())
				.getActualTypeArguments()[0]);
	}

	@GetMapping(value = "${controller.mapping.EDIT_SCHEMA}")
	@ResponseBody
	public HashMap<String, Object> getJsonSchema() {

		return jsonSchemaService.getJsonSchema(typeOfTDTO.getName());
	}
}
