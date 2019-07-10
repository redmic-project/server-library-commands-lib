package es.redmic.commandslib.usersettings.selection;

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
import es.redmic.commandslib.usersettings.aggregate.SelectionAggregate;
import es.redmic.commandslib.usersettings.statestore.SettingsStateStore;
import es.redmic.usersettingslib.events.clear.ClearCancelledEvent;
import es.redmic.usersettingslib.events.clear.ClearEvent;
import es.redmic.usersettingslib.events.clear.ClearedEvent;
import es.redmic.usersettingslib.events.common.SelectionCancelledEvent;
import es.redmic.usersettingslib.events.common.SelectionEvent;
import es.redmic.usersettingslib.events.deselect.DeselectCancelledEvent;
import es.redmic.usersettingslib.events.deselect.DeselectEvent;
import es.redmic.usersettingslib.events.deselect.DeselectedEvent;
import es.redmic.usersettingslib.events.select.SelectCancelledEvent;
import es.redmic.usersettingslib.events.select.SelectEvent;
import es.redmic.usersettingslib.events.select.SelectedEvent;

@RunWith(MockitoJUnitRunner.class)
public class ApplyEventTest {

	private final String code = UUID.randomUUID().toString();

	SettingsStateStore settingsStateStore;

	SelectionAggregate agg;

	@Before
	public void setUp() {

		settingsStateStore = Mockito.mock(SettingsStateStore.class);

		agg = new SelectionAggregate(settingsStateStore);
	}

	@Test
	public void applySelectedEvent_ChangeAggregateState_IfProcessIsOk() {

		SelectedEvent evt = SettingsDataUtil.getSelectedEvent(code);

		agg.apply(evt);

		checkSelectedDeselectedOrClearedState(evt);
	}

	@Test
	public void applyDeselectedEvent_ChangeAggregateState_IfProcessIsOk() {

		DeselectedEvent evt = SettingsDataUtil.getDeselectedEvent(code);

		agg.apply(evt);

		checkSelectedDeselectedOrClearedState(evt);
	}

	@Test
	public void applyClearedEvent_ChangeAggregateState_IfProcessIsOk() {

		ClearedEvent evt = SettingsDataUtil.getClearedEvent(code);

		agg.apply(evt);

		checkSelectedDeselectedOrClearedState(evt);
	}

	@Test
	public void applySelectCancelledEvent_ChangeAggrefateState_IfProcessIsOk() {

		SelectCancelledEvent evt = SettingsDataUtil.getSelectCancelledEvent(code);

		agg.apply(evt);

		checkCancelledState(evt);
	}

	@Test
	public void applyDeselectCancelledEvent_ChangeAggrefateState_IfProcessIsOk() {

		DeselectCancelledEvent evt = SettingsDataUtil.getDeselectCancelledEvent(code);

		agg.apply(evt);

		checkCancelledState(evt);
	}

	@Test
	public void applyClearCancelledEvent_ChangeAggrefateState_IfProcessIsOk() {

		ClearCancelledEvent evt = SettingsDataUtil.getClearCancelledEvent(code);

		agg.apply(evt);

		checkCancelledState(evt);
	}

	@Test
	public void loadFromHistory_ChangeAggregateStateToSelected_IfEventIsSelected() {

		SelectedEvent evt = SettingsDataUtil.getSelectedEvent(code);

		agg.loadFromHistory(evt);

		checkSelectedDeselectedOrClearedState(evt);
	}

	@Test(expected = ItemLockedException.class)
	public void loadFromHistory_ThrowItemLockedException_IfEventIsSelect() {

		SelectEvent evt = SettingsDataUtil.getSelectEvent(code);

		agg.loadFromHistory(evt);
	}

	@Test
	public void loadFromHistory_ChangeAggregateStateToDeselected_IfEventIsDeselected() {

		DeselectedEvent evt = SettingsDataUtil.getDeselectedEvent(code);

		agg.loadFromHistory(evt);

		checkSelectedDeselectedOrClearedState(evt);
	}

	@Test(expected = ItemLockedException.class)
	public void loadFromHistory_ThrowItemLockedException_IfEventIsDeselect() {

		DeselectEvent evt = SettingsDataUtil.getDeselectEvent(code);

		agg.loadFromHistory(evt);
	}

	@Test
	public void loadFromHistory_ChangeAggregateStateToCleared_IfEventIsCleared() {

		ClearedEvent evt = SettingsDataUtil.getClearedEvent(code);

		agg.loadFromHistory(evt);

		checkSelectedDeselectedOrClearedState(evt);
	}

	@Test(expected = ItemLockedException.class)
	public void loadFromHistory_ThrowItemLockedException_IfEventIsClear() {

		ClearEvent evt = SettingsDataUtil.getClearEvent(code);

		agg.loadFromHistory(evt);
	}

	@Test
	public void loadFromHistory_ChangeAggregateStateToSelectCancelled_IfLastEventIsSelectCancelledEvent() {

		List<Event> history = new ArrayList<>();

		history.add(SettingsDataUtil.getSelectedEvent(code));

		history.add(SettingsDataUtil.getSelectCancelledEvent(code));

		agg.loadFromHistory(history);

		checkCancelledState((SelectionCancelledEvent) history.get(1));
	}

	@Test
	public void loadFromHistory_ChangeAggregateStateToDeselectCancelled_IfLastEventIsDeselectCancelledEvent() {

		List<Event> history = new ArrayList<>();

		history.add(SettingsDataUtil.getSelectedEvent(code));

		history.add(SettingsDataUtil.getDeselectCancelledEvent(code));

		agg.loadFromHistory(history);

		checkCancelledState((SelectionCancelledEvent) history.get(1));
	}

	@Test
	public void loadFromHistory_ChangeAggregateStateToClearCancelled_IfLastEventIsClearCancelledEvent() {

		List<Event> history = new ArrayList<>();

		history.add(SettingsDataUtil.getSelectedEvent(code));

		history.add(SettingsDataUtil.getClearCancelledEvent(code));

		agg.loadFromHistory(history);

		checkCancelledState((SelectionCancelledEvent) history.get(1));
	}

	private void checkCancelledState(SelectionCancelledEvent evt) {

		assertEquals(agg.getVersion(), evt.getVersion());
		assertEquals(agg.getAggregateId(), evt.getAggregateId());
		assertEquals(agg.getSelection(), evt.getSelection());
		assertFalse(agg.isDeleted());
	}

	private void checkSelectedDeselectedOrClearedState(SelectionEvent evt) {

		assertEquals(agg.getVersion(), evt.getVersion());
		assertEquals(agg.getAggregateId(), evt.getAggregateId());
		assertEquals(agg.getSelection(), evt.getSelection());
		assertFalse(agg.isDeleted());
	}
}
