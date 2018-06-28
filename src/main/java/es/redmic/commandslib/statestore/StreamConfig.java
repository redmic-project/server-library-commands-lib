package es.redmic.commandslib.statestore;

import static org.junit.Assert.assertNotNull;

public class StreamConfig {

	private String schemaRegistry;

	private String bootstrapServers;

	private String topic;

	private String stateStoreDir;

	private String serviceId;

	public StreamConfig(Builder builder) {
		this.topic = builder.topic;
		this.stateStoreDir = builder.stateStoreDir;
		this.serviceId = builder.serviceId;
		this.bootstrapServers = builder.bootstrapServers;
		this.schemaRegistry = builder.schemaRegistry;
	}

	public static class Builder {

		private String bootstrapServers;

		private String schemaRegistry;

		private String topic;

		private String stateStoreDir;

		private String serviceId;

		public static Builder bootstrapServers(String bootstrapServers) {
			Builder builder = new Builder();
			builder.bootstrapServers = bootstrapServers;
			return builder;
		}

		public Builder schemaRegistry(String schemaRegistry) {
			this.schemaRegistry = schemaRegistry;
			return this;
		}

		public Builder topic(String topic) {
			this.topic = topic;
			return this;
		}

		public Builder stateStoreDir(String stateStoreDir) {
			this.stateStoreDir = stateStoreDir;
			return this;
		}

		public Builder serviceId(String serviceId) {
			this.serviceId = serviceId;
			return this;
		}

		public StreamConfig build() {

			assertNotNull(bootstrapServers);
			assertNotNull(schemaRegistry);
			assertNotNull(serviceId);
			assertNotNull(stateStoreDir);
			assertNotNull(topic);
			return new StreamConfig(this);
		}
	}

	public String getSchemaRegistry() {
		return schemaRegistry;
	}

	public String getBootstrapServers() {
		return bootstrapServers;
	}

	public String getTopic() {
		return topic;
	}

	public String getStateStoreDir() {
		return stateStoreDir;
	}

	public String getServiceId() {
		return serviceId;
	}
}
