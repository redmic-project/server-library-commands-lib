package es.redmic.commandslib.usersettings.persistence;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import es.redmic.commandslib.exceptions.ItemLockedException;
import es.redmic.commandslib.usersettings.aggregate.PersistenceAggregate;
import es.redmic.commandslib.usersettings.commands.CloneSettingsCommand;
import es.redmic.commandslib.usersettings.commands.DeleteSettingsCommand;
import es.redmic.commandslib.usersettings.commands.SaveSettingsCommand;
import es.redmic.commandslib.usersettings.commands.UpdateSettingsAccessedDateCommand;
import es.redmic.commandslib.usersettings.commands.UpdateSettingsCommand;
import es.redmic.commandslib.usersettings.statestore.SettingsStateStore;
import es.redmic.exception.data.ItemNotFoundException;
import es.redmic.usersettingslib.dto.PersistenceDTO;
import es.redmic.usersettingslib.events.SettingsEventTypes;
import es.redmic.usersettingslib.events.clone.CloneSettingsEvent;
import es.redmic.usersettingslib.events.delete.CheckDeleteSettingsEvent;
import es.redmic.usersettingslib.events.save.PartialSaveSettingsEvent;
import es.redmic.usersettingslib.events.update.UpdateSettingsAccessedDateEvent;
import es.redmic.usersettingslib.unit.utils.SettingsDataUtil;

@RunWith(MockitoJUnitRunner.class)
public class ProcessEventTest {

	private final String code = UUID.randomUUID().toString();

	SettingsStateStore settingsStateStore;

	PersistenceAggregate agg;

	@Before
	public void setUp() {

		settingsStateStore = Mockito.mock(SettingsStateStore.class);

		agg = new PersistenceAggregate(settingsStateStore);
	}

	@Test
	public void processSaveSettingsCommand_ReturnSaveEvent_IfProcessIsOk() {

		when(settingsStateStore.get(any())).thenReturn(null);

		PersistenceDTO persistence = SettingsDataUtil.getPersistenceDTO(code);

		SaveSettingsCommand command = new SaveSettingsCommand(persistence);

		PartialSaveSettingsEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getPersistence());
		assertEquals(evt.getPersistence(), persistence);
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), persistence.getId());
		assertEquals(evt.getType(), SettingsEventTypes.PARTIAL_SAVE);
		assertTrue(evt.getVersion().equals(1));
	}

	@Test
	public void processUpdateSettingsCommand_ReturnSaveEvent_IfProcessIsOk() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSettingsSavedEvent(code));

		PersistenceDTO persistence = SettingsDataUtil.getPersistenceDTO(code);

		UpdateSettingsCommand command = new UpdateSettingsCommand(persistence);

		PartialSaveSettingsEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getPersistence());
		assertEquals(evt.getPersistence(), persistence);
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), persistence.getId());
		assertEquals(evt.getType(), SettingsEventTypes.PARTIAL_SAVE);
		assertTrue(evt.getVersion().equals(2));
	}

	// Editar un elemento ya borrado
	@Test(expected = ItemNotFoundException.class)
	public void processUpdateSettingsCommand_ThrowItemNotFoundException_IfItemIsDeleted() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSettingsDeletedEvent(code));

		agg.process(new UpdateSettingsCommand(SettingsDataUtil.getPersistenceDTO(code)));
	}

	// Editar un elemento bloqueado
	@Test(expected = ItemLockedException.class)
	public void processUpdateCategoryCommand_ThrowItemLockedException_IfItemIsLocked() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSaveSettingsEvent(code));

		agg.process(new UpdateSettingsCommand(SettingsDataUtil.getPersistenceDTO(code)));
	}

	@Test
	public void processDeleteSettingsCommand_ReturnCheckDeleteSettingsEvent_IfProcessIsOk() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSettingsSavedEvent(code));

		PersistenceDTO persistence = SettingsDataUtil.getPersistenceDTO(code);

		DeleteSettingsCommand command = new DeleteSettingsCommand(persistence.getId());

		CheckDeleteSettingsEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), persistence.getId());
		assertEquals(evt.getType(), SettingsEventTypes.CHECK_DELETE);
		assertTrue(evt.getVersion().equals(2));
	}

	@Test
	public void processCloneSettingsCommand_ReturnCloneSettingsEvent_IfProcessIsOk() {

		PersistenceDTO persistence = SettingsDataUtil.getPersistenceDTO(code);

		String serviceName = "/atlas/commands/layer/settings";
		CloneSettingsCommand command = new CloneSettingsCommand(persistence.getId(), serviceName);

		CloneSettingsEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getId());
		assertNotNull(evt.getAggregateId());
		assertNotEquals(evt.getAggregateId(), persistence.getId());
		assertEquals(evt.getType(), SettingsEventTypes.CLONE);
		assertTrue(evt.getVersion().equals(1));
		assertEquals(evt.getAggregateId(), evt.getPersistence().getId());
		assertEquals(evt.getPersistence().getInserted(), evt.getPersistence().getUpdated());
		assertEquals(evt.getPersistence().getInserted(), evt.getPersistence().getAccessed());
		assertEquals(serviceName, evt.getPersistence().getService());
	}

	@Test
	public void processUpdateSettingsAccessedDateCommand_ReturnUpdateSettingsAccessedDateEvent_IfProcessIsOk() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSettingsSavedEvent(code));

		PersistenceDTO persistence = SettingsDataUtil.getPersistenceDTO(code);

		UpdateSettingsAccessedDateCommand command = new UpdateSettingsAccessedDateCommand(persistence.getId());

		UpdateSettingsAccessedDateEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getId());
		assertNotNull(evt.getAggregateId());
		assertEquals(evt.getAggregateId(), persistence.getId());
		assertEquals(evt.getType(), SettingsEventTypes.UPDATE_ACCESSED_DATE);
		assertTrue(evt.getVersion().equals(2));
		assertEquals(evt.getAggregateId(), persistence.getId());
	}

	// Borrar un elemento ya borrado
	@Test(expected = ItemNotFoundException.class)
	public void processDeleteSettingsCommand_ThrowItemNotFoundException_IfItemIsDeleted() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSettingsDeletedEvent(code));

		PersistenceDTO persistence = SettingsDataUtil.getPersistenceDTO(code);

		agg.process(new DeleteSettingsCommand(persistence.getId()));
	}

	// Borrar un elemento bloqueado
	@Test(expected = ItemLockedException.class)
	public void processDeleteSettingsCommand_ThrowItemLockedException_IfItemIsLocked() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSaveSettingsEvent(code));

		PersistenceDTO persistence = SettingsDataUtil.getPersistenceDTO(code);

		agg.process(new DeleteSettingsCommand(persistence.getId()));
	}
}
