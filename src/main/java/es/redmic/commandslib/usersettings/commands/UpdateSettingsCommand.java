package es.redmic.commandslib.usersettings.commands;

import org.joda.time.DateTime;

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

import es.redmic.commandslib.commands.Command;
import es.redmic.usersettingslib.dto.PersistenceDTO;

public class UpdateSettingsCommand extends Command {

	private PersistenceDTO persistence;

	public UpdateSettingsCommand(PersistenceDTO persistence) {

		persistence.setUpdated(DateTime.now());

		this.persistence = persistence;
	}

	public PersistenceDTO getPersistence() {
		return persistence;
	}

	public void setPersistence(PersistenceDTO persistence) {
		this.persistence = persistence;
	}
}
