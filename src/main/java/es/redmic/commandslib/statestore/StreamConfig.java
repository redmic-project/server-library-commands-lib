package es.redmic.commandslib.statestore;

public class StreamConfig {

	private String schemaRegistry;

	private String bootstrapServers;

	private String topic;

	private String stateStoreDir;

	private String serviceId;

	private Long windowsTime;

	public StreamConfig(Builder builder) {
		this.topic = builder.topic;
		this.stateStoreDir = builder.stateStoreDir;
		this.serviceId = builder.serviceId;
		this.bootstrapServers = builder.bootstrapServers;
		this.schemaRegistry = builder.schemaRegistry;
		this.windowsTime = builder.windowsTime;
	}

	public static class Builder {

		private static final long DEFAULT_WINDOWS_TIME_MS = 60000;

		private String bootstrapServers;

		private String schemaRegistry;

		private String topic;

		private String stateStoreDir;

		private String serviceId;

		private Long windowsTime;

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

		public Builder windowsTime(long windowsTime) {
			this.windowsTime = windowsTime;
			return this;
		}

		public StreamConfig build() {

			assert bootstrapServers != null;
			assert schemaRegistry != null;
			assert serviceId != null;
			assert stateStoreDir != null;
			assert topic != null;

			if (windowsTime == null) {
				windowsTime = DEFAULT_WINDOWS_TIME_MS;
			}
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

	public long getWindowsTime() {
		return windowsTime;
	}
}
