package es.redmic.commandslib.service;

import es.redmic.brokerlib.avro.common.CommonDTO;

public interface CommandGeoServiceItfc<T extends CommonDTO> {

	public T create(T item, String activityId);

	public T update(String id, T item, String activityId);

	public T delete(String id, String activityId);

}
