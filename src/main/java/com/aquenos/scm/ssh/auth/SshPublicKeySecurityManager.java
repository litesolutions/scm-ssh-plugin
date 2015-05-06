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
 * Session-less security manager for public-key authentication. Uses a
 * {@link ScmPublicKeyRealm} for authentication.
 * 
 * @author Sebastian Marsching
 */
@Singleton
public class SshPublicKeySecurityManager extends SessionlessSecurityManager {

	/**
	 * Constructor. Meant to be called by Guice.
	 * 
	 * @param realm
	 *            the realm used for authentication.
	 */
	@Inject
	public SshPublicKeySecurityManager(ScmPublicKeyRealm realm) {
		super();
		setRealm(realm);
	}

}
