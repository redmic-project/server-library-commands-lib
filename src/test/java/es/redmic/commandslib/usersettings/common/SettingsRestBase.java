package es.redmic.commandslib.usersettings.common;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.concurrent.ListenableFuture;

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.listener.SendListener;
import es.redmic.commandslib.usersettings.handler.SettingsCommandHandler;
import es.redmic.commandslib.usersettings.statestore.SettingsStateStore;
import es.redmic.testutils.documentation.DocumentationCommandBaseTest;
import es.redmic.usersettingslib.dto.PersistenceDTO;
import es.redmic.usersettingslib.dto.SelectionDTO;
import es.redmic.usersettingslib.events.clearselection.ClearSelectionConfirmedEvent;
import es.redmic.usersettingslib.events.clearselection.ClearSelectionEvent;
import es.redmic.usersettingslib.events.common.SettingsEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsConfirmedEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsEvent;
import es.redmic.usersettingslib.events.deselect.DeselectConfirmedEvent;
import es.redmic.usersettingslib.events.deselect.DeselectEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsConfirmedEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsEvent;
import es.redmic.usersettingslib.events.save.SettingsSavedEvent;
import es.redmic.usersettingslib.events.select.SelectConfirmedEvent;
import es.redmic.usersettingslib.events.select.SelectEvent;
import es.redmic.usersettingslib.events.select.SelectedEvent;
import es.redmic.usersettingslib.events.update.UpdateSettingsAccessedDateEvent;
import es.redmic.usersettingslib.unit.utils.SettingsDataUtil;

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

public class SettingsRestBase extends DocumentationCommandBaseTest {

	@Value("${documentation.MICROSERVICE_HOST}")
	private String HOST;

	@Value("${controller.mapping.SETTINGS}")
	private String SETTINGS_PATH;

	@Value("${spring.mvc.servlet.path}")
	String microServiceName;

	@Value("${controller.mapping.SETTINGS}")
	String controllerName;

	String serviceName;

	@Autowired
	SettingsCommandHandler settingsCommandHandler;

	SettingsStateStore settingsStateStore;

	protected static BlockingQueue<Object> blockingQueue;

	@Autowired
	private KafkaTemplate<String, Event> kafkaTemplate;

	@Value("${broker.topic.settings}")
	private String settings_topic;

	private String userId = "13";

	@BeforeClass
	public static void setup() {

		blockingQueue = new LinkedBlockingDeque<>();
	}

	@Before
	public void before() {

		serviceName = microServiceName + controllerName;

		settingsStateStore = Mockito.mock(SettingsStateStore.class);

		Whitebox.setInternalState(settingsCommandHandler, "settingsStateStore", settingsStateStore);

		// @formatter:off
		
		mockMvc = MockMvcBuilders
				.webAppContextSetup(webApplicationContext)
				.addFilters(springSecurityFilterChain)
				.apply(documentationConfiguration(this.restDocumentation)
						.uris().withScheme(SCHEME).withHost(HOST).withPort(PORT))
				.alwaysDo(this.document).build();

		// @formatter:on
	}

