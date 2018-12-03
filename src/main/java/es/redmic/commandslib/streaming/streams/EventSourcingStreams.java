package es.redmic.commandslib.streaming.streams;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;

import es.redmic.brokerlib.alert.AlertService;
import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.avro.common.EventTypes;
import es.redmic.commandslib.streaming.common.BaseStreams;
import es.redmic.commandslib.streaming.common.StreamConfig;
import es.redmic.commandslib.streaming.common.StreamUtils;

public abstract class EventSourcingStreams extends BaseStreams {

	protected StreamsBuilder builder = new StreamsBuilder();

	protected String snapshotTopicSuffix = "-snapshot";

	protected String snapshotTopic;

	public EventSourcingStreams(StreamConfig config, AlertService alertService) {
		super(config, alertService);
		snapshotTopic = topic + snapshotTopicSuffix;
	}

	@Override
	protected KafkaStreams processStreams() {

		createExtraStreams();

		KStream<String, Event> events = builder.stream(topic);

		KStream<String, Event> snapshotEvents = builder.stream(snapshotTopic);

		// Reenvia eventos snapshot al topic correspondiente
		forwardSnapshotEvents(events);

		// Realiza el enriquecimiento del item antes de crear
		processEnrichCreateSteam(events);

		// Create Success
		processCreateSuccessStream(events);

		// Realiza el enriquecimiento del item antes de modificar
		processEnrichUpdateSteam(events);

		// Update Success
		processUpdateSuccessStream(events);

		// Comprueba si el elemento está referenciado para cancelar el borrado
		processDeleteStream(events);

		// Failed change
		processFailedChangeStream(events, snapshotEvents);

		// PostUpdate
		processPostUpdateStream(events);

		// extra process
		processExtraStreams(events);

		return new KafkaStreams(builder.build(),
				StreamUtils.baseStreamsConfig(bootstrapServers, stateStoreDir, serviceId, schemaRegistry));
	}

	protected abstract void forwardSnapshotEvents(KStream<String, Event> events);

	/*
	 * Función para crear streams extra que sean necesarios y específicos de cada
	 * tipo
	 */
	protected abstract void createExtraStreams();

	/*
	 * Función que a partir de los eventos de tipo CreateEnrich y globalKTable de
	 * las relaciones, enriquece el item antes de mandarlo a crear
	 * 
	 */

	protected abstract void processEnrichCreateSteam(KStream<String, Event> events);

	/*
	 * Función que a partir de los eventos de crear y confirmación de la vista,
	 * envía evento creado
	 */

	protected void processCreateSuccessStream(KStream<String, Event> events) {

		// Stream filtrado por eventos de confirmación al crear
		KStream<String, Event> createConfirmedEvents = events
				.filter((id, event) -> (EventTypes.CREATE_CONFIRMED.equals(event.getType())));

		// Stream filtrado por eventos de petición de crear
		KStream<String, Event> createRequestEvents = events
				.filter((id, event) -> (EventTypes.CREATE.equals(event.getType())));

		// Join por id, mandando a kafka el evento de éxito
		createConfirmedEvents.join(createRequestEvents,
				(confirmedEvent, requestEvent) -> getCreatedEvent(confirmedEvent, requestEvent),
				JoinWindows.of(windowsTime)).filter((k, v) -> (v != null)).to(topic);
	}

	/*
	 * Función que a partir de los eventos de tipo UpdateEnrich y globalKTable de
	 * las relaciones, enriquece el item antes de mandarlo a modificar
	 * 
	 */

	protected abstract void processEnrichUpdateSteam(KStream<String, Event> events);

	/*
	 * Función que a partir del evento de confirmación de la vista y del evento
	 * create (petición de creación), si todo es correcto, genera evento created
	 */

	protected abstract Event getCreatedEvent(Event confirmedEvent, Event requestEvent);

	/*
	 * Función que a partir del evento modificar y la confirmación de la vista,
	 * envía evento modificado. Además a partir del flujo de eventos de confirmación
	 * de la vista, manda a procesar las ediciones parciales
	 */

