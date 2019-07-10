package es.redmic.commandslib.usersettings.persistence;

import static org.junit.Assert.assertEquals;
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
import es.redmic.commandslib.usersettings.SettingsDataUtil;
import es.redmic.commandslib.usersettings.aggregate.PersistenceAggregate;
import es.redmic.commandslib.usersettings.commands.DeleteSettingsCommand;
import es.redmic.commandslib.usersettings.commands.SaveSettingsCommand;
import es.redmic.commandslib.usersettings.commands.UpdateSettingsCommand;
import es.redmic.commandslib.usersettings.statestore.SettingsStateStore;
import es.redmic.exception.data.ItemNotFoundException;
import es.redmic.usersettingslib.dto.PersistenceDTO;
import es.redmic.usersettingslib.events.SettingsEventTypes;
import es.redmic.usersettingslib.events.delete.CheckDeleteSettingsEvent;
import es.redmic.usersettingslib.events.save.SaveSettingsEvent;

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

		SaveSettingsEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getPersistence());
		assertEquals(evt.getPersistence(), persistence);
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), persistence.getId());
		assertEquals(evt.getType(), SettingsEventTypes.SAVE);
		assertTrue(evt.getVersion().equals(1));
	}

	@Test
	public void processUpdateSettingsCommand_ReturnSaveEvent_IfProcessIsOk() {

		when(settingsStateStore.get(any())).thenReturn(SettingsDataUtil.getSettingsSavedEvent(code));

		PersistenceDTO persistence = SettingsDataUtil.getPersistenceDTO(code);

		UpdateSettingsCommand command = new UpdateSettingsCommand(persistence);

		SaveSettingsEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getPersistence());
		assertEquals(evt.getPersistence(), persistence);
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), persistence.getId());
		assertEquals(evt.getType(), SettingsEventTypes.SAVE);
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