	@Test
	public void selectInNewSelectionRequest_ReturnSelection_IfWasSuccess() throws Exception {

		String CODE = UUID.randomUUID().toString();

		SelectionDTO selectionDTO = SettingsDataUtil.getSelectionDTO(CODE);

		selectionDTO.setId(null);
		selectionDTO.setService(null);
		selectionDTO.setUserId(null);

		// @formatter:off
		
		this.mockMvc
				.perform(post(SETTINGS_PATH + "/select")
						.header("Authorization", "Bearer " + getTokenOAGUser())
						.content(mapper.writeValueAsString(selectionDTO))
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.body", notNullValue()))
				.andExpect(jsonPath("$.body.service", is(serviceName)))
				.andExpect(jsonPath("$.body.userId", is(userId)))
				.andExpect(jsonPath("$.body.selection", notNullValue()))
				.andExpect(jsonPath("$.body.selection", hasSize(1)))
				.andExpect(jsonPath("$.body.selection", hasItem("1")));
		
		// @formatter:on

		SelectEvent event = (SelectEvent) blockingQueue.poll(50, TimeUnit.SECONDS);

		SelectEvent expectedEvent = SettingsDataUtil.getSelectEvent(CODE);
		assertNotNull(event);
		assertEquals(event.getType(), expectedEvent.getType());
		assertEquals(event.getVersion(), expectedEvent.getVersion());
		assertEquals(event.getSettings().getService(), serviceName);
	}

	@Test
	public void selectRequest_ReturnSelection_IfWasSuccess() throws Exception {

		String CODE = UUID.randomUUID().toString();

		SelectedEvent evt = SettingsDataUtil.getSelectedEvent(CODE);
		evt.getSettings().setName(null);
		evt.getSettings().setService(serviceName);
		evt.getSettings().setUserId(userId);

		kafkaTemplate.send(settings_topic, evt.getAggregateId(), evt);

		when(settingsStateStore.get(anyString())).thenReturn(evt);

		SelectionDTO selectionDTO = SettingsDataUtil.getSelectionDTO(CODE);
		selectionDTO.setService(null);
		selectionDTO.setUserId(null);

		// @formatter:off
		
		String id = SettingsDataUtil.PREFIX + CODE;
		
		this.mockMvc
				.perform(put(SETTINGS_PATH + "/select/" + id)
						.header("Authorization", "Bearer " + getTokenOAGUser())
						.content(mapper.writeValueAsString(selectionDTO))
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.body", notNullValue()))
				.andExpect(jsonPath("$.body.id", is(id)))
				.andExpect(jsonPath("$.body.service", is(serviceName)))
				.andExpect(jsonPath("$.body.userId", is(userId)))
				.andExpect(jsonPath("$.body.selection", notNullValue()))
				.andExpect(jsonPath("$.body.selection", hasSize(1)))
				.andExpect(jsonPath("$.body.selection", hasItem("1")));
		
		// @formatter:on

		SelectEvent event = (SelectEvent) blockingQueue.poll(50, TimeUnit.SECONDS);

		SelectEvent expectedEvent = SettingsDataUtil.getSelectEvent(CODE);
		assertNotNull(event);
		assertEquals(event.getType(), expectedEvent.getType());
		assertEquals(event.getVersion(), expectedEvent.getVersion());
		assertEquals(event.getSettings().getService(), serviceName);
	}

	@Test
	public void deselectRequest_ReturnSelection_IfWasSuccess() throws Exception {

		String CODE = UUID.randomUUID().toString();

		SelectedEvent evt = SettingsDataUtil.getSelectedEvent(CODE);
		evt.getSettings().setName(null);
		evt.getSettings().setService(serviceName);
		evt.getSettings().setUserId(userId);

		kafkaTemplate.send(settings_topic, evt.getAggregateId(), evt);

		when(settingsStateStore.get(anyString())).thenReturn(evt);

		SelectionDTO selectionDTO = SettingsDataUtil.getSelectionDTO(CODE);
		selectionDTO.setService(null);
		selectionDTO.setUserId(null);

		// @formatter:off
		
		String id = SettingsDataUtil.PREFIX + CODE;
		
		this.mockMvc
				.perform(put(SETTINGS_PATH + "/deselect/" + id)
						.header("Authorization", "Bearer " + getTokenOAGUser())
						.content(mapper.writeValueAsString(selectionDTO))
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.body", notNullValue()))
				.andExpect(jsonPath("$.body.id", is(id)))
				.andExpect(jsonPath("$.body.service", is(serviceName)))
				.andExpect(jsonPath("$.body.userId", is(userId)))
				.andExpect(jsonPath("$.body.selection", notNullValue()))
				.andExpect(jsonPath("$.body.selection", hasSize(0)));
		
		// @formatter:on

		DeselectEvent event = (DeselectEvent) blockingQueue.poll(50, TimeUnit.SECONDS);

		DeselectEvent expectedEvent = SettingsDataUtil.getDeselectEvent(CODE);
		assertNotNull(event);
		assertEquals(expectedEvent.getType(), event.getType());
		assertEquals(expectedEvent.getVersion(), event.getVersion());
		assertEquals(serviceName, event.getSettings().getService());
	}

	@Test
	public void clearRequest_ReturnSelection_IfWasSuccess() throws Exception {

		String CODE = UUID.randomUUID().toString();

		SelectedEvent evt = SettingsDataUtil.getSelectedEvent(CODE);
		evt.getSettings().setName(null);
		evt.getSettings().setService(serviceName);
		evt.getSettings().setUserId(userId);

		kafkaTemplate.send(settings_topic, evt.getAggregateId(), evt);

		when(settingsStateStore.get(anyString())).thenReturn(evt);

		SelectionDTO selectionDTO = SettingsDataUtil.getSelectionDTO(CODE);
		selectionDTO.setService(null);
		selectionDTO.setUserId(null);
		selectionDTO.getSelection().clear();

		// @formatter:off
		
		String id = SettingsDataUtil.PREFIX + CODE;
		
		this.mockMvc
				.perform(put(SETTINGS_PATH + "/clearselection/" + id)
						.header("Authorization", "Bearer " + getTokenOAGUser())
						.content(mapper.writeValueAsString(selectionDTO))
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.body", notNullValue()))
				.andExpect(jsonPath("$.body.id", is(id)))
				.andExpect(jsonPath("$.body.service", is(serviceName)))
				.andExpect(jsonPath("$.body.userId", is(userId)))
				.andExpect(jsonPath("$.body.selection", notNullValue()))
				.andExpect(jsonPath("$.body.selection", hasSize(0)));
		
		// @formatter:on

		ClearSelectionEvent event = (ClearSelectionEvent) blockingQueue.poll(50, TimeUnit.SECONDS);

		ClearSelectionEvent expectedEvent = SettingsDataUtil.getClearEvent(CODE);
		assertNotNull(event);
		assertEquals(expectedEvent.getType(), event.getType());
		assertEquals(expectedEvent.getVersion(), event.getVersion());
		assertEquals(serviceName, event.getSettings().getService());
	}

	@Test
	public void saveRequest_ReturnSavedItem_IfWasSuccess() throws Exception {

		SelectedEvent evt = SettingsDataUtil.getSelectedEvent(UUID.randomUUID().toString());
		evt.getSettings().setName(null);
		evt.getSettings().setService(serviceName);
		evt.getSettings().setUserId(userId);

		kafkaTemplate.send(settings_topic, evt.getAggregateId(), evt);

		String CODE = UUID.randomUUID().toString();

		PersistenceDTO persistenceDTO = SettingsDataUtil.getPersistenceDTO(CODE);
		persistenceDTO.setSettingsId(evt.getAggregateId());

		// @formatter:off
		
		this.mockMvc
				.perform(post(SETTINGS_PATH)
						.header("Authorization", "Bearer " + getTokenOAGUser())
						.content(mapper.writeValueAsString(persistenceDTO))
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.body", notNullValue()))
				.andExpect(jsonPath("$.body.service", is(serviceName)))
				.andExpect(jsonPath("$.body.userId", is(userId)))
				.andExpect(jsonPath("$.body.selection", notNullValue()))
				.andExpect(jsonPath("$.body.selection", hasSize(1)))
				.andExpect(jsonPath("$.body.selection", hasItem("1"))).andReturn();
		
		// @formatter:on

		SaveSettingsEvent event = (SaveSettingsEvent) blockingQueue.poll(50, TimeUnit.SECONDS);

		SaveSettingsEvent expectedEvent = SettingsDataUtil.getSaveSettingsEvent(CODE);
		assertNotNull(event);
		assertEquals(expectedEvent.getType(), event.getType());
		assertEquals(expectedEvent.getVersion(), event.getVersion());
		assertEquals(serviceName, event.getSettings().getService());
	}

	@Test
	public void updateRequest_ReturnUpdatedItem_IfWasSuccess() throws Exception {

		SelectedEvent selectedEvt = SettingsDataUtil.getSelectedEvent(UUID.randomUUID().toString());
		selectedEvt.getSettings().setName(null);
		selectedEvt.getSettings().setService(serviceName);
		selectedEvt.getSettings().setUserId(userId);

		kafkaTemplate.send(settings_topic, selectedEvt.getAggregateId(), selectedEvt);

		String CODE = UUID.randomUUID().toString();

		SettingsSavedEvent evt = SettingsDataUtil.getSettingsSavedEvent(CODE);
		evt.getSettings().setService(serviceName);
		evt.getSettings().setUserId(userId);

		kafkaTemplate.send(settings_topic, evt.getAggregateId(), evt);

		when(settingsStateStore.get(anyString())).thenReturn(evt);

		PersistenceDTO persistenceDTO = SettingsDataUtil.getPersistenceDTO(CODE);
		persistenceDTO.setSettingsId(evt.getAggregateId());

		// @formatter:off
		
		String id = SettingsDataUtil.PREFIX + CODE;
		
		this.mockMvc
				.perform(put(SETTINGS_PATH + "/" + id)
						.header("Authorization", "Bearer " + getTokenOAGUser())
						.content(mapper.writeValueAsString(persistenceDTO))
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.body", notNullValue()))
				.andExpect(jsonPath("$.body.id", is(id)))
				.andExpect(jsonPath("$.body.service", is(serviceName)))
				.andExpect(jsonPath("$.body.userId", is(userId)))
				.andExpect(jsonPath("$.body.selection", notNullValue()))
				.andExpect(jsonPath("$.body.selection", hasSize(1)))
				.andExpect(jsonPath("$.body.selection", hasItem("1")));
		
		// @formatter:on

		SaveSettingsEvent event = (SaveSettingsEvent) blockingQueue.poll(50, TimeUnit.SECONDS);

		SaveSettingsEvent expectedEvent = SettingsDataUtil.getSaveSettingsEvent(CODE);

		assertNotNull(event);
		assertEquals(expectedEvent.getType(), event.getType());
		assertEquals((Integer) (expectedEvent.getVersion() + 1), event.getVersion());
		assertEquals(serviceName, event.getSettings().getService());
	}

	@Test
	public void deleteRequest_ReturnSuccessItem_IfWasSuccess() throws Exception {

		String CODE = UUID.randomUUID().toString();

		SettingsSavedEvent evt = SettingsDataUtil.getSettingsSavedEvent(CODE);
		evt.getSettings().setService(serviceName);
		evt.getSettings().setUserId(userId);

		kafkaTemplate.send(settings_topic, evt.getAggregateId(), evt);

		when(settingsStateStore.get(anyString())).thenReturn(evt);

		// @formatter:off
		
		String id = SettingsDataUtil.PREFIX + CODE;
		
		this.mockMvc
				.perform(MockMvcRequestBuilders.delete(SETTINGS_PATH + "/" + id)
						.header("Authorization", "Bearer " + getTokenOAGUser())
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)));
		
		// @formatter:on

		DeleteSettingsEvent event = (DeleteSettingsEvent) blockingQueue.poll(50, TimeUnit.SECONDS);

		DeleteSettingsEvent expectedEvent = SettingsDataUtil.getDeleteSettingsEvent(CODE);
		assertNotNull(event);
		assertEquals(expectedEvent.getType(), event.getType());
		assertEquals(expectedEvent.getVersion(), event.getVersion());
	}

	@Test
	public void cloneRequest_ReturnSavedItem_IfWasSuccess() throws Exception {

		String otherUserId = "15";

		String CODE = UUID.randomUUID().toString();

		SettingsSavedEvent settingsSavedEvent = SettingsDataUtil.getSettingsSavedEvent(CODE);
		settingsSavedEvent.getSettings().setName(null);
		settingsSavedEvent.getSettings().setService(serviceName);
		settingsSavedEvent.getSettings().setUserId(otherUserId);
		settingsSavedEvent.setUserId(otherUserId);

		kafkaTemplate.send(settings_topic, settingsSavedEvent.getAggregateId(), settingsSavedEvent);

		when(settingsStateStore.get(settingsSavedEvent.getAggregateId())).thenReturn(settingsSavedEvent);

		PersistenceDTO persistenceDTO = SettingsDataUtil.getPersistenceDTO();
		persistenceDTO.setUserId(null);
		persistenceDTO.setSettingsId(settingsSavedEvent.getAggregateId());

		// @formatter:off
		
		String id = SettingsDataUtil.PREFIX + CODE;
		
		this.mockMvc
				.perform(put(SETTINGS_PATH + "/clone/" + id)
						.header("Authorization", "Bearer " + getTokenOAGUser())
						.content(mapper.writeValueAsString(persistenceDTO))
						.contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.body", notNullValue()))
				.andExpect(jsonPath("$.body.id", is(not(equalTo(id)))))
				.andExpect(jsonPath("$.body.service", is(serviceName)))
				.andExpect(jsonPath("$.body.userId", is(userId)))
				.andExpect(jsonPath("$.body.selection", notNullValue()))
				.andExpect(jsonPath("$.body.selection", hasSize(1)))
				.andExpect(jsonPath("$.body.selection", hasItem("1")));
		
		// @formatter:on

		SaveSettingsEvent expectedEvent = SettingsDataUtil.getSaveSettingsEvent(CODE);

		Event firstEvent = (Event) blockingQueue.poll(50, TimeUnit.SECONDS);

		checkCloneEvents(firstEvent, settingsSavedEvent, expectedEvent);

		Event secondEvent = (Event) blockingQueue.poll(50, TimeUnit.SECONDS);

		checkCloneEvents(secondEvent, settingsSavedEvent, expectedEvent);

		Event thirdEvent = (Event) blockingQueue.poll(50, TimeUnit.SECONDS);

		checkCloneEvents(thirdEvent, settingsSavedEvent, expectedEvent);

	}

	private void checkCloneEvents(Event event, SettingsSavedEvent settingsSavedEvent, SaveSettingsEvent expectedEvent) {

		assertNotNull(event);

		if ((event instanceof SaveSettingsEvent)
				&& (!event.getAggregateId().equals(settingsSavedEvent.getAggregateId()))) {

			logger.info("Comprobando evento SaveSettingsEvent por guardado del clonado");

			assertEquals(expectedEvent.getType(), event.getType());
			assertEquals(expectedEvent.getVersion(), event.getVersion());
			assertEquals(serviceName, ((SettingsEvent) event).getSettings().getService());
			assertEquals(userId, ((SettingsEvent) event).getSettings().getUserId());

		} else if ((event instanceof SaveSettingsEvent)
				&& (event.getAggregateId().equals(settingsSavedEvent.getAggregateId()))) {

			logger.info("Comprobando evento SaveSettingsEvent por actualización de fecha de acceso");
			assertEquals(expectedEvent.getType(), event.getType());
			assertEquals((Integer) 2, event.getVersion());
			assertEquals(serviceName, ((SettingsEvent) event).getSettings().getService());
			assertEquals(settingsSavedEvent.getUserId(), ((SettingsEvent) event).getSettings().getUserId());

		} else if (event instanceof UpdateSettingsAccessedDateEvent) {

			logger.info("Comprobando evento UpdateSettingsAccessedDateEvent por acceso a settings");

			assertEquals(settingsSavedEvent.getAggregateId(), event.getAggregateId());
			assertEquals(settingsSavedEvent.getUserId(), event.getUserId());
		}
	}

	@KafkaHandler
	public void select(SelectEvent selectEvent) {

		SelectConfirmedEvent selectConfirmedEvent = new SelectConfirmedEvent().buildFrom(selectEvent);

		ListenableFuture<SendResult<String, Event>> future = kafkaTemplate.send(settings_topic,
				selectEvent.getAggregateId(), selectConfirmedEvent);
		future.addCallback(new SendListener());

		blockingQueue.offer(selectEvent);
	}

	@KafkaHandler
	public void deselect(DeselectEvent deselectEvent) {

		DeselectConfirmedEvent selectConfirmedEvent = new DeselectConfirmedEvent().buildFrom(deselectEvent);

		ListenableFuture<SendResult<String, Event>> future = kafkaTemplate.send(settings_topic,
				deselectEvent.getAggregateId(), selectConfirmedEvent);
		future.addCallback(new SendListener());

		blockingQueue.offer(deselectEvent);
	}

	@KafkaHandler
	public void clear(ClearSelectionEvent clearSelectionEvent) {

		ClearSelectionConfirmedEvent clearSelectionConfirmedEvent = new ClearSelectionConfirmedEvent()
				.buildFrom(clearSelectionEvent);

		ListenableFuture<SendResult<String, Event>> future = kafkaTemplate.send(settings_topic,
				clearSelectionEvent.getAggregateId(), clearSelectionConfirmedEvent);
		future.addCallback(new SendListener());

		blockingQueue.offer(clearSelectionEvent);
	}

	@KafkaHandler
	public void save(SaveSettingsEvent saveSettingsEvent) {

		SaveSettingsConfirmedEvent saveSettingsConfirmedEvent = new SaveSettingsConfirmedEvent()
				.buildFrom(saveSettingsEvent);

		ListenableFuture<SendResult<String, Event>> future = kafkaTemplate.send(settings_topic,
				saveSettingsEvent.getAggregateId(), saveSettingsConfirmedEvent);
		future.addCallback(new SendListener());

		blockingQueue.offer(saveSettingsEvent);
	}

	@KafkaHandler
	public void delete(DeleteSettingsEvent deleteSettingsEvent) {

		DeleteSettingsConfirmedEvent deleteSettingsConfirmedEvent = new DeleteSettingsConfirmedEvent()
				.buildFrom(deleteSettingsEvent);

		ListenableFuture<SendResult<String, Event>> future = kafkaTemplate.send(settings_topic,
				deleteSettingsEvent.getAggregateId(), deleteSettingsConfirmedEvent);
		future.addCallback(new SendListener());

		blockingQueue.offer(deleteSettingsEvent);
	}

	@KafkaHandler
	private void listen(UpdateSettingsAccessedDateEvent updateSettingsAccessedDateEvent) {

		blockingQueue.offer(updateSettingsAccessedDateEvent);
	}

	@KafkaHandler(isDefault = true)
	public void defaultEvent(Object def) {

	}
}
