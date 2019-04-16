package es.redmic.commandslib.jsonschema;

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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaUrl;

import es.redmic.brokerlib.deserializer.CustomRelationDeserializer;

public class TestDTO {

	public TestDTO() {
	}

	private String id;

	private Integer mmsi;

	private Integer imo;

	@JsonSerialize(as = TestTypeDTO.class)
	@JsonDeserialize(using = CustomRelationDeserializer.class)
	@JsonSchemaUrl(value = "controller.mapping.testtype")
	private TestTypeDTO type;

	private String name;

	private String callSign;

	private Double length;

	private Double beam;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Integer getMmsi() {
		return mmsi;
	}

	public void setMmsi(Integer mmsi) {
		this.mmsi = mmsi;
	}

	public Integer getImo() {
		return imo;
	}

	public void setImo(Integer imo) {
		this.imo = imo;
	}

	public TestTypeDTO getType() {
		return type;
	}

	public void setType(TestTypeDTO type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCallSign() {
		return callSign;
	}

	public void setCallSign(String callSign) {
		this.callSign = callSign;
	}

	public Double getLength() {
		return length;
	}

	public void setLength(Double length) {
		this.length = length;
	}

	public Double getBeam() {
		return beam;
	}

	public void setBeam(Double beam) {
		this.beam = beam;
	}

	public class TestTypeDTO {

		private String id;

		private String name;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
