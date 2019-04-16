package es.redmic.commandslib.gateway;

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
	}

	@Override
	public void onApplicationEvent(BrokerEvent brokerEvent) {

		Event evt = (Event) brokerEvent.getEvt();

		String topic = brokerEvent.getTopic();

		logger.debug("sending payload='{}' to topic='{}'", evt, topic);

		ListenableFuture<SendResult<String, Event>> future = kafkaTemplate.send(topic, evt.getAggregateId(), evt);

		future.addCallback(new SendListener());
	}
}
