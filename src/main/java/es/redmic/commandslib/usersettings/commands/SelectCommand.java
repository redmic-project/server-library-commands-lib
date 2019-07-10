package es.redmic.commandslib.usersettings.commands;

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

import java.util.UUID;

import es.redmic.commandslib.commands.Command;
import es.redmic.usersettingslib.dto.SelectionDTO;
import es.redmic.usersettingslib.utils.SettingsUtil;

public class SelectCommand extends Command {

	private SelectionDTO selection;

	public SelectCommand(SelectionDTO selection) {

		if (selection.getId() == null) {
			// Crea id único para settings cuando se trata de una nueva
			selection.setId(SettingsUtil.generateId(UUID.randomUUID().toString()));
		}
		this.selection = selection;
	}

	public SelectionDTO getSelection() {
		return selection;
	}

	public void setSelection(SelectionDTO selection) {
		this.selection = selection;
	}
}
