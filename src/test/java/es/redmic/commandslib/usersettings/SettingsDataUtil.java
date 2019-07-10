package es.redmic.commandslib.usersettings;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;

import es.redmic.usersettingslib.dto.PersistenceDTO;
import es.redmic.usersettingslib.dto.SelectionDTO;
import es.redmic.usersettingslib.events.SettingsEventTypes;
import es.redmic.usersettingslib.events.clear.ClearCancelledEvent;
import es.redmic.usersettingslib.events.clear.ClearEvent;
import es.redmic.usersettingslib.events.clear.ClearedEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsCancelledEvent;
import es.redmic.usersettingslib.events.delete.DeleteSettingsEvent;
import es.redmic.usersettingslib.events.delete.SettingsDeletedEvent;
import es.redmic.usersettingslib.events.deselect.DeselectCancelledEvent;
import es.redmic.usersettingslib.events.deselect.DeselectEvent;
import es.redmic.usersettingslib.events.deselect.DeselectedEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsCancelledEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsEvent;
import es.redmic.usersettingslib.events.save.SettingsSavedEvent;
import es.redmic.usersettingslib.events.select.SelectCancelledEvent;
import es.redmic.usersettingslib.events.select.SelectEvent;
import es.redmic.usersettingslib.events.select.SelectedEvent;

public abstract class SettingsDataUtil {

	// @formatter:off
	public final static String PREFIX = "settings-",
			USER = "1";
	// @formatter:on

	// SELECT

	public static SelectEvent getSelectEvent(String code) {

		SelectEvent evt = new SelectEvent();
		evt.setAggregateId(PREFIX + code);
		evt.setType(SettingsEventTypes.SELECT);
		evt.setVersion(1);
		evt.setUserId(USER);
		evt.setSelection(getSelectionDTO(code));
		return evt;
	}

	public static SelectedEvent getSelectedEvent(String code) {

		SelectedEvent evt = new SelectedEvent().buildFrom(getSelectEvent(code));
		evt.setType(SettingsEventTypes.SELECTED);
		evt.setSelection(getSelectionDTO(code));
		return evt;
	}

	public static SelectCancelledEvent getSelectCancelledEvent(String code) {

		SelectCancelledEvent evt = new SelectCancelledEvent().buildFrom(getSelectEvent(code));
		evt.setType(SettingsEventTypes.SELECT_CANCELLED);
		evt.setSelection(getSelectionDTO(code));
		evt.setExceptionType("ItemNotFound");
		Map<String, String> arguments = new HashMap<String, String>();
		arguments.put("a", "b");
		evt.setArguments(arguments);
		return evt;
	}

	// DESELECT

	public static DeselectEvent getDeselectEvent(String code) {

		DeselectEvent evt = new DeselectEvent();
		evt.setAggregateId(PREFIX + code);
		evt.setType(SettingsEventTypes.SELECT);
		evt.setVersion(1);
		evt.setUserId(USER);
		evt.setSelection(getSelectionDTO(code));
		return evt;
	}

	public static DeselectedEvent getDeselectedEvent(String code) {

		DeselectedEvent evt = new DeselectedEvent().buildFrom(getDeselectEvent(code));
		evt.setType(SettingsEventTypes.DESELECTED);
		evt.setSelection(getSelectionDTO(code));
		return evt;
	}

	public static DeselectCancelledEvent getDeselectCancelledEvent(String code) {

		DeselectCancelledEvent evt = new DeselectCancelledEvent().buildFrom(getDeselectEvent(code));
		evt.setType(SettingsEventTypes.DESELECT_CANCELLED);
		evt.setSelection(getSelectionDTO(code));
		evt.setExceptionType("ItemNotFound");
		Map<String, String> arguments = new HashMap<String, String>();
		arguments.put("a", "b");
		evt.setArguments(arguments);
		return evt;
	}

	// CLEAR

	public static ClearEvent getClearEvent(String code) {

		ClearEvent evt = new ClearEvent();
		evt.setAggregateId(PREFIX + code);
		evt.setType(SettingsEventTypes.CLEAR);
		evt.setVersion(1);
		evt.setUserId(USER);
		evt.setSelection(getSelectionDTO(code));
		return evt;
	}

