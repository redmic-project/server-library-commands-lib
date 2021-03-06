package es.redmic.commandslib.controller;

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

import javax.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import es.redmic.brokerlib.avro.common.CommonDTO;
import es.redmic.commandslib.service.CommandGeoServiceItfc;
import es.redmic.exception.data.ItemAlreadyExistException;
import es.redmic.exception.databinding.DTONotValidException;
import es.redmic.models.es.common.dto.BodyItemDTO;
import es.redmic.models.es.common.dto.SuperDTO;

public abstract class CommandGeoController<TDTO extends CommonDTO> extends CommandBaseController<TDTO> {

	CommandGeoServiceItfc<TDTO> service;

	public CommandGeoController(CommandGeoServiceItfc<TDTO> service) {

		super();
		this.service = service;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO add(@PathVariable(name = "activityId", required = false) String activityId,
			@Valid @RequestBody TDTO dto, BindingResult errorDto) {

		if (errorDto.hasErrors())
			throw new DTONotValidException(errorDto);

		TDTO result = service.create(dto, activityId);

		if (result == null)
			throw new ItemAlreadyExistException();

		return new BodyItemDTO<TDTO>(result);
	}

	@PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO update(@PathVariable(name = "activityId", required = false) String activityId,
			@Valid @RequestBody TDTO dto, BindingResult errorDto, @PathVariable("id") String id) {

		if (errorDto.hasErrors())
			throw new DTONotValidException(errorDto);
		dto.setId(id);
		return new BodyItemDTO<TDTO>(service.update(id, dto, activityId));
	}

	@DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public SuperDTO delete(@PathVariable(name = "activityId", required = false) String activityId,
			@PathVariable("id") String id) {
		service.delete(id, activityId);
		return new SuperDTO(true);
	}
}
