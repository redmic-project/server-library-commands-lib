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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.commandslib.exceptions.ItemLockedException;
import es.redmic.commandslib.usersettings.SettingsDataUtil;
import es.redmic.commandslib.usersettings.aggregate.PersistenceAggregate;
import es.redmic.commandslib.usersettings.statestore.SettingsStateStore;
import es.redmic.usersettingslib.events.common.PersistenceCancelledEvent;
import es.redmic.usersettingslib.events.common.PersistenceEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsCancelledEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsEvent;
import es.redmic.usersettingslib.events.delete.SettingsDeletedEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsCancelledEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsEvent;
import es.redmic.usersettingslib.events.save.SettingsSavedEvent;

@RunWith(MockitoJUnitRunner.class)
public class ApplyEventTest {

	private final String code = UUID.randomUUID().toString();

	SettingsStateStore settingsStateStore;

	PersistenceAggregate agg;

	@Before
	public void setUp() {

		settingsStateStore = Mockito.mock(SettingsStateStore.class);

		agg = new PersistenceAggregate(settingsStateStore);
	}

	@Test
	public void applySettingsSavedEvent_ChangeAggregateState_IfProcessIsOk() {

		SettingsSavedEvent evt = SettingsDataUtil.getSettingsSavedEvent(code);

		agg.apply(evt);

		checkSavedState(evt);
	}

	@Test
	public void applySettingsDeletedEvent_ChangeAggregateState_IfProcessIsOk() {

		SettingsDeletedEvent evt = SettingsDataUtil.getSettingsDeletedEvent(code);

		agg.apply(evt);

		checkDeletedState(evt);
	}

	@Test
	public void applySaveSettingsCancelledEvent_ChangeAggregateState_IfProcessIsOk() {

		SaveSettingsCancelledEvent evt = SettingsDataUtil.getSaveSettingsCancelledEvent(code);

		agg.apply(evt);

		checkCancelledState(evt);
	}

	@Test
	public void applyDeleteSettingsCancelledEvent_ChangeAggregateState_IfProcessIsOk() {

		DeleteSettingsCancelledEvent evt = SettingsDataUtil.getDeleteSettingsCancelledEvent(code);

		agg.apply(evt);

		checkCancelledState(evt);
	}

	@Test
	public void loadFromHistory_ChangeAggregateStateToSaved_IfEventIsSaved() {

		SettingsSavedEvent evt = SettingsDataUtil.getSettingsSavedEvent(code);

		agg.loadFromHistory(evt);

		checkSavedState(evt);
	}

	@Test(expected = ItemLockedException.class)
	public void loadFromHistory_ThrowItemLockedException_IfEventIsCreate() {

		SaveSettingsEvent evt = SettingsDataUtil.getSaveSettingsEvent(code);

		agg.loadFromHistory(evt);
	}

	@Test
	public void loadFromHistory_ChangeAggregateStateToDeleted_IfEventIsDeleted() {

		SettingsDeletedEvent evt = SettingsDataUtil.getSettingsDeletedEvent(code);

		agg.loadFromHistory(evt);

		checkDeletedState(evt);
	}

	@Test(expected = ItemLockedException.class)
	public void loadFromHistory_ThrowItemLockedException_IfEventIsUpdate() {

		DeleteSettingsEvent evt = SettingsDataUtil.getDeleteSettingsEvent(code);

		agg.loadFromHistory(evt);
	}

	@Test
	public void loadFromHistory_ChangeAggregateStateToSaveSettingsCancelled_IfLastEventIsSaveSettingsCancelledEvent() {

		List<Event> history = new ArrayList<>();

		history.add(SettingsDataUtil.getSettingsSavedEvent(code));

		history.add(SettingsDataUtil.getSaveSettingsCancelledEvent(code));

		agg.loadFromHistory(history);

		checkCancelledState((PersistenceCancelledEvent) history.get(1));
	}

	@Test
	public void loadFromHistory_ChangeAggregateStateToDeleteCancelled_IfLastEventIsDeleteCancelled() {

		List<Event> history = new ArrayList<>();

		history.add(SettingsDataUtil.getSettingsSavedEvent(code));

		history.add(SettingsDataUtil.getDeleteSettingsCancelledEvent(code));

		agg.loadFromHistory(history);

		checkCancelledState((PersistenceCancelledEvent) history.get(1));
	}

	private void checkCancelledState(PersistenceCancelledEvent evt) {

		assertEquals(agg.getVersion(), evt.getVersion());
		assertEquals(agg.getAggregateId(), evt.getAggregateId());
		assertEquals(agg.getPersistence(), evt.getPersistence());
		assertFalse(agg.isDeleted());
	}

	private void checkSavedState(PersistenceEvent evt) {

		assertEquals(agg.getVersion(), evt.getVersion());
		assertEquals(agg.getAggregateId(), evt.getAggregateId());
		assertEquals(agg.getPersistence(), evt.getPersistence());
		assertFalse(agg.isDeleted());
	}

	private void checkDeletedState(SettingsDeletedEvent evt) {

		assertEquals(agg.getVersion(), evt.getVersion());
		assertEquals(agg.getAggregateId(), evt.getAggregateId());
		assertTrue(agg.isDeleted());
	}
}
