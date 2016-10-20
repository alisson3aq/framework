package org.demoiselle.jee.configuration.model;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;

import org.demoiselle.jee.configuration.annotation.Configuration;

@Configuration()
public class ConfigWithValidationModel {
	
	@NotNull
	private String configString;
	
	@Null
	private Integer configInteger;
	
	public String getConfigString() {
		return configString;
	}
	public Integer getConfigInteger() {
		return configInteger;
	}
	
	

}