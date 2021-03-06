package es.redmic.commandslib.usersettings.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.powermock.reflect.Whitebox;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import es.redmic.brokerlib.alert.AlertType;
import es.redmic.brokerlib.alert.Message;
import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.avro.common.EventTypes;
import es.redmic.brokerlib.avro.fail.PrepareRollbackEvent;
import es.redmic.brokerlib.avro.fail.RollbackFailedEvent;
import es.redmic.commandslib.exceptions.ConfirmationTimeoutException;
import es.redmic.commandslib.exceptions.ItemLockedException;
import es.redmic.commandslib.usersettings.aggregate.PersistenceAggregate;
import es.redmic.commandslib.usersettings.commands.ClearCommand;
import es.redmic.commandslib.usersettings.commands.CloneSettingsCommand;
import es.redmic.commandslib.usersettings.commands.DeleteSettingsCommand;
import es.redmic.commandslib.usersettings.commands.DeselectCommand;
import es.redmic.commandslib.usersettings.commands.SaveSettingsCommand;
import es.redmic.commandslib.usersettings.commands.SelectCommand;
import es.redmic.commandslib.usersettings.commands.UpdateSettingsCommand;
import es.redmic.commandslib.usersettings.handler.SettingsCommandHandler;
import es.redmic.exception.common.ExceptionType;
import es.redmic.exception.data.DeleteItemException;
import es.redmic.restlib.config.UserService;

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

import es.redmic.testutils.kafka.KafkaBaseIntegrationTest;
import es.redmic.usersettingslib.dto.PersistenceDTO;
import es.redmic.usersettingslib.dto.SelectionDTO;
import es.redmic.usersettingslib.dto.SettingsDTO;
import es.redmic.usersettingslib.events.SettingsEventTypes;
import es.redmic.usersettingslib.events.clearselection.ClearSelectionCancelledEvent;
import es.redmic.usersettingslib.events.clearselection.ClearSelectionConfirmedEvent;
import es.redmic.usersettingslib.events.clearselection.ClearSelectionEvent;
import es.redmic.usersettingslib.events.clearselection.ClearSelectionFailedEvent;
import es.redmic.usersettingslib.events.clearselection.PartialClearSelectionEvent;
import es.redmic.usersettingslib.events.clearselection.SelectionClearedEvent;
import es.redmic.usersettingslib.events.clone.CloneSettingsEvent;
import es.redmic.usersettingslib.events.common.SettingsCancelledEvent;
import es.redmic.usersettingslib.events.common.SettingsEvent;
import es.redmic.usersettingslib.events.delete.CheckDeleteSettingsEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsCancelledEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsCheckFailedEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsCheckedEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsConfirmedEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsFailedEvent;
import es.redmic.usersettingslib.events.delete.SettingsDeletedEvent;
import es.redmic.usersettingslib.events.deselect.DeselectCancelledEvent;
import es.redmic.usersettingslib.events.deselect.DeselectConfirmedEvent;
import es.redmic.usersettingslib.events.deselect.DeselectEvent;
import es.redmic.usersettingslib.events.deselect.DeselectFailedEvent;
import es.redmic.usersettingslib.events.deselect.DeselectedEvent;
import es.redmic.usersettingslib.events.deselect.PartialDeselectEvent;
import es.redmic.usersettingslib.events.fail.SettingsRollbackEvent;
import es.redmic.usersettingslib.events.save.PartialSaveSettingsEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsCancelledEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsConfirmedEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsFailedEvent;
import es.redmic.usersettingslib.events.save.SettingsSavedEvent;
import es.redmic.usersettingslib.events.select.PartialSelectEvent;
import es.redmic.usersettingslib.events.select.SelectCancelledEvent;
import es.redmic.usersettingslib.events.select.SelectConfirmedEvent;
import es.redmic.usersettingslib.events.select.SelectEvent;
import es.redmic.usersettingslib.events.select.SelectFailedEvent;
import es.redmic.usersettingslib.events.select.SelectedEvent;
import es.redmic.usersettingslib.events.update.UpdateSettingsAccessedDateEvent;
import es.redmic.usersettingslib.unit.utils.SettingsDataUtil;

public class SettingsCommandHandlerBase extends KafkaBaseIntegrationTest {

	protected static Logger logger = LogManager.getLogger();

	private static final String code = UUID.randomUUID().toString();

	@Value("${broker.topic.settings}")
	private String settings_topic;

	@Autowired
	private KafkaTemplate<String, Event> kafkaTemplate;

	protected static BlockingQueue<Object> blockingQueue;

	protected static BlockingQueue<Object> blockingQueueForAlerts;

	@Mock
	UserService userService;

	@Autowired
	SettingsCommandHandler settingsCommandHandler;

	@Before
	public void setup() {

		blockingQueue = new LinkedBlockingDeque<>();
		blockingQueueForAlerts = new LinkedBlockingDeque<>();

		Whitebox.setInternalState(settingsCommandHandler, "userService", userService);

		when(userService.getUserId()).thenReturn("13");
	}

	// Select

	// Envía un evento parcial para una nueva selección y debe provocar un evento
	// Select con settings dentro y la nueva selección
	@Test
	public void partialSelectEventInNewSelection_SendSelectEventWithNewSelection_IfReceivesSuccess() throws Exception {

		// Envía select para meterlo en el stream
		PartialSelectEvent partialSelectEvent = SettingsDataUtil.getPartialSelectEvent(code + "1");
		kafkaTemplate.send(settings_topic, partialSelectEvent.getAggregateId(), partialSelectEvent);

		Event select = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(select);
		assertEquals(SettingsEventTypes.SELECT, select.getType());

		SelectionDTO selectionExpected = partialSelectEvent.getSelection();
		SettingsDTO settings = ((SettingsEvent) select).getSettings();

		assertEquals(selectionExpected.getService(), settings.getService());
		assertNotNull(settings.getService());
		assertEquals(selectionExpected.getSelection(), settings.getSelection());
		assertNotNull(settings.getSelection());
		assertFalse(settings.getShared());
		assertNull(settings.getName());
	}

