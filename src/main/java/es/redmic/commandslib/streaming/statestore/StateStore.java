package es.redmic.commandslib.streaming.statestore;

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
