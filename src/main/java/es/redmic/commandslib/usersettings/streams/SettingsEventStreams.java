package es.redmic.commandslib.usersettings.streams;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;

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

import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.joda.time.DateTime;
import org.mapstruct.factory.Mappers;

import es.redmic.brokerlib.alert.AlertService;
import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.avro.common.EventError;
import es.redmic.brokerlib.avro.common.EventTypes;
import es.redmic.commandslib.streaming.common.BaseStreams;
import es.redmic.commandslib.streaming.common.StreamConfig;
import es.redmic.commandslib.streaming.common.StreamUtils;
import es.redmic.exception.common.ExceptionType;
import es.redmic.usersettingslib.dto.PersistenceDTO;
import es.redmic.usersettingslib.dto.SelectionDTO;
import es.redmic.usersettingslib.dto.SettingsDTO;
import es.redmic.usersettingslib.events.SettingsEventFactory;
import es.redmic.usersettingslib.events.SettingsEventTypes;
import es.redmic.usersettingslib.events.clone.CloneSettingsEvent;
import es.redmic.usersettingslib.events.common.SelectionEvent;
import es.redmic.usersettingslib.events.common.SettingsEvent;
import es.redmic.usersettingslib.events.delete.CheckDeleteSettingsEvent;
import es.redmic.usersettingslib.events.save.PartialSaveSettingsEvent;
import es.redmic.usersettingslib.events.update.UpdateSettingsAccessedDateEvent;
import es.redmic.usersettingslib.mapper.SettingsMapper;

public class SettingsEventStreams extends BaseStreams {

	protected StreamsBuilder builder = new StreamsBuilder();

	protected String snapshotTopicSuffix = "-snapshot";

	protected String snapshotTopic;

	public SettingsEventStreams(StreamConfig config, AlertService alertService) {
		super(config, alertService);
		snapshotTopic = topic + snapshotTopicSuffix;
		init();
	}

	@Override
	protected KafkaStreams processStreams() {

		KStream<String, Event> events = builder.stream(topic);

		KStream<String, Event> snapshotEvents = builder.stream(snapshotTopic);

		// Table filtrado por eventos finales (Siempre el último evento)
		KTable<String, Event> snapshotKTable = snapshotEvents.groupByKey().reduce((aggValue, newValue) -> newValue);

		// Reenvia eventos snapshot al topic correspondiente
		forwardSnapshotEvents(events);

		processPartialSelect(events, snapshotKTable);

		processSelectConfirm(events);

		processSelectFailedStream(events, snapshotKTable);

		processPartialDeselect(events, snapshotKTable);

		processDeselectConfirm(events);

		processDeselectFailedStream(events, snapshotKTable);

		processPartialClearSelection(events, snapshotKTable);

		processClearSelectionConfirm(events);

		processClearSelectionFailedStream(events, snapshotKTable);

		processPartialSaveSettings(events, snapshotKTable);

		processSaveSettingsConfirm(events);

		processSaveSettingsFailedStream(events, snapshotKTable);

		processCheckDeleteSettings(events, snapshotKTable);

		processDeleteFailedStream(events, snapshotKTable);

		processCloneSettings(events, snapshotKTable);

		processUpdateSettingsAccessedDate(events, snapshotKTable);

		return new KafkaStreams(builder.build(),
				StreamUtils.baseStreamsConfig(bootstrapServers, stateStoreDir, serviceId, schemaRegistry));
	}

	private void forwardSnapshotEvents(KStream<String, Event> events) {

		events.filter((id, event) -> (SettingsEventTypes.isSnapshot(event.getType()))).to(snapshotTopic);
	}

	// Select

