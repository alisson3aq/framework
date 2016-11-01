package org.demoiselle.jee.core.lifecycle;

import org.demoiselle.jee.core.exception.DemoiselleException;

/**
 * 
 * Represents a throw Exceptions from Lifecycle feature
 *
 */
public class LifecycleException extends DemoiselleException {

	private static final long serialVersionUID = -3751898389838825583L;

	public LifecycleException(Exception e) {
		super(e);
	}

}
