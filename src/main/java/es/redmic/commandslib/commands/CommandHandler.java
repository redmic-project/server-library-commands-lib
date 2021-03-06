package es.redmic.commandslib.commands;

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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.kafka.annotation.KafkaHandler;

import es.redmic.brokerlib.alert.AlertService;
import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.avro.fail.RollbackFailedEvent;
import es.redmic.commandslib.aggregate.Aggregate;
import es.redmic.commandslib.exceptions.ConfirmationTimeoutException;
import es.redmic.commandslib.gateway.BrokerEvent;
import es.redmic.exception.common.BaseException;

public abstract class CommandHandler implements ApplicationEventPublisherAware {

	@Value("${rest.eventsource.timeout.ms}")
	protected long timeoutMS;

	protected static Logger logger = LogManager.getLogger();

	protected ApplicationEventPublisher eventPublisher;

	protected Map<String, CompletableFuture<Object>> completableFeatures = new HashMap<>();

	@Autowired
	AlertService alertService;

	@KafkaHandler
	private void listen(RollbackFailedEvent event) {

		alertService.errorAlert("Rollback fallido " + event.getAggregateId(), "Rollback de evento "
				+ event.getFailEventType() + " con id " + event.getAggregateId() + " ha fallado.");
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	protected void publishToKafka(Event evt, String topic) {

		this.eventPublisher.publishEvent(new BrokerEvent(this, evt, topic));
	}

	/* Handler por defecto para descartar los mensajes que no queremos */
	@KafkaHandler(isDefault = true)
	public void defaultListen(Object event) {
	}

	// Resuelve el CompletableFuture con el evento recibido
	protected void resolveCommand(String sessionId) {
		resolveCommand(sessionId, null);
	}

	protected void resolveCommand(String sessionId, Object result) {

		// Si el evento es una excepción se resuelve con ella, si no, con null que
		// significa que todo fue bien
		Executors.newCachedThreadPool().submit(() -> {
			CompletableFuture<Object> future = completableFeatures.get(sessionId);

			if (future != null) {
				future.complete(result);
			} else {
				logger.warn("Petición asíncrona no resgistrada para sessionId: " + sessionId);
			}
			return;
		});
	}

	protected void unlockStatus(Aggregate agg, String id, String topic) {

		Event rollbackEvent = agg.getRollbackEventFromBlockedEvent(id, timeoutMS);

		if (rollbackEvent != null) {
			alertService.errorAlert(rollbackEvent.getType() + " rollback", "Enviando rollback de evento "
					+ rollbackEvent.getType() + " con id " + rollbackEvent.getAggregateId());
			publishToKafka(rollbackEvent, topic);
		}
	}

	protected <T> T sendEventAndWaitResult(Aggregate agg, Event event, String topic) {

		// Crea la espera hasta que se responda con evento completado
		CompletableFuture<T> completableFuture = getCompletableFeature(event.getSessionId());

		// Emite evento para enviar a kafka
		publishToKafka(event, topic);

		// Obtiene el resultado cuando se resuelva la espera
		try {
			return getResult(event.getSessionId(), completableFuture);
		} catch (ConfirmationTimeoutException e) {
			e.printStackTrace();
			alertService.errorAlert(event.getType() + " rollback", "Enviando rollback de evento " + event.getType()
					+ " con id " + event.getAggregateId() + " " + e.getLocalizedMessage());
			publishToKafka(agg.getRollbackEvent(event), topic);
			throw e;
		}
	}

	// Crea un completableFuture para esperar por el evento de confirmación o error.
	protected <T> CompletableFuture<T> getCompletableFeature(String sessionId) {

		// Añade espera para resolver la petición
		CompletableFuture<Object> future = new CompletableFuture<Object>();
		completableFeatures.put(sessionId, future);

		// Cuando se resuelve la espera, se resuelve con el dto
		return future.thenApplyAsync(obj -> apply(obj));
	}

	@SuppressWarnings("unchecked")
	private <T> T apply(Object result) {

		if (!(result instanceof BaseException)) {
			logger.debug("Resolver con éxito");
			return (T) result;
		} else {
			logger.debug("Error. Lanzar excepción.");
			throw (BaseException) result;
		}
	}

	// Resuelve el CompletableFuture controlando posibles fallos y borrando la
	// entrada. El timeout es configurable dependiendo de la función llamada
	protected <T> T getResult(String sessionId, CompletableFuture<T> completableFuture) {
		return getResult(timeoutMS, sessionId, completableFuture);
	}

	protected <T> T getResult(long timeoutMS, String sessionId, CompletableFuture<T> completableFuture) {

		try {
			return completableFuture.get(timeoutMS, TimeUnit.MILLISECONDS);
		} catch (InterruptedException | TimeoutException e) {
			e.printStackTrace();
			logger.error("Error. No se ha recibido confirmación de la acción realizada.");

			throw new ConfirmationTimeoutException();
		} catch (ExecutionException e) {
			e.printStackTrace();

			if (e.getCause() instanceof BaseException)
				throw ((BaseException) e.getCause()); // Error enviado desde la vista

			logger.error("Error. Excepción no controlada en la ejecución.");

			throw new ConfirmationTimeoutException();
		} finally {
			completableFeatures.remove(sessionId);
		}
	}
}
