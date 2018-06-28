package es.redmic.commandslib.streams;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.commandslib.statestore.StreamConfig;
import es.redmic.commandslib.statestore.StreamUtils;

public abstract class EventStreams extends BaseStreams {

	protected StreamsBuilder builder = new StreamsBuilder();

	public EventStreams(StreamConfig config) {
		super(config);
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
			logger.error("Se esperaba eventos con el mismo id de sesión");
			return false;
		}
		return true;
	}

	@Override
	protected void postProcessStreams() {
	}
}