	public static ClearedEvent getClearedEvent(String code) {

		ClearedEvent evt = new ClearedEvent().buildFrom(getClearEvent(code));
		evt.setType(SettingsEventTypes.CLEARED);
		evt.setSelection(getSelectionDTO(code));
		return evt;
	}

	public static ClearCancelledEvent getClearCancelledEvent(String code) {

		ClearCancelledEvent evt = new ClearCancelledEvent().buildFrom(getClearEvent(code));
		evt.setType(SettingsEventTypes.CLEAR_CANCELLED);
		evt.setSelection(getSelectionDTO(code));
		evt.setExceptionType("ItemNotFound");
		Map<String, String> arguments = new HashMap<String, String>();
		arguments.put("a", "b");
		evt.setArguments(arguments);
		return evt;
	}

	// SAVE

	public static SaveSettingsEvent getSaveSettingsEvent(String code) {

		SaveSettingsEvent evt = new SaveSettingsEvent();
		evt.setAggregateId(PREFIX + code);
		evt.setType(SettingsEventTypes.SAVE);
		evt.setVersion(1);
		evt.setUserId(USER);
		evt.setPersistence(getPersistenceDTO(code));
		return evt;
	}

	public static SettingsSavedEvent getSettingsSavedEvent(String code) {

		SettingsSavedEvent evt = new SettingsSavedEvent().buildFrom(getSaveSettingsEvent(code));
		evt.setType(SettingsEventTypes.SAVED);
		evt.setPersistence(getPersistenceDTO(code));
		return evt;
	}

	public static SaveSettingsCancelledEvent getSaveSettingsCancelledEvent(String code) {

		SaveSettingsCancelledEvent evt = new SaveSettingsCancelledEvent().buildFrom(getSaveSettingsEvent(code));
		evt.setType(SettingsEventTypes.SAVE_CANCELLED);
		evt.setPersistence(getPersistenceDTO(code));
		evt.setExceptionType("ItemNotFound");
		Map<String, String> arguments = new HashMap<String, String>();
		arguments.put("a", "b");
		evt.setArguments(arguments);
		return evt;
	}

	// DELETE

	public static DeleteSettingsEvent getDeleteSettingsEvent(String code) {

		DeleteSettingsEvent evt = new DeleteSettingsEvent();
		evt.setAggregateId(PREFIX + code);
		evt.setType(SettingsEventTypes.DELETE);
		evt.setVersion(1);
		evt.setUserId(USER);
		return evt;
	}

	public static SettingsDeletedEvent getSettingsDeletedEvent(String code) {

		SettingsDeletedEvent evt = new SettingsDeletedEvent().buildFrom(getDeleteSettingsEvent(code));
		evt.setType(SettingsEventTypes.DELETED);
		return evt;
	}

	public static DeleteSettingsCancelledEvent getDeleteSettingsCancelledEvent(String code) {

		DeleteSettingsCancelledEvent evt = new DeleteSettingsCancelledEvent().buildFrom(getDeleteSettingsEvent(code));
		evt.setType(SettingsEventTypes.DELETE_CANCELLED);
		evt.setPersistence(getPersistenceDTO(code));
		evt.setExceptionType("ItemNotFound");
		Map<String, String> arguments = new HashMap<String, String>();
		arguments.put("a", "b");
		evt.setArguments(arguments);
		return evt;
	}

	//

	@SuppressWarnings("serial")
	public static SelectionDTO getSelectionDTO(String code) {

		SelectionDTO selection = new SelectionDTO();

		selection.setId(PREFIX + code);
		selection.setService("prueba");
		selection.setSelection(new ArrayList<String>() {
			{
				add("1");
			}
		});

		selection.setInserted(DateTime.now());
		selection.setUpdated(DateTime.now());
		selection.setAccessed(DateTime.now());
		return selection;
	}

	public static PersistenceDTO getPersistenceDTO(String code) {

		PersistenceDTO persistence = new PersistenceDTO();

		persistence.setId(PREFIX + code);
		persistence.setName("prueba");
		persistence.setUserId(USER);

		persistence.setService("prueba");

		persistence.setInserted(DateTime.now());
		persistence.setUpdated(DateTime.now());
		persistence.setAccessed(DateTime.now());
		return persistence;
	}
}
