package es.redmic.commandslib.controller;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;

import javax.validation.Valid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import es.redmic.brokerlib.avro.common.CommonDTO;
import es.redmic.commandslib.service.CommandServiceItfc;
import es.redmic.commandslib.service.JsonSchemaService;
import es.redmic.exception.data.ItemAlreadyExistException;
import es.redmic.exception.databinding.DTONotValidException;
import es.redmic.models.es.common.dto.BodyItemDTO;
import es.redmic.models.es.common.dto.SuperDTO;

public abstract class CommandController<TDTO extends CommonDTO> extends CommandBaseController<TDTO> {

	protected static Logger logger = LogManager.getLogger();

	protected Class<TDTO> typeOfTDTO;

	@Autowired
	JsonSchemaService jsonSchemaService;

	CommandServiceItfc<TDTO> service;

	@SuppressWarnings("unchecked")
	public CommandController(CommandServiceItfc<TDTO> service) {
		this.service = service;

		this.typeOfTDTO = (Class<TDTO>) (((ParameterizedType) getClass().getGenericSuperclass())
				.getActualTypeArguments()[0]);
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO add(@Valid @RequestBody TDTO dto, BindingResult errorDto) {

		if (errorDto.hasErrors())
			throw new DTONotValidException(errorDto);

		TDTO result = service.create(dto);

		if (result == null)
			throw new ItemAlreadyExistException();

		return new BodyItemDTO<TDTO>(result);
	}

	@PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO update(@Valid @RequestBody TDTO dto, BindingResult errorDto, @PathVariable("id") String id) {

		if (errorDto.hasErrors())
			throw new DTONotValidException(errorDto);
		dto.setId(id);
		return new BodyItemDTO<TDTO>(service.update(id, dto));
	}

	@DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO delete(@PathVariable("id") String id) {
		service.delete(id);
		return new SuperDTO(true);
	}

	@Override
	@GetMapping(value = "${controller.mapping.EDIT_SCHEMA}")
	@ResponseBody
	public HashMap<String, Object> getJsonSchema() {

		return jsonSchemaService.getJsonSchema(typeOfTDTO.getName());
	}
}
