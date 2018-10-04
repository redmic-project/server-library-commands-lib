package es.redmic.commandslib.streaming.common;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.redmic.brokerlib.alert.AlertService;

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
