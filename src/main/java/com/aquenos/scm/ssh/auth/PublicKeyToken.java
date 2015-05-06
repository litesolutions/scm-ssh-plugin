/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.auth;

import java.security.PublicKey;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * Authentication token used for authentication with a public key.
 * 
 * @author Sebastian Marsching
 */
public class PublicKeyToken implements AuthenticationToken {

	private static final long serialVersionUID = -4997384790606994434L;

	private String username;
	private PublicKey publicKey;

	/**
	 * Constructs a public-key authentication-token.
	 * 
	 * @param username
	 *            the name of the user to authenticate.
	 * @param publicKey
	 *            the public key to use for authentication.
	 */
	public PublicKeyToken(String username, PublicKey publicKey) {
		this.username = username;
		this.publicKey = publicKey;
	}

	/**
	 * Returns the username associated with this token.
	 * 
	 * @return username.
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Returns the public key associated with this token.
	 * 
	 * @return public key.
	 */
	public PublicKey getPublicKey() {
		return publicKey;
	}

	@Override
	public String getPrincipal() {
		return username;
	}

	@Override
	public PublicKey getCredentials() {
		return publicKey;
	}

}
