package es.redmic.commandslib.service;

import es.redmic.brokerlib.avro.common.CommonDTO;

public interface CommandServiceItfc<T extends CommonDTO> {

	public T create(T item);

	public T update(String id, T item);

	public T delete(String id);

}
