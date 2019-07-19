package es.redmic.commandslib.usersettings.service;

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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import es.redmic.commandslib.usersettings.commands.ClearCommand;
import es.redmic.commandslib.usersettings.commands.DeleteSettingsCommand;
import es.redmic.commandslib.usersettings.commands.DeselectCommand;
import es.redmic.commandslib.usersettings.commands.SaveSettingsCommand;
import es.redmic.commandslib.usersettings.commands.SelectCommand;
import es.redmic.commandslib.usersettings.commands.UpdateSettingsCommand;
import es.redmic.commandslib.usersettings.handler.SettingsCommandHandler;
import es.redmic.usersettingslib.dto.PersistenceDTO;
import es.redmic.usersettingslib.dto.SelectionDTO;
import es.redmic.usersettingslib.dto.SettingsDTO;

@Service
@ConditionalOnProperty(name = "redmic.user-settings.enabled", havingValue = "true")
public class SettingsService {

	SettingsCommandHandler commandHandler;

	public SettingsService(SettingsCommandHandler commandHandler) {
		this.commandHandler = commandHandler;
	}

	public SettingsDTO select(SelectionDTO selection) {
		return commandHandler.select(new SelectCommand(selection));
	}

	public SettingsDTO select(String id, SelectionDTO selection) {
		selection.setId(id);
		return commandHandler.select(new SelectCommand(selection));
	}

	public SettingsDTO deselect(String id, SelectionDTO selection) {
		selection.setId(id);
		return commandHandler.deselect(new DeselectCommand(selection));
	}

	public SettingsDTO clear(String id, SelectionDTO selection) {
		selection.setId(id);
		return commandHandler.clear(new ClearCommand(selection));
	}

	public SettingsDTO create(PersistenceDTO persistence) {
		return commandHandler.save(new SaveSettingsCommand(persistence));
	}

	public SettingsDTO update(String id, PersistenceDTO persistence) {
		persistence.setId(id);
		return commandHandler.update(new UpdateSettingsCommand(persistence));
	}

	public SettingsDTO delete(String id) {
		return commandHandler.delete(new DeleteSettingsCommand(id));
	}
}
