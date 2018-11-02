package es.redmic.commandslib.streaming.common;

import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.processor.WallclockTimestampExtractor;
import org.apache.kafka.streams.state.RocksDBConfigSetter;
import org.rocksdb.Options;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

public class StreamUtils {

	// @formatter:off

	protected final static String SCHEMA_REGISTRY_URL_PROPERTY = "schema.registry.url",
			SCHEMA_REGISTRY_VALUE_SUBJECT_NAME_STRATEGY = "value.subject.name.strategy";

	// @formatter:on

	public static Properties baseStreamsConfig(String bootstrapServers, String stateDir, String appId,
			String schemaRegistry) {
		Properties config = new Properties();
		// Workaround for a known issue with RocksDB in environments where you have only
		// 1 cpu core.
		config.put(StreamsConfig.ROCKSDB_CONFIG_SETTER_CLASS_CONFIG, CustomRocksDBConfig.class);
		config.put(StreamsConfig.APPLICATION_ID_CONFIG, appId);
		config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
		config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
		config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);

		// config.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG,
		// StreamsConfig.EXACTLY_ONCE);
		config.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1); // commit as fast as possible

		config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
		config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
		config.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, WallclockTimestampExtractor.class.getName());
		config.put(SCHEMA_REGISTRY_URL_PROPERTY, schemaRegistry);
		config.put(SCHEMA_REGISTRY_VALUE_SUBJECT_NAME_STRATEGY,
				"io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");

		return config;
	}

	public static class CustomRocksDBConfig implements RocksDBConfigSetter {

		@Override
		public void setConfig(final String storeName, final Options options, final Map<String, Object> configs) {
			// Workaround: We must ensure that the parallelism is set to >= 2. There seems
			// to be a known
			// issue with RocksDB where explicitly setting the parallelism to 1 causes
			// issues (even though
			// 1 seems to be RocksDB's default for this configuration).
			int compactionParallelism = Math.max(Runtime.getRuntime().availableProcessors(), 2);
			// Set number of compaction threads (but not flush threads).
			options.setIncreaseParallelism(compactionParallelism);
		}
	}

}
