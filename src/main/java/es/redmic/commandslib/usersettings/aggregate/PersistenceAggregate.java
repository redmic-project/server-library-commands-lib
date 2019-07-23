package es.redmic.commandslib.usersettings.aggregate;

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

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.commandslib.aggregate.Aggregate;
import es.redmic.commandslib.usersettings.commands.DeleteSettingsCommand;
import es.redmic.commandslib.usersettings.commands.SaveSettingsCommand;
import es.redmic.commandslib.usersettings.commands.UpdateSettingsCommand;
import es.redmic.commandslib.usersettings.statestore.SettingsStateStore;
import es.redmic.usersettingslib.dto.SettingsDTO;
import es.redmic.usersettingslib.events.SettingsEventTypes;
import es.redmic.usersettingslib.events.common.SettingsCancelledEvent;
import es.redmic.usersettingslib.events.common.SettingsEvent;
import es.redmic.usersettingslib.events.delete.CheckDeleteSettingsEvent;
import es.redmic.usersettingslib.events.delete.SettingsDeletedEvent;
import es.redmic.usersettingslib.events.save.PartialSaveSettingsEvent;

public class PersistenceAggregate extends Aggregate {

	private SettingsDTO settings;

	private SettingsStateStore settingsStateStore;

	public PersistenceAggregate(SettingsStateStore settingsStateStore) {
		this.settingsStateStore = settingsStateStore;
	}

	public PartialSaveSettingsEvent process(SaveSettingsCommand cmd) {

		assert settingsStateStore != null;

		String id = cmd.getPersistence().getId();

		if (exist(id)) {
			Event state = getStateFromHistory(id);
			loadFromHistory(state);
			checkState(id, state.getType());
		}

		this.setAggregateId(id);

		PartialSaveSettingsEvent evt = new PartialSaveSettingsEvent(cmd.getPersistence());
		evt.setAggregateId(id);
		evt.setVersion(1);
		return evt;
	}

	public PartialSaveSettingsEvent process(UpdateSettingsCommand cmd) {

		assert settingsStateStore != null;

		String id = cmd.getPersistence().getId();

		Event state = getStateFromHistory(id);

		loadFromHistory(state);

		checkState(id, state.getType());

		PartialSaveSettingsEvent evt = new PartialSaveSettingsEvent(cmd.getPersistence());
		evt.setAggregateId(id);
		evt.setVersion(getVersion() + 1);
		return evt;
	}

	public CheckDeleteSettingsEvent process(DeleteSettingsCommand cmd) {

		assert settingsStateStore != null;

		String id = cmd.getSettingsId();

		Event state = getStateFromHistory(id);

		loadFromHistory(state);

		checkState(id, state.getType());

		CheckDeleteSettingsEvent evt = new CheckDeleteSettingsEvent();
		evt.setAggregateId(id);
		evt.setVersion(getVersion() + 1);
		return evt;
	}

	public SettingsDTO getSettings() {
		return settings;
	}

	@Override
	protected boolean isLocked(String eventType) {

		return SettingsEventTypes.isLocked(eventType);
	}

	@Override
	protected Event getItemFromStateStore(String id) {

		return settingsStateStore.get(id);
	}

	@Override
	public void loadFromHistory(Event event) {
		logger.debug("Cargando último estado de Category ", event.getAggregateId());

		check(event);

		String eventType = event.getType();

		switch (eventType) {
		case "SAVED":
			logger.debug("Settings guardada");
			apply((SettingsEvent) event);
			break;
		case "DELETED":
			logger.debug("Settings borrada");
			apply((SettingsDeletedEvent) event);
			break;
		// CANCELLED
		case "SAVE_CANCELLED":
		case "DELETE_CANCELLED":
			logger.debug("Compensación por guardado/borrado fallido");
			apply((SettingsCancelledEvent) event);
			break;
		default:
			logger.debug("Evento no manejado ", event.getType());
		}
	}

	public void apply(SettingsEvent evt) {
		super.apply(evt);
		this.settings = evt.getSettings();
	}

	public void apply(SettingsDeletedEvent evt) {
		super.apply(evt);
		this.deleted = true;
	}

	public void apply(SettingsCancelledEvent evt) {
		super.apply(evt);
		this.settings = evt.getSettings();
	}

	@Override
	protected void reset() {
		this.settings = null;
		super.reset();
	}
}