	private void processPartialSelect(KStream<String, Event> events, KTable<String, Event> snapshotEvents) {

		// Stream filtrado por eventos parciales de selección
		KStream<String, Event> partialSelectEvents = events
				.filter((id, event) -> (SettingsEventTypes.PARTIAL_SELECT.equals(event.getType())));

		// Join por id, mandando a kafka el evento de select
		partialSelectEvents
				.leftJoin(snapshotEvents,
						(partialSelectEvent, snapshotEvent) -> getSelectEvent(partialSelectEvent, snapshotEvent))
				.filter((k, v) -> (v != null)).to(topic);
	}

	private Event getSelectEvent(Event partialSelectEvent, Event snapshotEvent) {

		assert partialSelectEvent.getType().equals(SettingsEventTypes.PARTIAL_SELECT);

		assert (snapshotEvent == null || SettingsEventTypes.isSnapshot(snapshotEvent.getType()));

		SelectionDTO newSelection = ((SelectionEvent) partialSelectEvent).getSelection();

		if (snapshotEvent == null) {
			return SettingsEventFactory.getEvent(partialSelectEvent, SettingsEventTypes.SELECT,
					Mappers.getMapper(SettingsMapper.class).map(newSelection));
		} else {

			SettingsDTO settings = ((SettingsEvent) snapshotEvent).getSettings();

			if (!changeSelectionIsGranted(settings, newSelection)) {

				return SettingsEventFactory.getEvent(partialSelectEvent, SettingsEventTypes.SELECT_FAILED,
						ExceptionType.ES_SELECTION_WORK.toString(), null);
			}

			Set<String> set = new LinkedHashSet<>();

			set.addAll(newSelection.getSelection());
			set.addAll(settings.getSelection());
			settings.getSelection().clear();
			settings.getSelection().addAll(set);

			return SettingsEventFactory.getEvent(partialSelectEvent, SettingsEventTypes.SELECT, settings);
		}
	}

	private void processSelectFailedStream(KStream<String, Event> events, KTable<String, Event> snapshotKTable) {

		// Stream filtrado por eventos de fallo al seleccionar
		KStream<String, Event> failedEvents = events
				.filter((id, event) -> (SettingsEventTypes.SELECT_FAILED.equals(event.getType())));

		// Join por id, mandando a kafka el evento de compensación
		failedEvents.leftJoin(snapshotKTable,
				(failedEvent, snapshotEvent) -> getSelectCancelledEvent(failedEvent, snapshotEvent)).to(topic);
	}

	private Event getSelectCancelledEvent(Event failedEvent, Event snapshotEvent) {

		assert failedEvent.getType().equals(SettingsEventTypes.SELECT_FAILED);

		assert (snapshotEvent == null || SettingsEventTypes.isSnapshot(snapshotEvent.getType()));

		EventError eventError = (EventError) failedEvent;

		SettingsDTO settings = null;

		// Si falló una selección que no existía (equivalente a borrarla)
		if (snapshotEvent != null) {
			settings = ((SettingsEvent) snapshotEvent).getSettings();
		}

		return SettingsEventFactory.getEvent(failedEvent, SettingsEventTypes.SELECT_CANCELLED, settings,
				eventError.getExceptionType(), eventError.getArguments());
	}

	private void processSelectConfirm(KStream<String, Event> events) {

		// Stream filtrado por eventos de confirmación al seleccionar
		KStream<String, Event> selectConfirmedEvents = events
				.filter((id, event) -> (SettingsEventTypes.SELECT_CONFIRMED.equals(event.getType())));

		// Table filtrado por eventos de petición de selección (Siempre el último
		// evento)
		KTable<String, Event> selectRequestEvents = events
				.filter((id, event) -> (SettingsEventTypes.SELECT.equals(event.getType()))).groupByKey()
				.reduce((aggValue, newValue) -> newValue);

		// Join por id, mandando a kafka el evento de éxito
		selectConfirmedEvents
				.join(selectRequestEvents,
						(confirmedEvent, requestEvent) -> getSelectedEvent(confirmedEvent, requestEvent))
				.filter((k, v) -> (v != null)).to(topic);
	}

