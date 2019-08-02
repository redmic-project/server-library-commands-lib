package es.redmic.commandslib.usersettings.handler;

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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import es.redmic.brokerlib.alert.AlertService;
import es.redmic.commandslib.commands.CommandHandler;
import es.redmic.commandslib.streaming.common.StreamConfig;
import es.redmic.commandslib.streaming.common.StreamConfig.Builder;
import es.redmic.commandslib.usersettings.aggregate.PersistenceAggregate;
import es.redmic.commandslib.usersettings.aggregate.SelectionAggregate;
import es.redmic.commandslib.usersettings.commands.ClearCommand;
import es.redmic.commandslib.usersettings.commands.CloneSettingsCommand;
import es.redmic.commandslib.usersettings.commands.DeleteSettingsCommand;
import es.redmic.commandslib.usersettings.commands.DeselectCommand;
import es.redmic.commandslib.usersettings.commands.SaveSettingsCommand;
import es.redmic.commandslib.usersettings.commands.SelectCommand;
import es.redmic.commandslib.usersettings.commands.UpdateSettingsAccessedDateCommand;
import es.redmic.commandslib.usersettings.commands.UpdateSettingsCommand;
import es.redmic.commandslib.usersettings.statestore.SettingsStateStore;
import es.redmic.commandslib.usersettings.streams.SettingsEventStreams;
import es.redmic.exception.factory.ExceptionFactory;
import es.redmic.restlib.config.UserService;
import es.redmic.usersettingslib.dto.SettingsDTO;
import es.redmic.usersettingslib.events.SettingsEventFactory;
import es.redmic.usersettingslib.events.SettingsEventTypes;
import es.redmic.usersettingslib.events.clearselection.ClearSelectionCancelledEvent;
import es.redmic.usersettingslib.events.clearselection.PartialClearSelectionEvent;
import es.redmic.usersettingslib.events.clearselection.SelectionClearedEvent;
import es.redmic.usersettingslib.events.clone.CloneSettingsEvent;
import es.redmic.usersettingslib.events.delete.CheckDeleteSettingsEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsCancelledEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsCheckedEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsConfirmedEvent;
import es.redmic.usersettingslib.events.delete.SettingsDeletedEvent;
import es.redmic.usersettingslib.events.deselect.DeselectCancelledEvent;
import es.redmic.usersettingslib.events.deselect.DeselectedEvent;
import es.redmic.usersettingslib.events.deselect.PartialDeselectEvent;
import es.redmic.usersettingslib.events.save.PartialSaveSettingsEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsCancelledEvent;
import es.redmic.usersettingslib.events.save.SettingsSavedEvent;
import es.redmic.usersettingslib.events.select.PartialSelectEvent;
import es.redmic.usersettingslib.events.select.SelectCancelledEvent;
import es.redmic.usersettingslib.events.select.SelectedEvent;
import es.redmic.usersettingslib.events.update.UpdateSettingsAccessedDateEvent;

@Component
@ConditionalOnProperty(name = "redmic.user-settings.enabled", havingValue = "true")
@KafkaListener(topics = "${broker.topic.settings}")
public class SettingsCommandHandler extends CommandHandler {

	@Value("${spring.kafka.properties.schema.registry.url}")
	protected String schemaRegistry;

	@Value("${spring.kafka.bootstrap-servers}")
	protected String bootstrapServers;

	@Value("${broker.topic.settings}")
	private String settingsTopic;

	@Value("${broker.state.store.settings.dir}")
	private String stateStoreSettingsDir;

	@Value("${broker.state.store.settings.id}")
	private String settingsIdConfig;

	@Value("${broker.stream.events.settings.id}")
	private String settingsEventsStreamId;

	@Value("${stream.windows.time.ms}")
	private Long streamWindowsTime;

	private SettingsStateStore settingsStateStore;

	@Autowired
	UserService userService;

	@Autowired
	AlertService alertService;

	public SettingsCommandHandler() {

	}

	@PostConstruct
	private void setUp() {

		// @formatter:off
		
		Builder config = StreamConfig.Builder
				.bootstrapServers(bootstrapServers)
				.schemaRegistry(schemaRegistry)
				.stateStoreDir(stateStoreSettingsDir)
				.topic(settingsTopic);
		
		settingsStateStore = new SettingsStateStore(
				config
					.serviceId(settingsIdConfig)
					.build(), alertService);

		new SettingsEventStreams(
				config
					.serviceId(settingsEventsStreamId)
					.windowsTime(streamWindowsTime)
					.build(), alertService);
		
		// @formatter:on
	}

	public SettingsDTO select(SelectCommand cmd) {

		SelectionAggregate agg = new SelectionAggregate(settingsStateStore, userService);

		PartialSelectEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se debe aplicar
		if (event == null)
			return null;

		// Se aplica el evento
		agg.apply(event);

		return sendEventAndWaitResult(event, settingsTopic);
	}

