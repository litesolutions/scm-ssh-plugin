/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Session-less security manager for password authentication. Uses
 * {@link ScmPasswordRealm} for authentication.
 * 
 * @author Sebastian Marsching
 */
@Singleton
public class SshPasswordSecurityManager extends SessionlessSecurityManager {

	/**
	 * Constructor. Meant to be called by Guice.
	 * 
	 * @param realm
	 *            the realm used for authentication.
	 */
	@Inject
	public SshPasswordSecurityManager(ScmPasswordRealm realm) {
		super();
		setRealm(realm);
	}

}