	private Event getSelectedEvent(Event confirmedEvent, Event requestEvent) {

		assert confirmedEvent.getType().equals(SettingsEventTypes.SELECT_CONFIRMED);

		assert requestEvent.getType().equals(SettingsEventTypes.SELECT);

		if (!isSameSession(confirmedEvent, requestEvent)) {
			return null;
		}

		SettingsDTO settings = ((SettingsEvent) requestEvent).getSettings();

		return SettingsEventFactory.getEvent(confirmedEvent, SettingsEventTypes.SELECTED, settings);
	}

	// Deselect

	private void processPartialDeselect(KStream<String, Event> events, KTable<String, Event> snapshotEvents) {

		// Stream filtrado por eventos parciales de deselección
		KStream<String, Event> partialDeselectEvents = events
				.filter((id, event) -> (SettingsEventTypes.PARTIAL_DESELECT.equals(event.getType())));

		// Join por id, mandando a kafka el evento de select
		partialDeselectEvents
				.join(snapshotEvents,
						(partialDeselectEvent, snapshotEvent) -> getDeselectEvent(partialDeselectEvent, snapshotEvent))
				.filter((k, v) -> (v != null)).to(topic);
	}

	private Event getDeselectEvent(Event partialDeselectEvent, Event snapshotEvent) {

		assert partialDeselectEvent.getType().equals(SettingsEventTypes.PARTIAL_DESELECT);

		assert SettingsEventTypes.isSnapshot(snapshotEvent.getType());

		SelectionDTO newSelection = ((SelectionEvent) partialDeselectEvent).getSelection();

		SettingsDTO settings = ((SettingsEvent) snapshotEvent).getSettings();

		if (!changeSelectionIsGranted(settings, newSelection)) {

			return SettingsEventFactory.getEvent(partialDeselectEvent, SettingsEventTypes.DESELECT_FAILED,
					ExceptionType.ES_SELECTION_WORK.toString(), null);
		}

		settings.getSelection().removeAll(newSelection.getSelection());

		return SettingsEventFactory.getEvent(partialDeselectEvent, SettingsEventTypes.DESELECT, settings);
	}

	private void processDeselectConfirm(KStream<String, Event> events) {

		// Stream filtrado por eventos de confirmación al deseleccionar
		KStream<String, Event> deselectConfirmedEvents = events
				.filter((id, event) -> (SettingsEventTypes.DESELECT_CONFIRMED.equals(event.getType())));

		// Table filtrado por eventos de petición de deselección (Siempre el último
		// evento)
		KTable<String, Event> deselectRequestEvents = events
				.filter((id, event) -> (SettingsEventTypes.DESELECT.equals(event.getType()))).groupByKey()
				.reduce((aggValue, newValue) -> newValue);

		// Join por id, mandando a kafka el evento de éxito
		deselectConfirmedEvents
				.join(deselectRequestEvents,
						(confirmedEvent, requestEvent) -> getDeselectedEvent(confirmedEvent, requestEvent))
				.filter((k, v) -> (v != null)).to(topic);
	}

	private Event getDeselectedEvent(Event confirmedEvent, Event requestEvent) {

		assert confirmedEvent.getType().equals(SettingsEventTypes.DESELECT_CONFIRMED);

		assert requestEvent.getType().equals(SettingsEventTypes.DESELECT);

		if (!isSameSession(confirmedEvent, requestEvent)) {
			return null;
		}

		SettingsDTO settings = ((SettingsEvent) requestEvent).getSettings();

		return SettingsEventFactory.getEvent(confirmedEvent, SettingsEventTypes.DESELECTED, settings);
	}

	private void processDeselectFailedStream(KStream<String, Event> events, KTable<String, Event> snapshotKTable) {

		// Stream filtrado por eventos de fallo al deseleccionar
		KStream<String, Event> failedEvents = events
				.filter((id, event) -> (SettingsEventTypes.DESELECT_FAILED.equals(event.getType())));

		// Join por id, mandando a kafka el evento de compensación
		failedEvents
				.join(snapshotKTable,
						(failedEvent, snapshotEvent) -> getDeselectCancelledEvent(failedEvent, snapshotEvent))
				.to(topic);

	}

