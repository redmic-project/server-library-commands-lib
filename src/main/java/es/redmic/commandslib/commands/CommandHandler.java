package es.redmic.commandslib.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.kafka.annotation.KafkaHandler;

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.commandslib.exceptions.ConfirmationTimeoutException;
import es.redmic.commandslib.gateway.BrokerEvent;
import es.redmic.exception.common.BaseException;

public abstract class CommandHandler implements ApplicationEventPublisherAware {

	@Value("${rest.eventsource.timeout.ms}")
	private long timeoutMS;

	protected static Logger logger = LogManager.getLogger();

	protected ApplicationEventPublisher eventPublisher;

	protected Map<String, CompletableFuture<BaseException>> completableFeatures = new HashMap<>();

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
		logger.info("Mensaje descartado: " + event.getClass());
	}

	// Resuelve el CompletableFuture con el evento recibido
	protected void resolveCommand(String sessionId) {
		resolveCommand(sessionId, null);
	}

	protected void resolveCommand(String sessionId, BaseException ex) {

		// Si el evento es una excepción se resuelve con ella, si no, con null que
		// significa que todo fue bien
		Executors.newCachedThreadPool().submit(() -> {
			CompletableFuture<BaseException> future = completableFeatures.get(sessionId);

			if (future != null) {
				future.complete(ex);// future.complete(ex);
			} else {
				logger.info("Petición asíncrona no resgistrada");
			}
			return;
		});
	}

	// Crea un completableFuture para esperar por el evento de confirmación o error.
	protected <T> CompletableFuture<T> getCompletableFeature(String sessionId, T item) {

		// Añade espera para resolver la petición
		CompletableFuture<BaseException> future = new CompletableFuture<BaseException>();
		completableFeatures.put(sessionId, future);
		// Cuando se resuelve la espera, se resuelve con el dto
		return future.thenApplyAsync(ex -> apply(ex, item));
	}

	private <T> T apply(BaseException ex, T item) {

		if (ex == null) {
			logger.debug("Resolver con éxito");
			return item;
		} else {
			logger.debug("Error. Lanzar excepción.");
			throw ex;
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
			// TODO: Enviar alerta ya que ha quedado un evento sin acabar el ciclo
			logger.error("Error. No se ha recibido confirmación de la acción realizada.");
			throw new ConfirmationTimeoutException();
		} catch (ExecutionException e) {
			if (e.getCause() instanceof BaseException)
				throw ((BaseException) e.getCause()); // Error enviado desde la vista
			else
				throw new ConfirmationTimeoutException();
		} finally {
			completableFeatures.remove(sessionId);
		}
	}
}
