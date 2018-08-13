package es.redmic.commandslib.aggregate;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.avro.common.EventTypes;
import es.redmic.commandslib.exceptions.HistoryNotFoundException;
import es.redmic.commandslib.exceptions.ItemLockedException;
import es.redmic.exception.data.ItemNotFoundException;

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

		if (isLocked(eventType)) {
			logger.error("Intentando modificar un elemento bloqueado por una edición en curso, ", id);
			throw new ItemLockedException("id", id);
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

	protected void _loadFromHistory(Event history) {

		String eventType = history.getType();

		switch (eventType) {
		case "CREATE_CONFIRMED":
			logger.debug("Creación confirmada");
			apply(history);
			break;
		case "UPDATE_CONFIRMED":
			logger.debug("Modificación confirmada");
			apply(history);
			break;
		case "DELETE":
			logger.debug("En fase de borrado");
			apply(history);
			break;
		case "DELETE_CONFIRMED":
			logger.debug("Borrado confirmado");
			apply(history);
			break;
		// FAILED
		case "CREATE_FAILED":
		case "UPDATE_FAILED":
		case "DELETE_FAILED":
			logger.debug("Evento fallido");
			apply(history);
			break;
		default:
			logger.debug("Evento no manejado ", history.getType());
			break;
		}
	}

	protected void reset() {

		setVersion(null);
		setAggregateId(null);
	}

	public boolean isDeleted() {
		return deleted;
	}
}