	private Event getDeselectCancelledEvent(Event failedEvent, Event snapshotEvent) {

		assert failedEvent.getType().equals(SettingsEventTypes.DESELECT_FAILED);

		assert SettingsEventTypes.isSnapshot(snapshotEvent.getType());

		EventError eventError = (EventError) failedEvent;

		SettingsDTO settings = ((SettingsEvent) snapshotEvent).getSettings();

		return SettingsEventFactory.getEvent(failedEvent, SettingsEventTypes.DESELECT_CANCELLED, settings,
				eventError.getExceptionType(), eventError.getArguments());
	}

	// Clear

	private void processPartialClearSelection(KStream<String, Event> events, KTable<String, Event> snapshotEvents) {

		// Stream filtrado por eventos parciales de clear
		KStream<String, Event> partialClearEvents = events
				.filter((id, event) -> (SettingsEventTypes.PARTIAL_CLEAR_SELECTION.equals(event.getType())));

		// Join por id, mandando a kafka el evento de select
		partialClearEvents
				.join(snapshotEvents,
						(partialClearEvent, snapshotEvent) -> getClearEvent(partialClearEvent, snapshotEvent))
				.filter((k, v) -> (v != null)).to(topic);
	}

	private Event getClearEvent(Event partialClearEvent, Event snapshotEvent) {

		assert partialClearEvent.getType().equals(SettingsEventTypes.PARTIAL_CLEAR_SELECTION);

		assert SettingsEventTypes.isSnapshot(snapshotEvent.getType());

		SelectionDTO newSelection = ((SelectionEvent) partialClearEvent).getSelection();

		SettingsDTO settings = ((SettingsEvent) snapshotEvent).getSettings();

		if (!changeSelectionIsGranted(settings, newSelection)) {
			// TODO: generar nueva excepción. Si es necesario, añadir argumentos
			return SettingsEventFactory.getEvent(partialClearEvent, SettingsEventTypes.CLEAR_SELECTION_FAILED,
					ExceptionType.ES_SELECTION_WORK.toString(), null);
		}

		settings.getSelection().clear();

		return SettingsEventFactory.getEvent(partialClearEvent, SettingsEventTypes.CLEAR_SELECTION, settings);
	}

	private void processClearSelectionConfirm(KStream<String, Event> events) {

		// Stream filtrado por eventos de confirmación al limpiar selección
		KStream<String, Event> clearSelectionConfirmedEvents = events
				.filter((id, event) -> (SettingsEventTypes.CLEAR_SELECTION_CONFIRMED.equals(event.getType())));

		// Table filtrado por eventos de petición de limpiar selección (Siempre el
		// último evento)
		KTable<String, Event> clearSelectionRequestEvents = events
				.filter((id, event) -> (SettingsEventTypes.CLEAR_SELECTION.equals(event.getType()))).groupByKey()
				.reduce((aggValue, newValue) -> newValue);

		// Join por id, mandando a kafka el evento de éxito
		clearSelectionConfirmedEvents
				.join(clearSelectionRequestEvents,
						(confirmedEvent, requestEvent) -> getSelectionClearedEvent(confirmedEvent, requestEvent))
				.filter((k, v) -> (v != null)).to(topic);
	}

	private Event getSelectionClearedEvent(Event confirmedEvent, Event requestEvent) {

		assert confirmedEvent.getType().equals(SettingsEventTypes.CLEAR_SELECTION_CONFIRMED);

		assert requestEvent.getType().equals(SettingsEventTypes.CLEAR_SELECTION);

		if (!isSameSession(confirmedEvent, requestEvent)) {
			return null;
		}

		SettingsDTO settings = ((SettingsEvent) requestEvent).getSettings();

		return SettingsEventFactory.getEvent(confirmedEvent, SettingsEventTypes.SELECTION_CLEARED, settings);
	}

