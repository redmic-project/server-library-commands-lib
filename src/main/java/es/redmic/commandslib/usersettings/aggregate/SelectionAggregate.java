package es.redmic.commandslib.usersettings.aggregate;

import org.mapstruct.factory.Mappers;

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
import es.redmic.commandslib.exceptions.ItemLockedException;
import es.redmic.commandslib.usersettings.commands.ClearCommand;
import es.redmic.commandslib.usersettings.commands.DeselectCommand;
import es.redmic.commandslib.usersettings.commands.SelectCommand;
import es.redmic.commandslib.usersettings.statestore.SettingsStateStore;
import es.redmic.restlib.config.UserService;
import es.redmic.usersettingslib.dto.SettingsDTO;
import es.redmic.usersettingslib.events.SettingsEventTypes;
import es.redmic.usersettingslib.events.clearselection.PartialClearSelectionEvent;
import es.redmic.usersettingslib.events.common.SelectionEvent;
import es.redmic.usersettingslib.events.common.SettingsCancelledEvent;
import es.redmic.usersettingslib.events.common.SettingsEvent;
import es.redmic.usersettingslib.events.deselect.PartialDeselectEvent;
import es.redmic.usersettingslib.events.select.PartialSelectEvent;
import es.redmic.usersettingslib.mapper.SettingsMapper;

public class SelectionAggregate extends Aggregate {

	private SettingsDTO settings;

	private SettingsStateStore settingsStateStore;

	private UserService userService;

	public SelectionAggregate(SettingsStateStore settingsStateStore, UserService userService) {
		this.settingsStateStore = settingsStateStore;
		this.userService = userService;
	}

	public PartialSelectEvent process(SelectCommand cmd) {

		assert settingsStateStore != null;

		String userId = userService.getUserId();

		String id = cmd.getSelection().getId();

		String historicalEventUserId = null;

		if (exist(id)) {
			Event state = getStateFromHistory(id);
			loadFromHistory(state);
			checkState(id, state.getType());
			historicalEventUserId = state.getUserId();
		}

		authorshipCheck(userId, historicalEventUserId);

		this.setAggregateId(id);

		PartialSelectEvent evt = new PartialSelectEvent(cmd.getSelection());
		evt.setAggregateId(id);
		evt.setVersion(1);
		evt.setUserId(userId);

		return evt;
	}

	public PartialDeselectEvent process(DeselectCommand cmd) {

		assert settingsStateStore != null;

		String userId = userService.getUserId();

		String id = cmd.getSelection().getId();

		Event state = getStateFromHistory(id);

		loadFromHistory(state);

		checkState(id, state.getType());

		authorshipCheck(userId, state.getUserId());

		PartialDeselectEvent evt = new PartialDeselectEvent(cmd.getSelection());
		evt.setAggregateId(id);
		evt.setVersion(getVersion() + 1);
		evt.setUserId(userId);
		return evt;
	}

	public PartialClearSelectionEvent process(ClearCommand cmd) {

		assert settingsStateStore != null;

		String userId = userService.getUserId();

		String id = cmd.getSelection().getId();

		Event state = getStateFromHistory(id);

		loadFromHistory(state);

		checkState(id, state.getType());

		authorshipCheck(userId, state.getUserId());

		PartialClearSelectionEvent evt = new PartialClearSelectionEvent(cmd.getSelection());
		evt.setAggregateId(id);
		evt.setVersion(getVersion() + 1);
		evt.setUserId(userId);
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
		case "SELECTED":
		case "DESELECTED":
		case "SELECTION_CLEARED":
			logger.debug("Selección modificada");
			apply((SettingsEvent) event);
			break;
		// CANCELLED
		case "SELECT_CANCELLED":
		case "DESELECT_CANCELLED":
		case "CLEAR_SELECTION_CANCELLED":
			logger.debug("Compensación por selección fallida");
			apply((SettingsCancelledEvent) event);
			break;
		default:
			logger.error("Item bloqueado por un evento de tipo: " + eventType);
			throw new ItemLockedException("id", event.getAggregateId());
		}
	}

	public void apply(SelectionEvent evt) {
		super.apply(evt);
		settings = Mappers.getMapper(SettingsMapper.class).map(evt.getSelection());
	}

	public void apply(SettingsEvent evt) {
		super.apply(evt);
		settings = evt.getSettings();
	}

	public void apply(SettingsCancelledEvent evt) {
		super.apply(evt);
		settings = evt.getSettings();
	}

	@Override
	protected void reset() {
		this.settings = null;
		super.reset();
	}
}