	public SettingsDTO deselect(DeselectCommand cmd) {

		SelectionAggregate agg = new SelectionAggregate(settingsStateStore, userService);

		PartialDeselectEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se debe aplicar
		if (event == null)
			return null;

		// Se aplica el evento
		agg.apply(event);

		return sendEventAndWaitResult(event, settingsTopic);
	}

	public SettingsDTO clear(ClearCommand cmd) {

		SelectionAggregate agg = new SelectionAggregate(settingsStateStore, userService);

		PartialClearSelectionEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se debe aplicar
		if (event == null)
			return null;

		// Se aplica el evento
		agg.apply(event);

		return sendEventAndWaitResult(event, settingsTopic);
	}

	public SettingsDTO save(SaveSettingsCommand cmd) {

		PersistenceAggregate agg = new PersistenceAggregate(settingsStateStore, userService);

		PartialSaveSettingsEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se debe aplicar
		if (event == null)
			return null;

		// Se aplica el evento
		agg.apply(event);

		return sendEventAndWaitResult(event, settingsTopic);
	}

	public SettingsDTO update(UpdateSettingsCommand cmd) {

		PersistenceAggregate agg = new PersistenceAggregate(settingsStateStore, userService);

		PartialSaveSettingsEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se debe aplicar
		if (event == null)
			return null;

		// Se aplica el evento
		agg.apply(event);

		return sendEventAndWaitResult(event, settingsTopic);
	}

	public SettingsDTO delete(DeleteSettingsCommand cmd) {

		PersistenceAggregate agg = new PersistenceAggregate(settingsStateStore, userService);

		CheckDeleteSettingsEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se debe aplicar
		if (event == null)
			return null;

		// Se aplica el evento
		agg.apply(event);

		return sendEventAndWaitResult(event, settingsTopic);
	}

	public SettingsDTO clone(CloneSettingsCommand cmd) {

		PersistenceAggregate agg = new PersistenceAggregate(settingsStateStore, userService);

		CloneSettingsEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se debe aplicar
		if (event == null)
			return null;

		// Se aplica el evento
		agg.apply(event);

		updateSettingsAccessedDate(new UpdateSettingsAccessedDateCommand(cmd.getPersistence().getSettingsId()));

		return sendEventAndWaitResult(event, settingsTopic);
	}

	public void updateSettingsAccessedDate(UpdateSettingsAccessedDateCommand cmd) {

		PersistenceAggregate agg = new PersistenceAggregate(settingsStateStore, userService);

		UpdateSettingsAccessedDateEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se debe aplicar
		if (event == null)
			logger.error("Imposible actualizar fecha de accedido para el item " + cmd.getSettingsId());

		publishToKafka(event, settingsTopic);
	}

	// Select

	@KafkaHandler
	private void listen(SelectedEvent event) {

		// El evento selected se envía desde el stream
		resolveCommand(event.getSessionId(), event.getSettings());
	}

	@KafkaHandler
	private void listen(SelectCancelledEvent event) {

		resolveCommand(event.getSessionId(),
				ExceptionFactory.getException(event.getExceptionType(), event.getArguments()));
	}

	// Deselect

	@KafkaHandler
	private void listen(DeselectedEvent event) {

		// El evento deselected se envía desde el stream
		resolveCommand(event.getSessionId(), event.getSettings());
	}

	@KafkaHandler
	private void listen(DeselectCancelledEvent event) {

		resolveCommand(event.getSessionId(),
				ExceptionFactory.getException(event.getExceptionType(), event.getArguments()));
	}

	// Clear

	@KafkaHandler
	private void listen(SelectionClearedEvent event) {

		// El evento selectionCleared se envía desde el stream

		resolveCommand(event.getSessionId(), event.getSettings());
	}

	@KafkaHandler
	private void listen(ClearSelectionCancelledEvent event) {

		resolveCommand(event.getSessionId(),
				ExceptionFactory.getException(event.getExceptionType(), event.getArguments()));
	}

	// Save

	@KafkaHandler
	private void listen(SettingsSavedEvent event) {

		// El evento settingsSaved se envía desde el stream
		resolveCommand(event.getSessionId(), event.getSettings());
	}

	@KafkaHandler
	private void listen(SaveSettingsCancelledEvent event) {

		resolveCommand(event.getSessionId(),
				ExceptionFactory.getException(event.getExceptionType(), event.getArguments()));
	}

	// Delete

	@KafkaHandler
	private void listen(DeleteSettingsCheckedEvent event) {

		publishToKafka(SettingsEventFactory.getEvent(event, SettingsEventTypes.DELETE), settingsTopic);
	}

	@KafkaHandler
	private void listen(DeleteSettingsConfirmedEvent event) {

		publishToKafka(SettingsEventFactory.getEvent(event, SettingsEventTypes.DELETED), settingsTopic);
	}

	@KafkaHandler
	private void listen(SettingsDeletedEvent event) {

		// El evento settingsDeleted se envía desde el stream
		resolveCommand(event.getSessionId());
	}

	@KafkaHandler
	private void listen(DeleteSettingsCancelledEvent event) {

		resolveCommand(event.getSessionId(),
				ExceptionFactory.getException(event.getExceptionType(), event.getArguments()));
	}
}