	protected void processUpdateSuccessStream(KStream<String, Event> events) {

		// Stream filtrado por eventos de confirmación al modificar
		KStream<String, Event> updateConfirmedEvents = events
				.filter((id, event) -> (EventTypes.UPDATE_CONFIRMED.equals(event.getType())));

		// Stream filtrado por eventos de petición de modificar
		KStream<String, Event> updateRequestEvents = events
				.filter((id, event) -> (EventTypes.UPDATE.equals(event.getType())));

		// Join por id, mandando a kafka el evento de éxito
		updateConfirmedEvents.join(updateRequestEvents,
				(confirmedEvent, requestEvent) -> getUpdatedEvent(confirmedEvent, requestEvent),
				JoinWindows.of(windowsTime)).filter((k, v) -> (v != null)).to(topic);

		processPartialUpdatedStream(events, updateConfirmedEvents);
	}

	/*
	 * Función que a partir del evento de confirmación de la vista y del evento
	 * update (petición de modificación), si todo es correcto, genera evento updated
	 */

	protected abstract Event getUpdatedEvent(Event confirmedEvent, Event requestEvent);

	/*
	 * Procesa peticiones de borrado para comprobar si está referenciado
	 */

	protected abstract void processDeleteStream(KStream<String, Event> events);

	/*
	 * Función que a partir del último evento correcto + el evento de edición
	 * parcial + la confirmación de la vista, envía evento modificado.
	 */

	protected abstract void processPartialUpdatedStream(KStream<String, Event> vesselEvents,
			KStream<String, Event> updateConfirmedEvents);

	/*
	 * Función que procesa los eventos fallidos
	 */
	protected void processFailedChangeStream(KStream<String, Event> events, KStream<String, Event> snapshotEvents) {

		processUpdateFailedStream(events, snapshotEvents);

		processDeleteFailedStream(events, snapshotEvents);
	}

	/*
	 * Función que a partir del último evento correcto y el evento fallido al
	 * editar, envía evento de cancelación
	 */

	protected void processUpdateFailedStream(KStream<String, Event> events, KStream<String, Event> successEvents) {

		// Stream filtrado por eventos de fallo al modificar
		KStream<String, Event> failedEvents = events
				.filter((id, event) -> (EventTypes.UPDATE_FAILED.equals(event.getType())));

		KTable<String, Event> successEventsTable = successEvents.groupByKey().reduce((aggValue, newValue) -> newValue);

		// Join por id, mandando a kafka el evento de compensación
		failedEvents
				.join(successEventsTable,
						(failedEvent, lastSuccessEvent) -> getUpdateCancelledEvent(failedEvent, lastSuccessEvent))
				.to(topic);
	}

	/*
	 * Función que a partir del evento fallido y el último evento correcto, genera
	 * evento UpdateCancelled
	 */

	protected abstract Event getUpdateCancelledEvent(Event failedEvent, Event lastSuccessEvent);

	/*
	 * Función que a partir del último evento correcto y el evento fallido al
	 * eliminar, envía evento de cancelación
	 */

	protected void processDeleteFailedStream(KStream<String, Event> events, KStream<String, Event> successEvents) {

		// Stream filtrado por eventos de fallo al borrar
		KStream<String, Event> failedEvents = events
				.filter((id, event) -> (EventTypes.DELETE_FAILED.equals(event.getType())));

		KTable<String, Event> successEventsTable = successEvents.groupByKey().reduce((aggValue, newValue) -> newValue);

		// Join por id, mandando a kafka el evento de compensación
		failedEvents
				.join(successEventsTable,
						(failedEvent, lastSuccessEvent) -> getDeleteCancelledEvent(failedEvent, lastSuccessEvent))
				.to(topic);
	}

	/*
	 * Función que a partir del evento fallido y el último evento correcto, genera
	 * evento DeleteFailed
	 */

	protected abstract Event getDeleteCancelledEvent(Event failedEvent, Event lastSuccessEvent);

	/*
	 * Función para procesar modificaciones de referencias
	 */

	protected abstract void processPostUpdateStream(KStream<String, Event> events);

	protected boolean isSameSession(Event a, Event b) {

		if (!(a.getSessionId().equals(b.getSessionId()))) {
			String message = "Recibido evento de petición con id de sessión diferente al evento de confirmación para item "
					+ a.getAggregateId();
			logger.error(message);
			alertService.errorAlert(a.getAggregateId(), message);
			return false;
		}
		return true;
	}

	@Override
	protected void postProcessStreams() {
	}

	/*
	 * Función para procesar eventos a partir del stream principal y que dependerá
	 * del servicio que lo implemente
	 */
	protected abstract void processExtraStreams(KStream<String, Event> events);
}
