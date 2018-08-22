package es.redmic.commandslib.streaming.statestore;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import es.redmic.brokerlib.alert.AlertService;
import es.redmic.brokerlib.avro.common.Event;
import es.redmic.commandslib.streaming.common.BaseStreams;
import es.redmic.commandslib.streaming.common.StreamConfig;
import es.redmic.commandslib.streaming.common.StreamUtils;

public abstract class StateStore extends BaseStreams {

	protected ReadOnlyKeyValueStore<String, Event> store;

	public StateStore(StreamConfig config, AlertService alertService) {
		super(config, alertService);
	}

	@Override
	protected KafkaStreams processStreams() {

		StreamsBuilder builder = new StreamsBuilder();

		builder.globalTable(topic, Materialized.as(topic));

		return new KafkaStreams(builder.build(),
				StreamUtils.baseStreamsConfig(bootstrapServers, stateStoreDir, serviceId, schemaRegistry));
	}

	@Override
	protected void postProcessStreams() {

		this.store = waitUntilStoreIsQueryable(topic, QueryableStoreTypes.<String, Event>keyValueStore(), streams);
	}
}