	private void processClearSelectionFailedStream(KStream<String, Event> events,
			KTable<String, Event> snapshotKTable) {

		// Stream filtrado por eventos de fallo al limpiar selección
		KStream<String, Event> failedEvents = events
				.filter((id, event) -> (SettingsEventTypes.CLEAR_SELECTION_FAILED.equals(event.getType())));

		// Join por id, mandando a kafka el evento de compensación
		failedEvents
				.join(snapshotKTable,
						(failedEvent, snapshotEvent) -> getClearSelectionCancelledEvent(failedEvent, snapshotEvent))
				.to(topic);
	}

	private Event getClearSelectionCancelledEvent(Event failedEvent, Event snapshotEvent) {

		assert failedEvent.getType().equals(SettingsEventTypes.CLEAR_SELECTION_FAILED);

		assert SettingsEventTypes.isSnapshot(snapshotEvent.getType());

		EventError eventError = (EventError) failedEvent;

		SettingsDTO settings = ((SettingsEvent) snapshotEvent).getSettings();

		return SettingsEventFactory.getEvent(failedEvent, SettingsEventTypes.CLEAR_SELECTION_CANCELLED, settings,
				eventError.getExceptionType(), eventError.getArguments());
	}

	// Save

	private void processPartialSaveSettings(KStream<String, Event> events, KTable<String, Event> snapshotKTable) {

		KStream<String, Event> partialEvents = events
				.filter((id, event) -> (SettingsEventTypes.PARTIAL_SAVE.equals(event.getType())))
				.selectKey((k, v) -> ((PartialSaveSettingsEvent) v).getPersistence().getSettingsId());

		partialEvents.leftJoin(snapshotKTable,
				(partialEvent, snapshotEvent) -> getSaveSettingsEvent((PartialSaveSettingsEvent) partialEvent,
						(SettingsEvent) snapshotEvent))
				.selectKey((k, v) -> v.getAggregateId()).to(topic);

	}

	private Event getSaveSettingsEvent(PartialSaveSettingsEvent partialEvent, SettingsEvent snapshotEvent) {

		if (snapshotEvent == null) {
			// TODO: generar nueva excepción. Si es necesario, añadir argumentos
			return SettingsEventFactory.getEvent(partialEvent, SettingsEventTypes.SAVE_FAILED,
					ExceptionType.ES_SELECTION_WORK.toString(), null);
		}

		SettingsDTO sourceSettings = snapshotEvent.getSettings();
		PersistenceDTO persistenceInfo = partialEvent.getPersistence();

		sourceSettings.setId(persistenceInfo.getId());
		sourceSettings.setName(persistenceInfo.getName());
		sourceSettings.setShared(persistenceInfo.getShared());

		return SettingsEventFactory.getEvent(partialEvent, SettingsEventTypes.SAVE, sourceSettings);
	}

	private void processSaveSettingsFailedStream(KStream<String, Event> events, KTable<String, Event> snapshotKTable) {

		// Stream filtrado por eventos de fallo al guardar
		KStream<String, Event> failedEvents = events
				.filter((id, event) -> (SettingsEventTypes.SAVE_FAILED.equals(event.getType())));

		// Join por id, mandando a kafka el evento de compensación
		failedEvents.leftJoin(snapshotKTable,
				(failedEvent, snapshotEvent) -> getSaveCancelledEvent(failedEvent, snapshotEvent)).to(topic);

	}

	private Event getSaveCancelledEvent(Event failedEvent, Event snapshotEvent) {

		assert failedEvent.getType().equals(SettingsEventTypes.SAVE_FAILED);

		assert snapshotEvent == null || SettingsEventTypes.isSnapshot(snapshotEvent.getType());

		EventError eventError = (EventError) failedEvent;

		SettingsDTO settings = null;

		if (snapshotEvent != null) {
			settings = ((SettingsEvent) snapshotEvent).getSettings();
		}

		return SettingsEventFactory.getEvent(failedEvent, SettingsEventTypes.SAVE_CANCELLED, settings,
				eventError.getExceptionType(), eventError.getArguments());
	}

