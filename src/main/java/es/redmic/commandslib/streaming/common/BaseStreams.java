package es.redmic.commandslib.streaming.common;

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
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.redmic.brokerlib.alert.AlertService;
import es.redmic.brokerlib.avro.common.Event;

public abstract class BaseStreams {

	protected static Logger logger = LogManager.getLogger();

	protected String schemaRegistry;

	protected String bootstrapServers;

	protected String topic;

	protected String stateStoreDir;

	protected String serviceId;

	protected Long windowsTime;

	protected final String SCHEMA_REGISTRY_URL_PROPERTY = "schema.registry.url";

	protected KafkaStreams streams;

	protected AlertService alertService;

	public BaseStreams(StreamConfig config, AlertService alertService) {
		this.topic = config.getTopic();
		this.stateStoreDir = config.getStateStoreDir();
		this.serviceId = config.getServiceId();
		this.bootstrapServers = config.getBootstrapServers();
		this.schemaRegistry = config.getSchemaRegistry();
		this.windowsTime = config.getWindowsTime();
		this.alertService = alertService;
	}

	protected void init() {

		streams = processStreams();

		streams.setUncaughtExceptionHandler(
				(Thread thread, Throwable throwable) -> uncaughtException(thread, throwable));

		streams.start();

		postProcessStreams();

		addShutdownHookAndBlock();
	}

	protected abstract KafkaStreams processStreams();

	protected abstract void postProcessStreams();

	private void addShutdownHookAndBlock() {

		Thread.currentThread().setUncaughtExceptionHandler((t, e) -> uncaughtException(t, e));

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				logger.info("Parando stream por señal SIGTERM");
				streams.close();
			}
		}));
	}

	private void uncaughtException(Thread thread, Throwable throwable) {

		String msg = "Error no conocido en kafka stream. El stream dejará de funcionar "
				+ throwable.getLocalizedMessage();
		logger.error(msg);
		throwable.printStackTrace();
		alertService.errorAlert(this.topic, msg);
		streams.close();
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

	protected boolean isSameSession(Event a, Event b) {

		if (!(a.getSessionId().equals(b.getSessionId()))) {
			String message = "Evento de petición " + b.getType() + " con id de sesión " + b.getSessionId()
					+ ", el cual es diferente al evento de confirmación " + a.getType() + " con id de sesión "
					+ a.getSessionId() + " para item " + b.getAggregateId() + "|" + b.getDate() + " ("
					+ a.getAggregateId() + "|" + a.getDate() + ")";
			logger.error(message);
			alertService.errorAlert(a.getAggregateId(), message);
			return false;
		}
		return true;
	}
}
