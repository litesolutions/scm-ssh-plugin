/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh;

/**
 * Provides shared constants for the SCM SSH plugin.
 * 
 * @author Sebastian Marsching
 */
public interface Constants {
	/**
	 * Name of the property used to store the authorized SSH keys of a user.
	 */
	final static String USER_AUTHORIZED_KEYS_PROPERTY = "com.aquenos.scm.ssh.authorizedkeys";
}