	private void processSaveSettingsConfirm(KStream<String, Event> events) {

		// Stream filtrado por eventos de confirmación al guardar
		KStream<String, Event> selectConfirmedEvents = events
				.filter((id, event) -> (SettingsEventTypes.SAVE_CONFIRMED.equals(event.getType())));

		// Table filtrado por eventos de petición de guardar selección (Siempre el
		// último
		// evento)
		KTable<String, Event> saveRequestEvents = events
				.filter((id, event) -> (SettingsEventTypes.SAVE.equals(event.getType()))).groupByKey()
				.reduce((aggValue, newValue) -> newValue);

		// Join por id, mandando a kafka el evento de éxito
		selectConfirmedEvents
				.join(saveRequestEvents,
						(confirmedEvent, requestEvent) -> getSettingsSavedEvent(confirmedEvent, requestEvent))
				.filter((k, v) -> (v != null)).to(topic);

	}

	private Event getSettingsSavedEvent(Event confirmedEvent, Event requestEvent) {

		assert confirmedEvent.getType().equals(SettingsEventTypes.SAVE_CONFIRMED);

		assert requestEvent.getType().equals(SettingsEventTypes.SAVE);

		if (!isSameSession(confirmedEvent, requestEvent)) {
			return null;
		}

		SettingsDTO settings = ((SettingsEvent) requestEvent).getSettings();

		return SettingsEventFactory.getEvent(confirmedEvent, SettingsEventTypes.SAVED, settings);
	}

	// Clone

	private void processCloneSettings(KStream<String, Event> events, KTable<String, Event> snapshotKTable) {

		KStream<String, Event> cloneEvents = events
				.filter((id, event) -> (SettingsEventTypes.CLONE.equals(event.getType())))
				.selectKey((k, v) -> ((CloneSettingsEvent) v).getPersistence().getSettingsId());

		// Envía evento para guardar nuevas settings de trabajo
		cloneEvents.leftJoin(snapshotKTable,
				(cloneEvent, snapshotEvent) -> getSaveSettingsByCloneEvent((CloneSettingsEvent) cloneEvent,
						(SettingsEvent) snapshotEvent))
				.selectKey((k, v) -> v.getAggregateId()).to(topic);
	}

	private Event getSaveSettingsByCloneEvent(CloneSettingsEvent cloneEvent, SettingsEvent snapshotEvent) {

		if (snapshotEvent == null) {
			// TODO: generar nueva excepción. Si es necesario, añadir argumentos
			return SettingsEventFactory.getEvent(cloneEvent, SettingsEventTypes.SAVE_FAILED,
					ExceptionType.ES_SELECTION_WORK.toString(), null);
		}

		SettingsDTO sourceSettings = snapshotEvent.getSettings();
		PersistenceDTO persistenceInfo = cloneEvent.getPersistence();

		sourceSettings.setId(persistenceInfo.getId());
		sourceSettings.setName(null);
		sourceSettings.setShared(false);
		sourceSettings.setUserId(cloneEvent.getUserId());

		return SettingsEventFactory.getEvent(cloneEvent, SettingsEventTypes.SAVE, sourceSettings);
	}

	// UpdateSettingsAccessedDate

	private void processUpdateSettingsAccessedDate(KStream<String, Event> events,
			KTable<String, Event> snapshotKTable) {

		KStream<String, Event> updateAccessedDateEvents = events
				.filter((id, event) -> (SettingsEventTypes.UPDATE_ACCESSED_DATE.equals(event.getType())));

		// Envía evento para actualizar la fecha de acceso de las settings copiada
		updateAccessedDateEvents.leftJoin(snapshotKTable,
				(updateAccessedDateEvent, snapshotEvent) -> getSaveSettingsByUpdateSettingsAccessedDateEvent(
						(UpdateSettingsAccessedDateEvent) updateAccessedDateEvent, (SettingsEvent) snapshotEvent))
				.to(topic);

	}

