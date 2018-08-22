package es.redmic.commandslib.streaming.streams;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;

import es.redmic.brokerlib.alert.AlertService;
import es.redmic.brokerlib.avro.common.Event;
import es.redmic.commandslib.streaming.common.BaseStreams;
import es.redmic.commandslib.streaming.common.StreamConfig;
import es.redmic.commandslib.streaming.common.StreamUtils;

public abstract class EventSourcingStreams extends BaseStreams {

	protected StreamsBuilder builder = new StreamsBuilder();

	public EventSourcingStreams(StreamConfig config, AlertService alertService) {
		super(config, alertService);
	}

	@Override
	protected KafkaStreams processStreams() {

		KStream<String, Event> events = builder.stream(topic);

		// Create Success
		processCreatedStream(events);

		// Update Success
		processUpdatedStream(events);

		// Failed change
		processFailedChangeStream(events);

		// PostUpdate
		processPostUpdateStream(events);

		return new KafkaStreams(builder.build(),
				StreamUtils.baseStreamsConfig(bootstrapServers, stateStoreDir, serviceId, schemaRegistry));
	}

	protected abstract void processCreatedStream(KStream<String, Event> events);

	protected abstract void processUpdatedStream(KStream<String, Event> events);

	protected abstract void processFailedChangeStream(KStream<String, Event> events);

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
}
