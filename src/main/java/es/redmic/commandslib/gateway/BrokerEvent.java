package es.redmic.commandslib.gateway;

import org.springframework.context.ApplicationEvent;

public class BrokerEvent extends ApplicationEvent {

	private static final long serialVersionUID = 1L;

	private Object evt;

	private String topic;

	public BrokerEvent(Object source, Object evt, String topic) {
		super(source);
		this.setEvt(evt);
		this.setTopic(topic);
	}

	public Object getEvt() {
		return evt;
	}

	public void setEvt(Object evt) {
		this.evt = evt;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}
}