	private Event getSaveSettingsByUpdateSettingsAccessedDateEvent(
			UpdateSettingsAccessedDateEvent updateAccessedDateEvent, SettingsEvent snapshotEvent) {

		if (snapshotEvent == null) {
			// TODO: generar nueva excepción. Si es necesario, añadir argumentos
			return SettingsEventFactory.getEvent(updateAccessedDateEvent, SettingsEventTypes.SAVE_FAILED,
					ExceptionType.ES_SELECTION_WORK.toString(), null);
		}

		SettingsDTO sourceSettings = snapshotEvent.getSettings();

		sourceSettings.setAccessed(DateTime.now());

		return SettingsEventFactory.getEvent(updateAccessedDateEvent, SettingsEventTypes.SAVE, sourceSettings);
	}

	// Delete

	private void processCheckDeleteSettings(KStream<String, Event> events, KTable<String, Event> snapshotKTable) {

		KStream<String, Event> checkDeleteEvents = events
				.filter((id, event) -> (SettingsEventTypes.CHECK_DELETE.equals(event.getType())));

		// Join por id, mandando a kafka el evento de compensación
		checkDeleteEvents
				.join(snapshotKTable,
						(checkDeleteEvent, snapshotEvent) -> getCheckResultEvent(checkDeleteEvent, snapshotEvent))
				.to(topic);

	}

	private Event getCheckResultEvent(Event checkDeleteEvent, Event snapshotEvent) {

		SettingsDTO settings = ((SettingsEvent) snapshotEvent).getSettings();

		CheckDeleteSettingsEvent evt = (CheckDeleteSettingsEvent) checkDeleteEvent;

		if (!settings.getShared() && evt.getUserId().equals(settings.getUserId())) {
			return SettingsEventFactory.getEvent(evt, SettingsEventTypes.DELETE_CHECKED);
		}

		// TODO: generar nueva excepción. Si es necesario, añadir argumentos
		return SettingsEventFactory.getEvent(evt, SettingsEventTypes.CHECK_DELETE_FAILED,
				ExceptionType.ES_SELECTION_WORK.toString(), null);
	}

	private void processDeleteFailedStream(KStream<String, Event> events, KTable<String, Event> snapshotKTable) {

		// Stream filtrado por eventos de fallo al borrar
		KStream<String, Event> failedEvents = events
				.filter((id, event) -> (EventTypes.DELETE_FAILED.equals(event.getType())));

		// Join por id, mandando a kafka el evento de compensación
		failedEvents.join(snapshotKTable,
				(failedEvent, snapshotEvent) -> getDeleteCancelledEvent(failedEvent, snapshotEvent)).to(topic);
	}

	private Event getDeleteCancelledEvent(Event failedEvent, Event snapshotEvent) {

		assert failedEvent.getType().equals(SettingsEventTypes.DELETE_FAILED);

		assert SettingsEventTypes.isSnapshot(snapshotEvent.getType());

		SettingsDTO settings = ((SettingsEvent) snapshotEvent).getSettings();

		EventError eventError = (EventError) failedEvent;

		return SettingsEventFactory.getEvent(failedEvent, SettingsEventTypes.DELETE_CANCELLED, settings,
				eventError.getExceptionType(), eventError.getArguments());
	}

	private boolean changeSelectionIsGranted(SettingsDTO settings, SelectionDTO newSelection) {

		if (!(settings.getService().equals(newSelection.getService())
				&& settings.getUserId().equals(newSelection.getUserId()) && !settings.getShared()
				&& (settings.getName() == null))) {

			logger.error(
					"Imposible modificar la selección de trabajo. No cumple alguna de las restricciones establecidas.");

			return false;
		}
		return true;
	}

	@Override
	protected void postProcessStreams() {
	}
}
