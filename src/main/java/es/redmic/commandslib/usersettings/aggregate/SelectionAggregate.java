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
import es.redmic.commandslib.usersettings.commands.ClearCommand;
import es.redmic.commandslib.usersettings.commands.DeselectCommand;
import es.redmic.commandslib.usersettings.commands.SelectCommand;
import es.redmic.commandslib.usersettings.statestore.SettingsStateStore;
import es.redmic.usersettingslib.dto.SelectionDTO;
import es.redmic.usersettingslib.events.SettingsEventTypes;
import es.redmic.usersettingslib.events.clear.ClearEvent;
import es.redmic.usersettingslib.events.common.SelectionCancelledEvent;
import es.redmic.usersettingslib.events.common.SelectionEvent;
import es.redmic.usersettingslib.events.deselect.DeselectEvent;
import es.redmic.usersettingslib.events.select.SelectEvent;

public class SelectionAggregate extends Aggregate {

	private SelectionDTO selection;

	private SettingsStateStore settingsStateStore;

	public SelectionAggregate(SettingsStateStore settingsStateStore) {
		this.settingsStateStore = settingsStateStore;
	}

	public SelectEvent process(SelectCommand cmd) {

		assert settingsStateStore != null;

		String id = cmd.getSelection().getId();

		if (exist(id)) {
			logger.info("Descartando selección " + id + ". Ya está registrado.");
			return null; // Se lanza excepción en el origen no aquí
		}

		this.setAggregateId(id);

		SelectEvent evt = new SelectEvent(cmd.getSelection());
		evt.setAggregateId(id);
		evt.setVersion(1);
		return evt;
	}

	public DeselectEvent process(DeselectCommand cmd) {

		assert settingsStateStore != null;

		String id = cmd.getSelection().getId();

		Event state = getStateFromHistory(id);

		loadFromHistory(state);

		checkState(id, state.getType());

		DeselectEvent evt = new DeselectEvent(cmd.getSelection());
		evt.setAggregateId(id);
		evt.setVersion(getVersion() + 1);
		return evt;
	}

	public ClearEvent process(ClearCommand cmd) {

		assert settingsStateStore != null;

		String id = cmd.getSelection().getId();

		Event state = getStateFromHistory(id);

		loadFromHistory(state);

		checkState(id, state.getType());

		ClearEvent evt = new ClearEvent(cmd.getSelection());
		evt.setAggregateId(id);
		evt.setVersion(getVersion() + 1);
		return evt;
	}

	public SelectionDTO getSelection() {
		return selection;
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
		case "CLEARED":
			logger.debug("Selección modificada");
			apply((SelectionEvent) event);
			break;
		// CANCELLED
		case "SELECT_CANCELLED":
		case "DESELECT_CANCELLED":
		case "CLEAR_CANCELLED":
			logger.debug("Compensación por selección fallida");
			apply((SelectionCancelledEvent) event);
			break;
		default:
			logger.debug("Evento no manejado ", event.getType());
		}
	}

	public void apply(SelectionEvent evt) {
		super.apply(evt);
		selection = evt.getSelection();
	}

	public void apply(SelectionCancelledEvent evt) {
		super.apply(evt);
		selection = evt.getSelection();
	}

	@Override
	protected void reset() {
		this.selection = null;
		super.reset();
	}
}
