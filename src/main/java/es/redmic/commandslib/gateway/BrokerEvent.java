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
