package es.redmic.commandslib.statestore;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.QueryableStoreType;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import es.redmic.brokerlib.alert.AlertService;
import es.redmic.brokerlib.avro.common.Event;
import es.redmic.commandslib.streams.BaseStreams;

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

	/*
	 * En ocaciones el store se bloquea debido a operaciones de rebalanceo de kafka.
	 * Esta función permite esperar hasta que sea accesible.
	 */

	protected static <T> T waitUntilStoreIsQueryable(final String storeName,
			final QueryableStoreType<T> queryableStoreType, final KafkaStreams streams) {
		while (true) {
			try {
				return streams.store(storeName, queryableStoreType);
			} catch (InvalidStateStoreException ignored) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
		}
	}
}
