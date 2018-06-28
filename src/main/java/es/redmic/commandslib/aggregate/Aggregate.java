package es.redmic.commandslib.aggregate;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.avro.common.SimpleEvent;

public abstract class Aggregate {

	protected boolean deleted = false;

	private String aggregateId;

	private Integer version;

	protected static Logger logger = LogManager.getLogger();

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

	protected void _apply(SimpleEvent event) {
		setVersion(event.getVersion());
		setAggregateId(event.getAggregateId());
	}

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
}
