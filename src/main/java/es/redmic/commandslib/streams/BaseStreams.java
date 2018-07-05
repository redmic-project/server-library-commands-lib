package es.redmic.commandslib.streams;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.redmic.commandslib.statestore.StreamConfig;

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

	public BaseStreams(StreamConfig config) {
		this.topic = config.getTopic();
		this.stateStoreDir = config.getStateStoreDir();
		this.serviceId = config.getServiceId();
		this.bootstrapServers = config.getBootstrapServers();
		this.schemaRegistry = config.getSchemaRegistry();
		this.windowsTime = config.getWindowsTime();
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
		// TODO: Mandar alerta
		logger.error("Error no conocido en kafka stream");
		throwable.printStackTrace();
		streams.close();
	}
}