	// Envía un evento select para una nueva selección clonada y debe provocar un
	// evento
	// Select con settings dentro y la nueva selección
	@Test
	public void partialSelectEventInCloneSelection_SendSelectEventWithNewSelection_IfReceivesSuccess()
			throws Exception {

		// Envía select para meterlo en el stream
		SettingsSavedEvent savedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "111");
		savedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, savedEvent.getAggregateId(), savedEvent);
		blockingQueue.poll(20, TimeUnit.SECONDS);

		Thread.sleep(1000);

		PartialSelectEvent partialSelectEvent = SettingsDataUtil.getPartialSelectEvent(code + "111");
		partialSelectEvent.getSelection().getSelection().clear();
		partialSelectEvent.getSelection().getSelection().add("2");
		kafkaTemplate.send(settings_topic, partialSelectEvent.getAggregateId(), partialSelectEvent);

		Event select = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(select);
		assertEquals(SettingsEventTypes.SELECT, select.getType());

		SelectionDTO selectionExpected = partialSelectEvent.getSelection();
		SettingsDTO settings = ((SettingsEvent) select).getSettings();

		assertNotNull(settings.getService());
		assertEquals(selectionExpected.getService(), settings.getService());

		List<String> selection = savedEvent.getSettings().getSelection();
		selection.addAll(selectionExpected.getSelection());

		assertEquals(selection.size(), settings.getSelection().size());
		selection.removeAll(settings.getSelection());
		assertEquals(0, selection.size());
		assertFalse(settings.getShared());
		assertNull(settings.getName());
	}

	// Envía un evento parcial para una selección existente y debe provocar un
	// evento Select con settings dentro y la unión de la nueva selección con la que
	// ya existía
	@Test
	public void partialSelectEvent_SendSelectEventWithUnionOfSelections_IfReceivesSuccess() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "2");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		PartialSelectEvent partialSelectEvent = SettingsDataUtil.getPartialSelectEvent(code + "2");
		partialSelectEvent.getSelection().getSelection().clear();
		partialSelectEvent.getSelection().getSelection().add("2");
		kafkaTemplate.send(settings_topic, partialSelectEvent.getAggregateId(), partialSelectEvent);

		Event select = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(select);
		assertEquals(SettingsEventTypes.SELECT, select.getType());

		SelectionDTO selectionExpected = partialSelectEvent.getSelection();
		SettingsDTO settings = ((SettingsEvent) select).getSettings();

		assertEquals(selectionExpected.getService(), settings.getService());
		assertNotNull(settings.getService());

		List<String> selection = selectedEvent.getSettings().getSelection();
		selection.addAll(selectionExpected.getSelection());

		assertEquals(selection.size(), settings.getSelection().size());
		selection.removeAll(settings.getSelection());
		assertEquals(0, selection.size());
		assertFalse(settings.getShared());
		assertNull(settings.getName());
	}

	// Envía un evento parcial para una selección existente y debe provocar un
	// evento SelectFailed por no cumplir con las restricciones. Se trata de una
	// selección persistente no de trabajo
	@Test
	public void partialSelectEvent_SendSelectFailedEvent_IfChangeSelectionIsNoGranted() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "3");
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		PartialSelectEvent partialSelectEvent = SettingsDataUtil.getPartialSelectEvent(code + "3");
		kafkaTemplate.send(settings_topic, partialSelectEvent.getAggregateId(), partialSelectEvent);

		Event select = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(select);
		assertEquals(SettingsEventTypes.SELECT_FAILED, select.getType());
		assertEquals(ExceptionType.SELECTION_CHANGE_NOT_ALLOWED.toString(),
				((SelectFailedEvent) select).getExceptionType());
	}

	// Envía un evento de confirmación de selección y debe provocar un evento
	// Selected con settings dentro
	@Test
	public void selectConfirmedEvent_SendSelectedEvent_IfReceivesSuccess() throws Exception {

		// Envía select para meterlo en el stream
		SelectEvent selectEvent = SettingsDataUtil.getSelectEvent(code + "4");
		kafkaTemplate.send(settings_topic, selectEvent.getAggregateId(), selectEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		// Envía confirmed y espera un evento selected con la selección original dentro
		SelectConfirmedEvent event = SettingsDataUtil.getSelectConfirmedEvent(code + "4");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);
		Event confirm = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(confirm);
		assertEquals(SettingsEventTypes.SELECTED, confirm.getType());

		JSONAssert.assertEquals(selectEvent.getSettings().toString(),
				((SelectedEvent) confirm).getSettings().toString(), false);
	}

	// Envía un evento de error de selección y debe provocar un evento Cancelled sin
	// el item dentro
	@Test
	public void selectFailedEvent_SendSelectCancelledEventWithoutSettings_IfSelectionNoExist() throws Exception {

		SelectFailedEvent event = SettingsDataUtil.getSelectFailedEvent(code + "5");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		blockingQueue.poll(40, TimeUnit.SECONDS);

		Event cancelled = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);

		assertNotNull(cancelled);
		assertEquals(SettingsEventTypes.SELECT_CANCELLED, cancelled.getType());
	}

	// Envía un evento de error de selección y debe provocar un evento Cancelled con
	// el item dentro porque existía de antes
	@Test
	public void selectFailedEvent_SendSelectCancelledEventWhitSettings_IfSelectionExist() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "6");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(20, TimeUnit.SECONDS);

		Thread.sleep(1000);

		SelectFailedEvent event = SettingsDataUtil.getSelectFailedEvent(code + "6");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);
		blockingQueue.poll(40, TimeUnit.SECONDS);

		Event cancelled = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);

		assertNotNull(cancelled);
		assertEquals(SettingsEventTypes.SELECT_CANCELLED, cancelled.getType());
		assertEquals(selectedEvent.getSettings().getSelection(),
				((SettingsCancelledEvent) cancelled).getSettings().getSelection());
	}

	// Deselect

	// Envía un evento parcial para una selección existente y debe provocar un
	// evento Deselect con settings dentro y la nueva selección
	@Test
	public void partialDeselectEvent_SendDeselectEventWithNewSelection_IfReceivesSuccess() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "7");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		PartialDeselectEvent partialDeselectEvent = SettingsDataUtil.getPartialDeselectEvent(code + "7");
		kafkaTemplate.send(settings_topic, partialDeselectEvent.getAggregateId(), partialDeselectEvent);

		Event select = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(select);
		assertEquals(SettingsEventTypes.DESELECT, select.getType());

		SelectionDTO selectionExpected = partialDeselectEvent.getSelection();
		SettingsDTO settings = ((SettingsEvent) select).getSettings();

		assertEquals(selectionExpected.getService(), settings.getService());
		assertNotNull(settings.getService());

		List<String> selection = selectedEvent.getSettings().getSelection();
		selection.removeAll(selectionExpected.getSelection());

		assertEquals(selection, settings.getSelection());
		assertFalse(settings.getShared());
		assertNull(settings.getName());
	}

	// Envía un evento parcial para una selección existente y debe provocar un
	// evento DeselectFailed por no cumplir con las restricciones. Se trata de una
	// selección persistente no de trabajo
	@Test
	public void partialDeselectEvent_SendDeselectFailedEvent_IfChangeSelectionIsNoGranted() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "8");
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		PartialDeselectEvent partialDeselectEvent = SettingsDataUtil.getPartialDeselectEvent(code + "8");
		kafkaTemplate.send(settings_topic, partialDeselectEvent.getAggregateId(), partialDeselectEvent);

		Event select = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(select);
		assertEquals(SettingsEventTypes.DESELECT_FAILED, select.getType());
		assertEquals(ExceptionType.SELECTION_CHANGE_NOT_ALLOWED.toString(),
				((DeselectFailedEvent) select).getExceptionType());
	}

	// Envía un evento de confirmación de selección y debe provocar un evento
	// Deselected con settings dentro
	@Test
	public void deselectConfirmedEvent_SendDeselectedEvent_IfReceivesSuccess() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "9");
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		// Envía deselect para meterlo en el stream
		DeselectEvent deselectEvent = SettingsDataUtil.getDeselectEvent(code + "9");
		kafkaTemplate.send(settings_topic, deselectEvent.getAggregateId(), deselectEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		// Envía confirmed y espera un evento deselected con la selección original
		// dentro
		DeselectConfirmedEvent event = SettingsDataUtil.getDeselectConfirmedEvent(code + "9");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);
		Event confirm = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(confirm);
		assertEquals(SettingsEventTypes.DESELECTED, confirm.getType());

		JSONAssert.assertEquals(deselectEvent.getSettings().toString(),
				((DeselectedEvent) confirm).getSettings().toString(), false);
	}

	// Envía un evento de error de deselección y debe provocar un evento Cancelled
	// con
	// el item dentro
	@Test
	public void deselectFailedEvent_SendDeselectCancelledEventWhitSettings_IfSelectionExist() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "10");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(20, TimeUnit.SECONDS);

		Thread.sleep(1000);

		DeselectFailedEvent event = SettingsDataUtil.getDeselectFailedEvent(code + "10");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);
		blockingQueue.poll(40, TimeUnit.SECONDS);

		Event cancelled = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);

		assertNotNull(cancelled);
		assertEquals(SettingsEventTypes.DESELECT_CANCELLED, cancelled.getType());
		assertEquals(selectedEvent.getSettings().getSelection(),
				((SettingsCancelledEvent) cancelled).getSettings().getSelection());
	}

	// Clear selection

	// Envía un evento parcial para limpiar una selección existente y debe provocar
	// un evento ClearSelection con settings dentro
	@Test
	public void partialClearSelectionEvent_SendClearSelectionEvent_IfReceivesSuccess() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "11");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		PartialClearSelectionEvent partialClearSelectionEvent = SettingsDataUtil
				.getPartialClearSelectionEvent(code + "11");
		kafkaTemplate.send(settings_topic, partialClearSelectionEvent.getAggregateId(), partialClearSelectionEvent);

		Event select = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(select);
		assertEquals(SettingsEventTypes.CLEAR_SELECTION, select.getType());

		SelectionDTO selectionExpected = partialClearSelectionEvent.getSelection();
		SettingsDTO settings = ((SettingsEvent) select).getSettings();

		assertEquals(selectionExpected.getService(), settings.getService());
		assertNotNull(settings.getService());

		assertEquals(0, settings.getSelection().size());
		assertFalse(settings.getShared());
		assertNull(settings.getName());
	}

	// Envía un evento parcial para una selección existente y debe provocar un
	// evento ClearSelectionFailed por no cumplir con las restricciones. Ya que se
	// trata de una
	// selección persistente no de trabajo
	@Test
	public void partialClearSelectionEvent_SendClearSelectionFailedEvent_IfChangeSelectionIsNoGranted()
			throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "12");
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		PartialClearSelectionEvent partialClearSelectionEvent = SettingsDataUtil
				.getPartialClearSelectionEvent(code + "12");
		kafkaTemplate.send(settings_topic, partialClearSelectionEvent.getAggregateId(), partialClearSelectionEvent);

		Event select = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(select);
		assertEquals(SettingsEventTypes.CLEAR_SELECTION_FAILED, select.getType());
		assertEquals(ExceptionType.SELECTION_CHANGE_NOT_ALLOWED.toString(),
				((ClearSelectionFailedEvent) select).getExceptionType());
	}

	// Envía un evento de confirmación de limpiar selección y debe provocar un
	// evento SelectionCleared con settings dentro
	@Test
	public void clearSelectionConfirmedEvent_SendSelectionClearedEvent_IfReceivesSuccess() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "13");
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		// Envía clear para meterlo en el stream
		ClearSelectionEvent clearSelectionEvent = SettingsDataUtil.getClearEvent(code + "13");
		kafkaTemplate.send(settings_topic, clearSelectionEvent.getAggregateId(), clearSelectionEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		// Envía confirmed y espera un evento cleared con la selección limpia
		ClearSelectionConfirmedEvent event = SettingsDataUtil.getClearConfirmedEvent(code + "13");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);
		Event confirm = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(confirm);
		assertEquals(SettingsEventTypes.SELECTION_CLEARED, confirm.getType());

		JSONAssert.assertEquals(clearSelectionEvent.getSettings().toString(),
				((SelectionClearedEvent) confirm).getSettings().toString(), false);
	}

	// Envía un evento de error de limpiar selección y debe provocar un evento
	// clearSelectionCancelled con el item dentro
	@Test
	public void clearSelectionFailedEvent_SendClearSelectionCancelledEvent_IfReceivesSuccess() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "14");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(20, TimeUnit.SECONDS);

		Thread.sleep(1000);

		ClearSelectionFailedEvent event = SettingsDataUtil.getClearFailedEvent(code + "14");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);
		blockingQueue.poll(40, TimeUnit.SECONDS);

		Event cancelled = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);

		assertNotNull(cancelled);
		assertEquals(SettingsEventTypes.CLEAR_SELECTION_CANCELLED, cancelled.getType());
		assertEquals(selectedEvent.getSettings().getSelection(),
				((SettingsCancelledEvent) cancelled).getSettings().getSelection());
	}

	// Save settings

	// Envía un evento parcial para guardar una selección existente y debe provocar
	// un evento SaveSettings con settings dentro correspondientes a la selección
	// enviada
	@Test
	public void partialSaveSettingsEvent_SendSaveSettingsEvent_IfReceivesSuccess() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "15");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		PartialSaveSettingsEvent partialSaveEvent = SettingsDataUtil.getPartialSaveSettingsEvent(code + "16");

		partialSaveEvent.getPersistence().setSettingsId(selectedEvent.getAggregateId());

		kafkaTemplate.send(settings_topic, partialSaveEvent.getAggregateId(), partialSaveEvent);

		Event save = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(save);
		assertEquals(SettingsEventTypes.SAVE, save.getType());

		PersistenceDTO persistenceInfoExpected = partialSaveEvent.getPersistence();
		SettingsDTO settings = ((SettingsEvent) save).getSettings();

		assertEquals(persistenceInfoExpected.getService(), settings.getService());
		assertNotNull(settings.getService());

		assertEquals(selectedEvent.getSettings().getSelection(), settings.getSelection());
		assertEquals(persistenceInfoExpected.getShared(), settings.getShared());
		assertEquals(persistenceInfoExpected.getName(), settings.getName());
	}

	// Envía un evento parcial para guardar selección existente y debe provocar un
	// evento SaveSettingsFailed por no existir la selección de trabajo a guardar
	@Test
	public void partialSaveSettingsEvent_SendSaveSettingsFailedEvent_IfChangeSelectionNotExists() throws Exception {

		PartialSaveSettingsEvent partialSaveSettingsEvent = SettingsDataUtil.getPartialSaveSettingsEvent(code + "17");
		partialSaveSettingsEvent.getPersistence().setSettingsId("notExists");
		kafkaTemplate.send(settings_topic, partialSaveSettingsEvent.getAggregateId(), partialSaveSettingsEvent);

		Event failed = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(failed);
		assertEquals(SettingsEventTypes.SAVE_FAILED, failed.getType());
		assertEquals(ExceptionType.SETTINGS_TO_SAVE_NOT_FOUND_EXCEPTION.toString(),
				((SaveSettingsFailedEvent) failed).getExceptionType());
	}

	// Envía un evento de confirmación de limpiar guardado y debe provocar un
	// evento saved con settings dentro
	@Test
	public void saveConfirmedEvent_SendSavedEvent_IfReceivesSuccess() throws Exception {

		// Envía save para meterlo en el stream
		SaveSettingsEvent saveSettingsEvent = SettingsDataUtil.getSaveSettingsEvent(code + "18");
		kafkaTemplate.send(settings_topic, saveSettingsEvent.getAggregateId(), saveSettingsEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		// Envía confirmed y espera un evento saved con la selección
		SaveSettingsConfirmedEvent event = SettingsDataUtil.getSaveSettingsConfirmedEvent(code + "18");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);
		Event confirm = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(confirm);
		assertEquals(SettingsEventTypes.SAVED, confirm.getType());

		JSONAssert.assertEquals(saveSettingsEvent.getSettings().toString(),
				((SettingsSavedEvent) confirm).getSettings().toString(), false);
	}

	// Envía un evento de error de guardar selección y debe provocar un evento
	// saveSettingsCancelled con el item dentro
	@Test
	public void saveSettingsFailedEvent_SendSaveSettingsCancelledEvent_IfReceivesSuccess() throws Exception {

		// Envía selected para meterlo en el stream
		SettingsSavedEvent savedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "19");
		kafkaTemplate.send(settings_topic, savedEvent.getAggregateId(), savedEvent);
		blockingQueue.poll(20, TimeUnit.SECONDS);

		Thread.sleep(1000);

		SaveSettingsFailedEvent event = SettingsDataUtil.getSaveSettingsFailedEvent(code + "19");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);
		blockingQueue.poll(40, TimeUnit.SECONDS);

		Event cancelled = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);

		assertNotNull(cancelled);
		assertEquals(SettingsEventTypes.SAVE_CANCELLED, cancelled.getType());
		assertEquals(savedEvent.getSettings().getSelection(),
				((SettingsCancelledEvent) cancelled).getSettings().getSelection());
	}

	// Delete

	// Envía un evento de comprobación de que el elemento puede ser borrado y debe
	// provocar un evento DeleteSettingsCheckedEvent ya que no está compartido
	@Test
	public void checkDeleteSettingsEvent_SendDeleteSettingsCheckedEvent_IfReceivesSuccess()
			throws InterruptedException {

		// Envía saved para meterlo en el stream y lo saca de la cola
		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "20");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(10, TimeUnit.SECONDS);

		Thread.sleep(1000);

		CheckDeleteSettingsEvent event = SettingsDataUtil.getCheckDeleteSettingsEvent(code + "20");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		Event confirm = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);

		assertNotNull(confirm);
		assertEquals(SettingsEventTypes.DELETE_CHECKED, confirm.getType());
		assertEquals(event.getAggregateId(), confirm.getAggregateId());
		assertEquals(event.getUserId(), confirm.getUserId());
		assertEquals(event.getSessionId(), confirm.getSessionId());
		assertEquals(event.getVersion(), confirm.getVersion());
	}

	// Envía un evento de comprobación de que el elemento puede ser borrado y debe
	// provocar un evento CheckDeleteSettingsFailedEvent ya que está compartido
	@Test
	public void checkDeleteSettingsEvent_SendCheckDeleteSettingsFailedEvent_IfSettigsAreShared()
			throws InterruptedException {

		// Envía saved para meterlo en el stream y lo saca de la cola
		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "21");
		settingsSavedEvent.getSettings().setShared(true);
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(10, TimeUnit.SECONDS);

		Thread.sleep(1000);

		CheckDeleteSettingsEvent event = SettingsDataUtil.getCheckDeleteSettingsEvent(code + "21");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		Event failed = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);

		assertNotNull(failed);
		assertEquals(SettingsEventTypes.CHECK_DELETE_FAILED, failed.getType());
		assertEquals(event.getAggregateId(), failed.getAggregateId());
		assertEquals(event.getUserId(), failed.getUserId());
		assertEquals(event.getSessionId(), failed.getSessionId());
		assertEquals(event.getVersion(), failed.getVersion());
		assertEquals(ExceptionType.DELETE_SETTINGS_NOT_ALLOWED.toString(),
				((DeleteSettingsCheckFailedEvent) failed).getExceptionType());
	}

	// Envía un evento de confirmación de borrado y debe provocar un evento Deleted
	@Test
	public void deleteSettingsConfirmedEvent_SendSettingsDeletedEvent_IfReceivesSuccess() throws InterruptedException {

		DeleteSettingsConfirmedEvent event = SettingsDataUtil.getDeleteSettingsConfirmedEvent(code + "22");

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		Event confirm = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(confirm);
		assertEquals(SettingsEventTypes.DELETED, confirm.getType());
		assertEquals(event.getAggregateId(), confirm.getAggregateId());
		assertEquals(event.getUserId(), confirm.getUserId());
		assertEquals(event.getSessionId(), confirm.getSessionId());
		assertEquals(event.getVersion(), confirm.getVersion());
	}

	// Envía un evento de error de borrado y debe provocar un evento Cancelled con
	// el item dentro
	@Test(expected = DeleteItemException.class)
	public void deleteSettingsFailedEvent_SendSettingsCancelledEvent_IfReceivesSuccess() throws Exception {

		// Envía saved para meterlo en el stream y lo saca de la cola
		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "23");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(10, TimeUnit.SECONDS);

		Thread.sleep(1000);

		// Envía failed y espera un evento de cancelled con settings original dentro
		DeleteSettingsFailedEvent event = SettingsDataUtil.getDeleteSettingsFailedEvent(code + "23");

		// Añade completableFeature para que se resuelva al recibir el mensaje.
		CompletableFuture<SettingsDTO> completableFuture = Whitebox.invokeMethod(settingsCommandHandler,
				"getCompletableFeature", event.getSessionId());

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		Event cancelled = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);

		// Obtiene el resultado
		Whitebox.invokeMethod(settingsCommandHandler, "getResult", event.getSessionId(), completableFuture);

		assertNotNull(cancelled);
		assertEquals(SettingsEventTypes.DELETE_CANCELLED, cancelled.getType());
		JSONAssert.assertEquals(settingsSavedEvent.getSettings().toString(),
				((DeleteSettingsCancelledEvent) cancelled).getSettings().toString(), false);
	}

	// Clone

	// Envía un evento de clonado para una selección existente y debe provocar un
	// evento save con settings dentro
	@Test
	public void cloneSettingsEvent_SendSaveEvent_IfReceivesSuccess() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "25");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		CloneSettingsEvent cloneEvent = SettingsDataUtil.getCloneSettingsEvent(code + "26");

		cloneEvent.getPersistence().setSettingsId(selectedEvent.getAggregateId());

		kafkaTemplate.send(settings_topic, cloneEvent.getAggregateId(), cloneEvent);

		Event save = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(save);
		assertEquals(SettingsEventTypes.SAVE, save.getType());

		PersistenceDTO persistenceInfoExpected = cloneEvent.getPersistence();
		SettingsDTO settings = ((SettingsEvent) save).getSettings();

		assertEquals(persistenceInfoExpected.getService(), settings.getService());
		assertNotNull(settings.getService());

		assertEquals(selectedEvent.getSettings().getSelection(), settings.getSelection());
		assertEquals(false, settings.getShared());
		assertEquals(null, settings.getName());
	}

	// Envía un evento para clonar una selección existente y debe provocar un
	// evento SaveSettingsFailed por no existir la selección de trabajo a guardar
	@Test
	public void cloneSettingsEvent_SendSaveSettingsFailedEvent_IfSettingsToCloneNotExists() throws Exception {

		CloneSettingsEvent cloneSettingsEvent = SettingsDataUtil.getCloneSettingsEvent(code + "26b");
		cloneSettingsEvent.getPersistence().setSettingsId("notExists");
		kafkaTemplate.send(settings_topic, cloneSettingsEvent.getAggregateId(), cloneSettingsEvent);

		Event failed = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(failed);
		assertEquals(SettingsEventTypes.SAVE_FAILED, failed.getType());
		assertEquals(ExceptionType.SETTINGS_TO_CLONE_NOT_FOUND_EXCEPTION.toString(),
				((SaveSettingsFailedEvent) failed).getExceptionType());
	}

	// Envía un evento de actualizado de fecha de acceso para una selección
	// existente y debe provocar un
	// evento save con settings originales dentro y fecha de acceso actualizada
	@Test
	public void updateSettingsAccessedDateEvent_SendSaveEvent_IfReceivesSuccess() throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "27");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		UpdateSettingsAccessedDateEvent updateSettingsAccessedDateEvent = SettingsDataUtil
				.getUpdateSettingsAccessedDateEvent(code + "27");

		kafkaTemplate.send(settings_topic, updateSettingsAccessedDateEvent.getAggregateId(),
				updateSettingsAccessedDateEvent);

		Event save = (Event) blockingQueue.poll(120, TimeUnit.SECONDS);

		assertNotNull(save);
		assertEquals(SettingsEventTypes.SAVE, save.getType());

		assertEquals(selectedEvent.getUserId(), save.getUserId());
		assertEquals((Integer) (selectedEvent.getVersion() + 1), save.getVersion());

		SettingsDTO settingsForUpdate = ((SettingsEvent) save).getSettings(),
				sourceSettings = selectedEvent.getSettings();

		assertEquals(sourceSettings.getService(), settingsForUpdate.getService());
		assertEquals(selectedEvent.getSettings().getSelection(), settingsForUpdate.getSelection());
		assertEquals(sourceSettings.getShared(), settingsForUpdate.getShared());
		assertEquals(sourceSettings.getName(), settingsForUpdate.getName());
		assertNotEquals(sourceSettings.getAccessed(), settingsForUpdate.getAccessed());
	}

	// Rollback

	// Create

	// ConfirmationTimeoutException
	@Test
	public void saveSettings_ThrowConfirmationTimeoutExceptionAndSendRollbackEvent_IfConfirmationIsNotReceived()
			throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "30a");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		PersistenceDTO persistenceDTO = SettingsDataUtil.getPersistenceDTO(code + "30");
		persistenceDTO.setSettingsId(selectedEvent.getAggregateId());

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "save", new SaveSettingsCommand(persistenceDTO));
		} catch (Exception e) {
			assertTrue(e instanceof ConfirmationTimeoutException);
		}

		Event evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		while (evt == null || !evt.getType().equals(EventTypes.ROLLBACK))
			evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertEquals(EventTypes.ROLLBACK, evt.getType());
		assertEquals(SettingsEventTypes.PARTIAL_SAVE, ((SettingsRollbackEvent) evt).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	// ItemLockedException
	@Test
	public void saveSettings_ThrowItemLockedExceptionAndSendRollbackEvent_IfItemLocked() throws Exception {

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.SAVE_FAILED)
				.buildFrom(SettingsDataUtil.getSettingsSavedEvent(code + "31"));
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "save",
					new SaveSettingsCommand(SettingsDataUtil.getPersistenceDTO(code + "31")));
		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(EventTypes.ROLLBACK, rollback.getType());
		assertEquals(rollbackFailedEvent.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	@Test
	public void prepareRollbackEvent_SendSettingsRollbackEventWithFailEventTypeEqualToSaveSettings_IfSaveItemIsLocked()
			throws Exception {

		SaveSettingsEvent saveSettingsEvent = SettingsDataUtil.getSaveSettingsEvent(code + "32");
		kafkaTemplate.send(settings_topic, saveSettingsEvent.getAggregateId(), saveSettingsEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(saveSettingsEvent);

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(saveSettingsEvent.getType(), event.getFailEventType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		assertNull(((SettingsRollbackEvent) rollback).getLastSnapshotItem());
	}

	@Test
	public void prepareRollbackEventAfterRollbackFail_SendSettingsRollbackEventWithFailEventTypeEqualToSaveSettings_IfSaveItemIsLocked()
			throws Exception {

		SaveSettingsEvent saveSettingsEvent = SettingsDataUtil.getSaveSettingsEvent(code + "33");
		kafkaTemplate.send(settings_topic, saveSettingsEvent.getAggregateId(), saveSettingsEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.SAVE_FAILED)
				.buildFrom(saveSettingsEvent);
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(rollbackFailedEvent);

		try {
			kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);
		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		assertNull(((SettingsRollbackEvent) rollback).getLastSnapshotItem());
	}

	// Update
	// ConfirmationTimeoutException
	@Test
	public void updateSettings_ThrowConfirmationTimeoutExceptionAndSendRollbackEvent_IfConfirmationIsNotReceived()
			throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "34a");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "34");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		Thread.sleep(8000);

		PersistenceDTO persistenceDTO = SettingsDataUtil.getPersistenceDTO(code + "34");
		persistenceDTO.setSettingsId(selectedEvent.getAggregateId());

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "update", new UpdateSettingsCommand(persistenceDTO));
		} catch (Exception e) {
			assertTrue(e instanceof ConfirmationTimeoutException);
		}

		Event evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		while (evt == null || !evt.getType().equals(EventTypes.ROLLBACK))
			evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertEquals(EventTypes.ROLLBACK, evt.getType());
		assertEquals(SettingsEventTypes.PARTIAL_SAVE, ((SettingsRollbackEvent) evt).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	// ItemLockedException
	@Test
	public void updateSettings_ThrowItemLockedExceptionAndSendRollbackEvent_IfItemLocked() throws Exception {

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "35");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.SAVE_FAILED)
				.buildFrom(settingsSavedEvent);
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "update",
					new UpdateSettingsCommand(SettingsDataUtil.getPersistenceDTO(code + "35")));
		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(EventTypes.ROLLBACK, rollback.getType());
		assertEquals(rollbackFailedEvent.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(50, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	@Test
	public void prepareRollbackEvent_SendSettingsRollbackEventWithFailEventTypeEqualToSaveSettings_IfItemIsLocked()
			throws Exception {

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "36");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		SaveSettingsEvent saveSettingsEvent = SettingsDataUtil.getSaveSettingsEvent(code + "36");
		kafkaTemplate.send(settings_topic, saveSettingsEvent.getAggregateId(), saveSettingsEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(saveSettingsEvent);

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(saveSettingsEvent.getType(), event.getFailEventType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		JSONAssert.assertEquals(settingsSavedEvent.getSettings().toString(),
				((SettingsRollbackEvent) rollback).getLastSnapshotItem().toString(), false);
	}

	@Test
	public void prepareRollbackEventAfterRollbackFail_SendSettingsRollbackEventWithFailEventTypeEqualToSaveSettings_IfItemIsLocked()
			throws Exception {

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "37");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		SaveSettingsEvent saveSettingsEvent = SettingsDataUtil.getSaveSettingsEvent(code + "37");
		kafkaTemplate.send(settings_topic, saveSettingsEvent.getAggregateId(), saveSettingsEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.SAVE_FAILED)
				.buildFrom(saveSettingsEvent);
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(rollbackFailedEvent);

		try {
			kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);
		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		JSONAssert.assertEquals(settingsSavedEvent.getSettings().toString(),
				((SettingsRollbackEvent) rollback).getLastSnapshotItem().toString(), false);
	}

	// Clone
	// ConfirmationTimeoutException
	@Test
	public void cloneSettings_ThrowConfirmationTimeoutExceptionAndSendRollbackEvent_IfConfirmationIsNotReceived()
			throws Exception {

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "38a");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		Thread.sleep(8000);

		PersistenceDTO persistenceDTO = SettingsDataUtil.getPersistenceDTO(code + "38");
		persistenceDTO.setSettingsId(settingsSavedEvent.getAggregateId());

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "clone",
					new CloneSettingsCommand(persistenceDTO.getSettingsId(), persistenceDTO.getService()));
		} catch (Exception e) {
			assertTrue(e instanceof ConfirmationTimeoutException);
		}

		Event evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		while (evt == null || !evt.getType().equals(EventTypes.ROLLBACK))
			evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertEquals(EventTypes.ROLLBACK, evt.getType());
		assertEquals(SettingsEventTypes.CLONE, ((SettingsRollbackEvent) evt).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	// ItemLockedException
	@Test
	public void cloneSettings_ThrowItemLockedException_IfItemLocked() throws Exception {

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "39a");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.SAVE_FAILED)
				.buildFrom(settingsSavedEvent);
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		PersistenceDTO persistenceDTO = SettingsDataUtil.getPersistenceDTO(code + "39");
		persistenceDTO.setSettingsId(settingsSavedEvent.getAggregateId());

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "clone",
					new CloneSettingsCommand(persistenceDTO.getSettingsId(), persistenceDTO.getService()));
		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
	}

	// Delete
	// ConfirmationTimeoutException
	@Test
	public void deleteSettings_ThrowConfirmationTimeoutExceptionAndSendRollbackEvent_IfConfirmationIsNotReceived()
			throws Exception {

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "40");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		Thread.sleep(8000);

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "delete",
					new DeleteSettingsCommand(settingsSavedEvent.getSettings().getId()));
		} catch (Exception e) {
			assertTrue(e instanceof ConfirmationTimeoutException);
		}

		Event confirm = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(confirm);
		assertEquals(SettingsEventTypes.DELETE_CHECKED, confirm.getType());

		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(EventTypes.ROLLBACK, rollback.getType());
		assertEquals(SettingsEventTypes.CHECK_DELETE, ((SettingsRollbackEvent) rollback).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	// ItemLockedException
	@Test
	public void deleteSettings_ThrowItemLockedExceptionAndSendRollbackEvent_IfItemLocked() throws Exception {

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "41");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.DELETE_FAILED)
				.buildFrom(settingsSavedEvent);
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "delete",
					new DeleteSettingsCommand(settingsSavedEvent.getSettings().getId()));
		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(EventTypes.ROLLBACK, rollback.getType());
		assertEquals(rollbackFailedEvent.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(50, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	@Test
	public void prepareRollbackEvent_SendSettingsRollbackEventWithFailEventTypeEqualToDeleteSettings_IfItemIsLocked()
			throws Exception {

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "42");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		DeleteSettingsEvent deleteSettingsEvent = SettingsDataUtil.getDeleteSettingsEvent(code + "42");
		kafkaTemplate.send(settings_topic, deleteSettingsEvent.getAggregateId(), deleteSettingsEvent);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(deleteSettingsEvent);

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(deleteSettingsEvent.getType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		JSONAssert.assertEquals(settingsSavedEvent.getSettings().toString(),
				((SettingsRollbackEvent) rollback).getLastSnapshotItem().toString(), false);
	}

	@Test
	public void prepareRollbackEventAfterRollbackFail_SendSettingsRollbackEventWithFailEventTypeEqualToDeleteSettings_IfItemIsLocked()
			throws Exception {

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(code + "43");
		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		DeleteSettingsEvent deleteSettingsEvent = SettingsDataUtil.getDeleteSettingsEvent(code + "43");
		kafkaTemplate.send(settings_topic, deleteSettingsEvent.getAggregateId(), deleteSettingsEvent);

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.DELETE_FAILED)
				.buildFrom(deleteSettingsEvent);
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(rollbackFailedEvent);

		try {
			kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);
		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		JSONAssert.assertEquals(settingsSavedEvent.getSettings().toString(),
				((SettingsRollbackEvent) rollback).getLastSnapshotItem().toString(), false);
	}

	// Select

	// ConfirmationTimeoutException
	@Test
	public void select_ThrowConfirmationTimeoutExceptionAndSendRollbackEvent_IfConfirmationIsNotReceived()
			throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "45");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "select",
					new SelectCommand(SettingsDataUtil.getSelectionDTO(code + "45")));
		} catch (Exception e) {
			assertTrue(e instanceof ConfirmationTimeoutException);
		}

		Event evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		while (evt == null || !evt.getType().equals(EventTypes.ROLLBACK))
			evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertEquals(EventTypes.ROLLBACK, evt.getType());
		assertEquals(SettingsEventTypes.PARTIAL_SELECT, ((SettingsRollbackEvent) evt).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	// ItemLockedException
	@Test
	public void select_ThrowItemLockedExceptionAndSendRollbackEvent_IfItemLocked() throws Exception {

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.SELECT_FAILED)
				.buildFrom(SettingsDataUtil.getSettingsSavedEvent(code + "46"));
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "select",
					new SelectCommand(SettingsDataUtil.getSelectionDTO(code + "46")));
		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(EventTypes.ROLLBACK, rollback.getType());
		assertEquals(rollbackFailedEvent.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	@Test
	public void prepareRollbackEvent_SendSettingsRollbackEventWithFailEventTypeEqualToSelect_IfSelectItemIsLocked()
			throws Exception {

		SelectEvent selectEvent = SettingsDataUtil.getSelectEvent(code + "47");
		kafkaTemplate.send(settings_topic, selectEvent.getAggregateId(), selectEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(selectEvent);

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(selectEvent.getType(), event.getFailEventType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		assertNull(((SettingsRollbackEvent) rollback).getLastSnapshotItem());
	}

	@Test
	public void prepareRollbackEventAfterRollbackFail_SendSettingsRollbackEventWithFailEventTypeEqualToSelect_IfSelectItemIsLocked()
			throws Exception {

		SelectEvent selectEvent = SettingsDataUtil.getSelectEvent(code + "48");
		kafkaTemplate.send(settings_topic, selectEvent.getAggregateId(), selectEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.SELECT_FAILED)
				.buildFrom(selectEvent);
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(rollbackFailedEvent);

		try {
			kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);
		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		assertNull(((SettingsRollbackEvent) rollback).getLastSnapshotItem());
	}

	// Deselect

	// ConfirmationTimeoutException
	@Test
	public void deselect_ThrowConfirmationTimeoutExceptionAndSendRollbackEvent_IfConfirmationIsNotReceived()
			throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "49");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "deselect",
					new DeselectCommand(SettingsDataUtil.getSelectionDTO(code + "49")));
		} catch (Exception e) {
			assertTrue(e instanceof ConfirmationTimeoutException);
		}

		Event evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		while (evt == null || !evt.getType().equals(EventTypes.ROLLBACK))
			evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertEquals(EventTypes.ROLLBACK, evt.getType());
		assertEquals(SettingsEventTypes.PARTIAL_DESELECT, ((SettingsRollbackEvent) evt).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	// ItemLockedException
	@Test
	public void deselect_ThrowItemLockedExceptionAndSendRollbackEvent_IfItemLocked() throws Exception {

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.DESELECT_FAILED)
				.buildFrom(SettingsDataUtil.getSettingsSavedEvent(code + "50"));
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "deselect",
					new DeselectCommand(SettingsDataUtil.getSelectionDTO(code + "50")));
		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(EventTypes.ROLLBACK, rollback.getType());
		assertEquals(rollbackFailedEvent.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	@Test
	public void prepareRollbackEvent_SendSettingsRollbackEventWithFailEventTypeEqualToDeselect_IfDeselectItemIsLocked()
			throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "51");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		DeselectEvent deselectEvent = SettingsDataUtil.getDeselectEvent(code + "51");
		kafkaTemplate.send(settings_topic, deselectEvent.getAggregateId(), deselectEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(deselectEvent);

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(deselectEvent.getType(), event.getFailEventType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		JSONAssert.assertEquals(selectedEvent.getSettings().toString(),
				((SettingsRollbackEvent) rollback).getLastSnapshotItem().toString(), false);
	}

	@Test
	public void prepareRollbackEventAfterRollbackFail_SendSettingsRollbackEventWithFailEventTypeEqualToDeselect_IfDeselectItemIsLocked()
			throws Exception {

		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "52");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		DeselectEvent deselectEvent = SettingsDataUtil.getDeselectEvent(code + "52");
		kafkaTemplate.send(settings_topic, deselectEvent.getAggregateId(), deselectEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.DESELECT_FAILED)
				.buildFrom(deselectEvent);
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(rollbackFailedEvent);

		try {
			kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);
		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		JSONAssert.assertEquals(selectedEvent.getSettings().toString(),
				((SettingsRollbackEvent) rollback).getLastSnapshotItem().toString(), false);
	}

	// Clear

	// ConfirmationTimeoutException
	@Test
	public void clear_ThrowConfirmationTimeoutExceptionAndSendRollbackEvent_IfConfirmationIsNotReceived()
			throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "53");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		Thread.sleep(1000);

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "clear",
					new ClearCommand(SettingsDataUtil.getSelectionDTO(code + "53")));
		} catch (Exception e) {
			assertTrue(e instanceof ConfirmationTimeoutException);
		}

		Event evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		while (evt == null || !evt.getType().equals(EventTypes.ROLLBACK))
			evt = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertEquals(EventTypes.ROLLBACK, evt.getType());
		assertEquals(SettingsEventTypes.PARTIAL_CLEAR_SELECTION, ((SettingsRollbackEvent) evt).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	// ItemLockedException
	@Test
	public void clear_ThrowItemLockedExceptionAndSendRollbackEvent_IfItemLocked() throws Exception {

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.CLEAR_SELECTION_FAILED)
				.buildFrom(SettingsDataUtil.getSettingsSavedEvent(code + "54"));
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		try {
			Whitebox.invokeMethod(settingsCommandHandler, "clear",
					new ClearCommand(SettingsDataUtil.getSelectionDTO(code + "54")));
		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(EventTypes.ROLLBACK, rollback.getType());
		assertEquals(rollbackFailedEvent.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());

		// LLegó un mensaje de alerta
		Message message = (Message) blockingQueueForAlerts.poll(40, TimeUnit.SECONDS);
		assertNotNull(message);
		assertEquals(AlertType.ERROR.name(), message.getType());
	}

	@Test
	public void prepareRollbackEvent_SendSettingsRollbackEventWithFailEventTypeEqualToClearSelection_IfItemIsLocked()
			throws Exception {

		// Envía selected para meterlo en el stream
		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "55");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		ClearSelectionEvent clearSelectionEvent = SettingsDataUtil.getClearEvent(code + "55");
		kafkaTemplate.send(settings_topic, clearSelectionEvent.getAggregateId(), clearSelectionEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(clearSelectionEvent);

		kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		Event rollback = (Event) blockingQueue.poll(40, TimeUnit.SECONDS);

		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(clearSelectionEvent.getType(), event.getFailEventType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		JSONAssert.assertEquals(selectedEvent.getSettings().toString(),
				((SettingsRollbackEvent) rollback).getLastSnapshotItem().toString(), false);
	}

	@Test
	public void prepareRollbackEventAfterRollbackFail_SendSettingsRollbackEventWithFailEventTypeEqualToClearSelection_IfItemIsLocked()
			throws Exception {

		SelectedEvent selectedEvent = SettingsDataUtil.getSelectedEvent(code + "56");
		selectedEvent.getSettings().setName(null);
		kafkaTemplate.send(settings_topic, selectedEvent.getAggregateId(), selectedEvent);
		blockingQueue.poll(60, TimeUnit.SECONDS);

		ClearSelectionEvent clearSelectionEvent = SettingsDataUtil.getClearEvent(code + "56");
		kafkaTemplate.send(settings_topic, clearSelectionEvent.getAggregateId(), clearSelectionEvent);
		blockingQueue.poll(30, TimeUnit.SECONDS);

		RollbackFailedEvent rollbackFailedEvent = new RollbackFailedEvent(SettingsEventTypes.CLEAR_SELECTION_FAILED)
				.buildFrom(clearSelectionEvent);
		kafkaTemplate.send(settings_topic, rollbackFailedEvent.getAggregateId(), rollbackFailedEvent);

		Thread.sleep(8000);

		PrepareRollbackEvent event = (PrepareRollbackEvent) new PersistenceAggregate(null, userService)
				.getRollbackEvent(rollbackFailedEvent);

		try {
			kafkaTemplate.send(settings_topic, event.getAggregateId(), event);

		} catch (Exception e) {
			assertTrue(e instanceof ItemLockedException);
		}
		Event rollback = (Event) blockingQueue.poll(60, TimeUnit.SECONDS);
		assertNotNull(rollback);
		assertEquals(SettingsEventTypes.ROLLBACK, rollback.getType());
		assertEquals(event.getFailEventType(), ((SettingsRollbackEvent) rollback).getFailEventType());
		JSONAssert.assertEquals(selectedEvent.getSettings().toString(),
				((SettingsRollbackEvent) rollback).getLastSnapshotItem().toString(), false);
	}

	// Select

	@KafkaHandler
	public void selectedEvent(SelectedEvent selectedEvent) {

		blockingQueue.offer(selectedEvent);
	}

	@KafkaHandler
	public void selectEvent(SelectEvent selectEvent) {

		blockingQueue.offer(selectEvent);
	}

	@KafkaHandler
	public void selectFailedEvent(SelectFailedEvent selectFailedEvent) {

		blockingQueue.offer(selectFailedEvent);
	}

	@KafkaHandler
	public void selectCancelledEvent(SelectCancelledEvent selectCancelledEvent) {

		blockingQueue.offer(selectCancelledEvent);
	}

	// Deselect

	@KafkaHandler
	public void deselectedEvent(DeselectedEvent deselectedEvent) {

		blockingQueue.offer(deselectedEvent);
	}

	@KafkaHandler
	public void deselectEvent(DeselectEvent deselectEvent) {

		blockingQueue.offer(deselectEvent);
	}

	@KafkaHandler
	public void deselectFailedEvent(DeselectFailedEvent deselectFailedEvent) {

		blockingQueue.offer(deselectFailedEvent);
	}

	@KafkaHandler
	public void deselectCancelledEvent(DeselectCancelledEvent deselectCancelledEvent) {

		blockingQueue.offer(deselectCancelledEvent);
	}

	// Clear

	@KafkaHandler
	public void selectionClearedEvent(SelectionClearedEvent selectionClearedEvent) {

		blockingQueue.offer(selectionClearedEvent);
	}

	@KafkaHandler
	public void clearSelectionEvent(ClearSelectionEvent clearSelectionEvent) {

		blockingQueue.offer(clearSelectionEvent);
	}

	@KafkaHandler
	public void clearSelectionFailedEvent(ClearSelectionFailedEvent clearSelectionFailedEvent) {

		blockingQueue.offer(clearSelectionFailedEvent);
	}

	@KafkaHandler
	public void clearSelectionCancelledEvent(ClearSelectionCancelledEvent clearSelectionCancelledEvent) {

		blockingQueue.offer(clearSelectionCancelledEvent);
	}

	// Save

	@KafkaHandler
	public void settingsSavedEvent(SettingsSavedEvent settingsSavedEvent) {

		blockingQueue.offer(settingsSavedEvent);
	}

	@KafkaHandler
	public void saveSettingsEvent(SaveSettingsEvent saveSettingsEvent) {

		blockingQueue.offer(saveSettingsEvent);
	}

	@KafkaHandler
	public void saveSettingsFailedEvent(SaveSettingsFailedEvent saveSettingsFailedEvent) {

		blockingQueue.offer(saveSettingsFailedEvent);
	}

	@KafkaHandler
	public void saveSettingsCancelledEvent(SaveSettingsCancelledEvent saveSettingsCancelledEvent) {

		blockingQueue.offer(saveSettingsCancelledEvent);
	}

	// Delete

	@KafkaHandler
	public void settingsDeletedEvent(SettingsDeletedEvent settingsDeletedEvent) {

		blockingQueue.offer(settingsDeletedEvent);
	}

	@KafkaHandler
	public void deleteSettingsCancelledEvent(DeleteSettingsCancelledEvent deleteSettingsCancelledEvent) {

		blockingQueue.offer(deleteSettingsCancelledEvent);
	}

	@KafkaHandler
	public void deleteSettingsCheckedEvent(DeleteSettingsCheckedEvent deleteSettingsCheckedEvent) {

		blockingQueue.offer(deleteSettingsCheckedEvent);
	}

	@KafkaHandler
	public void deleteSettingsCheckFailedEvent(DeleteSettingsCheckFailedEvent deleteSettingsCheckFailedEvent) {

		blockingQueue.offer(deleteSettingsCheckFailedEvent);
	}

	//

	@KafkaHandler
	public void settingsRollbackEvent(SettingsRollbackEvent settingsRollbackEvent) {

		blockingQueue.offer(settingsRollbackEvent);
	}

	@KafkaListener(topics = "${broker.topic.alert}", groupId = "test")
	public void errorAlert(Message message) {
		blockingQueueForAlerts.offer(message);
	}

	@KafkaHandler(isDefault = true)
	public void defaultEvent(Object def) {

	}
}
