package es.redmic.commandslib.usersettings.controller;

import javax.annotation.PostConstruct;

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

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import es.redmic.commandslib.usersettings.service.SettingsService;
import es.redmic.exception.data.ItemAlreadyExistException;
import es.redmic.exception.databinding.DTONotValidException;
import es.redmic.models.es.common.dto.BodyItemDTO;
import es.redmic.models.es.common.dto.SuperDTO;
import es.redmic.usersettingslib.dto.PersistenceDTO;
import es.redmic.usersettingslib.dto.SelectionDTO;
import es.redmic.usersettingslib.dto.SettingsDTO;

@Controller
@ConditionalOnProperty(name = "redmic.user-settings.enabled", havingValue = "true")
@RequestMapping(value = "${controller.mapping.SETTINGS}")
public class SettingsController {

	private SettingsService service;

	@Value("${spring.mvc.servlet.path}")
	String microServicePath;

	@Value("${controller.mapping.SETTINGS}")
	String controllerPath;

	String serviceName;

	public SettingsController(SettingsService service) {
		super();
		this.service = service;
	}

	@PostConstruct
	public void postConstructSettingsController() {
		serviceName = microServicePath + controllerPath;
	}

	@PostMapping(value = "/select", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO select(@Valid @RequestBody SelectionDTO dto, BindingResult errorDto, HttpServletRequest request) {

		if (errorDto.hasErrors())
			throw new DTONotValidException(errorDto);

		dto.setService(serviceName);

		SettingsDTO result = service.select(dto);

		if (result == null)
			throw new ItemAlreadyExistException();

		return new BodyItemDTO<SettingsDTO>(result);
	}

	@PutMapping(value = "/select/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO select(@Valid @RequestBody SelectionDTO dto, BindingResult errorDto, @PathVariable("id") String id,
			HttpServletRequest request) {

		if (errorDto.hasErrors())
			throw new DTONotValidException(errorDto);

		dto.setService(serviceName);
		dto.setId(id);

		return new BodyItemDTO<SettingsDTO>(service.select(id, dto));
	}

	@PutMapping(value = "/deselect/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO update(@Valid @RequestBody SelectionDTO dto, BindingResult errorDto, @PathVariable("id") String id,
			HttpServletRequest request) {

		if (errorDto.hasErrors())
			throw new DTONotValidException(errorDto);

		dto.setService(serviceName);
		dto.setId(id);

		return new BodyItemDTO<SettingsDTO>(service.deselect(id, dto));
	}

	@PutMapping(value = "/clearselection/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO clear(@Valid @RequestBody SelectionDTO dto, BindingResult errorDto, @PathVariable("id") String id,
			HttpServletRequest request) {

		if (errorDto.hasErrors())
			throw new DTONotValidException(errorDto);

		dto.setService(serviceName);
		dto.setId(id);

		return new BodyItemDTO<SettingsDTO>(service.clear(id, dto));
	}

	@PutMapping(value = "/clone/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO clone(@PathVariable("id") String id) {

		return new BodyItemDTO<SettingsDTO>(service.clone(id, serviceName));
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO save(@Valid @RequestBody PersistenceDTO dto, BindingResult errorDto, HttpServletRequest request) {

		if (errorDto.hasErrors())
			throw new DTONotValidException(errorDto);

		dto.setService(serviceName);

		SettingsDTO result = service.create(dto);

		if (result == null)
			throw new ItemAlreadyExistException();

		return new BodyItemDTO<SettingsDTO>(result);
	}

	@PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO update(@Valid @RequestBody PersistenceDTO dto, BindingResult errorDto,
			@PathVariable("id") String id, HttpServletRequest request) {

		if (errorDto.hasErrors())
			throw new DTONotValidException(errorDto);

		dto.setService(serviceName);
		dto.setId(id);

		return new BodyItemDTO<SettingsDTO>(service.update(id, dto));
	}

	@DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO delete(@PathVariable("id") String id) {
		service.delete(id);
		return new SuperDTO(true);
	}
}
