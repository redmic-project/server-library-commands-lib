package es.redmic.commandslib.health;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaHealthCheck implements HealthIndicator {

	private KafkaTemplate<String, String> kafka;

	@Autowired
	public KafkaHealthCheck(KafkaTemplate<String, String> kafka) {
		this.kafka = kafka;
	}

	/**
	 * Return an indication of health.
	 *
	 * @return the health for
	 */
	@Override
	public Health health() {
		try {
			kafka.send("kafka-health-indicator", "❥").get(100, TimeUnit.MILLISECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			return Health.down(e).build();
		}
		return Health.up().build();
	}
}