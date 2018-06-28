package es.redmic.commandslib.gateway;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.listener.SendListener;

@Component
public class CommandGateway implements ApplicationListener<BrokerEvent> {

	protected static Logger logger = LogManager.getLogger();

	@Autowired
	protected KafkaTemplate<String, Event> kafkaTemplate;

	public CommandGateway() {
		logger.info("Arrancando command gateway");
	}

	@Override
	public void onApplicationEvent(BrokerEvent brokerEvent) {

		Event evt = (Event) brokerEvent.getEvt();

		String topic = brokerEvent.getTopic();

		logger.info("sending payload='{}' to topic='{}'", evt, topic);

		ListenableFuture<SendResult<String, Event>> future = kafkaTemplate.send(topic, evt.getAggregateId(), evt);

		future.addCallback(new SendListener());
	}
}
