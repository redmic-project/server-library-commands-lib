package es.redmic.commandslib.aggregate;

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

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.avro.common.EventTypes;
import es.redmic.brokerlib.avro.fail.RollbackEvent;
import es.redmic.commandslib.exceptions.HistoryNotFoundException;
import es.redmic.commandslib.exceptions.ItemLockedException;
import es.redmic.exception.data.ItemNotFoundException;
import es.redmic.exception.settings.SettingsChangeForbiddenException;
import es.redmic.usersettingslib.events.SettingsEventTypes;

public abstract class Aggregate {

	protected boolean deleted = false;

	private String aggregateId;

	private Integer version;

	protected static Logger logger = LogManager.getLogger();

	/*
	 * Función que comprueba si el item ya existe
	 */
	protected boolean exist(String id) {

		if (id != null) {
			// comprueba que el id no exista
			Event state = getItemFromStateStore(id);

			if (state != null) {

				loadFromHistory(state);

				if (!isDeleted()) {
					return true;
				}
				reset();
			}
		}
		return false;
	}

	/*
	 * Función para obtener el último estado del
	 */
	protected Event getStateFromHistory(String id) {

		Event state = getItemFromStateStore(id);

		if (state == null) {
			logger.error("Intentando modificar(editar o eliminar) un elemento del cual no se tiene historial, ", id);
			throw new HistoryNotFoundException(EventTypes.UPDATE + " | " + EventTypes.DELETE, id);
		}

		return state;
	}

	protected void checkState(String id, String eventType) {

		if (this.deleted) {
			logger.error("Intentando modificar un elemento eliminado, ", id);
			throw new ItemNotFoundException("id", id);
		}
	}

	protected void check(Event event) {

		if (isLocked(event.getType()) && !event.getType().equals(EventTypes.DELETED)) {

			logger.error("Intentando modificar un elemento bloqueado por una edición en curso, ",
					event.getAggregateId());
			throw new ItemLockedException("id", event.getAggregateId());
		}
	}

	public String getAggregateId() {
		return aggregateId;
	}

	public void setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public void apply(Event event) {
		setVersion(event.getVersion());
		setAggregateId(event.getAggregateId());
	}

	/*
	 * Función que devuelve si el item específico está bloqueado
	 */
	protected abstract boolean isLocked(String eventType);

	/*
	 * Función que obtiene el item con id pasado en caso de existir.
	 */
	protected abstract Event getItemFromStateStore(String id);

	/*
	 * Función que a partir de todos los eventos generados sobre un item, aplica
	 * todos los cambios para restaurar el estado actual del item. Si queremos
	 * obtener todos los cambios debemos usar KStream
	 */
	public void loadFromHistory(List<Event> history) {

		for (Event evt : history) {
			loadFromHistory(evt);
		}
	}

	/*
	 * Función que a partir de un evento generado sobre un item, aplica dicho
	 * evento, seteando un estado válido.
	 */
	public abstract void loadFromHistory(Event event);

	protected void reset() {

		setVersion(null);
		setAggregateId(null);
	}

	public boolean isDeleted() {
		return deleted;
	}

	protected void authorshipCheck(String userId, String historicalEventUserId) {

		if (historicalEventUserId != null && !userId.equals(historicalEventUserId))
			throw new SettingsChangeForbiddenException();
	}

	public Event getRollbackEventFromBlockedEvent(String id, long timeoutMS) {

		Event blockedEvent = getItemFromStateStore(id);

		if (!SettingsEventTypes.isSnapshot(blockedEvent.getType())
				&& blockedEventRequiresRollback(blockedEvent, timeoutMS))
			return getRollbackEvent(blockedEvent);
		return null;
	}

	private boolean blockedEventRequiresRollback(Event event, long timeoutMS) {

		return event.getDate().plus(timeoutMS).isAfterNow();
	}

	public Event getRollbackEvent(Event sourceEvent) {

		return new RollbackEvent().buildFrom(sourceEvent);
	}
}
