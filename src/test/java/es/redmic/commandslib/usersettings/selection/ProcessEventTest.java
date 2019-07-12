package es.redmic.commandslib.usersettings.selection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

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

import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import es.redmic.commandslib.exceptions.HistoryNotFoundException;
import es.redmic.commandslib.exceptions.ItemLockedException;
import es.redmic.commandslib.usersettings.SettingsDataUtil;
import es.redmic.commandslib.usersettings.aggregate.SelectionAggregate;
import es.redmic.commandslib.usersettings.commands.ClearCommand;
import es.redmic.commandslib.usersettings.commands.DeselectCommand;
import es.redmic.commandslib.usersettings.commands.SelectCommand;
import es.redmic.commandslib.usersettings.statestore.SettingsStateStore;
import es.redmic.usersettingslib.dto.SelectionDTO;
import es.redmic.usersettingslib.events.SettingsEventTypes;
import es.redmic.usersettingslib.events.clearselection.PartialClearSelectionEvent;
import es.redmic.usersettingslib.events.deselect.PartialDeselectEvent;
import es.redmic.usersettingslib.events.select.PartialSelectEvent;

@RunWith(MockitoJUnitRunner.class)
public class ProcessEventTest {

	private final String code = UUID.randomUUID().toString();

	SettingsStateStore settingsStateStore;

	SelectionAggregate agg;

	@Before
	public void setUp() {

		settingsStateStore = Mockito.mock(SettingsStateStore.class);

		agg = new SelectionAggregate(settingsStateStore);
	}

	@Test
	public void processSelectCommand_ReturnSelectEvent_IfProcessIsOk() {

		when(settingsStateStore.get(any())).thenReturn(null);

		SelectionDTO selection = SettingsDataUtil.getSelectionDTO(code);

		SelectCommand command = new SelectCommand(selection);

		PartialSelectEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getSelection());
		assertEquals(evt.getSelection(), selection);
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), selection.getId());
		assertEquals(evt.getType(), SettingsEventTypes.PARTIAL_SELECT);
		assertTrue(evt.getVersion().equals(1));
	}

	@Test
	public void processDeselectCommand_ReturnDeselectEvent_IfProcessIsOk() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSelectedEvent(code));

		SelectionDTO selection = SettingsDataUtil.getSelectionDTO(code);

		DeselectCommand command = new DeselectCommand(selection);

		PartialDeselectEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getSelection());
		assertEquals(evt.getSelection(), selection);
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), selection.getId());
		assertEquals(evt.getType(), SettingsEventTypes.PARTIAL_DESELECT);
		assertTrue(evt.getVersion().equals(2));
	}

	@Test
	public void processClearCommand_ReturnClearEvent_IfProcessIsOk() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSelectedEvent(code));

		SelectionDTO selection = SettingsDataUtil.getSelectionDTO(code);

		ClearCommand command = new ClearCommand(selection);

		PartialClearSelectionEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getSelection());
		assertEquals(evt.getSelection(), selection);
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), selection.getId());
		assertEquals(evt.getType(), SettingsEventTypes.PARTIAL_CLEAR_SELECTION);
		assertTrue(evt.getVersion().equals(2));
	}

	// Seleccionar un item con una selección bloqueada
	@Test(expected = ItemLockedException.class)
	public void processSelectCommand_ThrowItemLockedException_IfItemIsLocked() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSelectEvent(code));

		agg.process(new SelectCommand(SettingsDataUtil.getSelectionDTO(code)));
	}

	// Deseleccionar un item con una selección que no existe
	@Test(expected = HistoryNotFoundException.class)
	public void processDeselectCommand_ThrowItemNotFoundException_IfItemNotExist() {

		when(settingsStateStore.get(any())).thenReturn(null);

		agg.process(new DeselectCommand(SettingsDataUtil.getSelectionDTO(code)));
	}

	// Seleccionar un item con una selección bloqueada
	@Test(expected = ItemLockedException.class)
	public void processDeselectCommand_ThrowItemLockedException_IfItemIsLocked() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSelectEvent(code));

		agg.process(new DeselectCommand(SettingsDataUtil.getSelectionDTO(code)));
	}

	// Deseleccionar un item con una selección que no existe
	@Test(expected = HistoryNotFoundException.class)
	public void processClearCommand_ThrowItemNotFoundException_IfItemNotExist() {

		when(settingsStateStore.get(any())).thenReturn(null);

		agg.process(new ClearCommand(SettingsDataUtil.getSelectionDTO(code)));
	}

	// Seleccionar un item con una selección bloqueada
	@Test(expected = ItemLockedException.class)
	public void processClearCommand_ThrowItemLockedException_IfItemIsLocked() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSelectEvent(code));

		agg.process(new ClearCommand(SettingsDataUtil.getSelectionDTO(code)));
	}
}
