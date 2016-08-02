/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.auth;

import java.io.IOException;
import java.io.StringReader;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;


import sonia.scm.user.User;
import sonia.scm.user.UserManager;
import sonia.scm.web.security.AuthenticationResult;
import sonia.scm.web.security.AuthenticationState;

import com.aquenos.scm.ssh.Constants;
import com.aquenos.scm.ssh.server.AuthorizedKeysReader;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Authenticator authenticating public keys against a list of public keys
 * authorized for a specific user.
 * 
 * @author Sebastian Marsching
 */
@Singleton
public class PublicKeyAuthenticator {

	private UserManager userManager;

	/**
	 * Constructor. Meant to be called by Guice.
	 * 
	 * @param userManager
	 *            the user manager that is used to get user objects.
	 */
	@Inject
	public PublicKeyAuthenticator(UserManager userManager) {
		this.userManager = userManager;
	}

	/**
	 * Tries to authenticate a user using the supplied public key.
	 * 
	 * @param username
	 *            the name of the user.
	 * @param publicKey
	 *            the public key to look for in the list of authorized keys for
	 *            the user. The public key must be an RSA or DSA key.
	 * @return authentication result representing the result of the
	 *         authentication operation.
	 */
	public AuthenticationResult authenticate(String username,
			PublicKey publicKey) {
		User user = userManager.get(username);
		if (user != null) {
			//if (userManager.getDefaultType().equals(user.getType())) {
				List<PublicKey> keysForUser = getPublicKeysForUser(user);
				if (keysForUser == null) {
					return AuthenticationResult.NOT_FOUND;
				}
				if (containsPublicKey(keysForUser, publicKey)) {
					return new AuthenticationResult(user,
							AuthenticationState.SUCCESS);
				} else {
					return AuthenticationResult.FAILED;
				}
			//}
		}
		return AuthenticationResult.NOT_FOUND;
	}

	private List<PublicKey> getPublicKeysForUser(User user) {
		String authorizedKeysString = user
				.getProperty(Constants.USER_AUTHORIZED_KEYS_PROPERTY);
		if (authorizedKeysString != null) {
			try {
				return AuthorizedKeysReader
						.readAuthorizedKeys(new StringReader(
								authorizedKeysString));
			} catch (IOException e) {
				throw new RuntimeException("Unexpected IOException.", e);
			}
		} else {
			return null;
		}
	}

	private static boolean containsPublicKey(List<PublicKey> keyList,
			PublicKey key) {
		RSAPublicKey rsaKey = null;
		DSAPublicKey dsaKey = null;
		DSAParams dsaParams = null;
		if (key instanceof RSAPublicKey) {
			rsaKey = (RSAPublicKey) key;
		} else if (key instanceof DSAPublicKey) {
			dsaKey = (DSAPublicKey) key;
			dsaParams = dsaKey.getParams();
		} else {
			// Unknown key type.
			return false;
		}
		for (PublicKey keyInList : keyList) {
			if (rsaKey != null && keyInList instanceof RSAPublicKey) {
				RSAPublicKey rsaKey2 = (RSAPublicKey) keyInList;
				if (rsaKey.getModulus().equals(rsaKey2.getModulus())
						&& rsaKey.getPublicExponent().equals(
								rsaKey2.getPublicExponent())) {
					return true;
				}
			} else if (dsaKey != null && keyInList instanceof DSAPublicKey) {
				DSAPublicKey dsaKey2 = (DSAPublicKey) keyInList;
				DSAParams dsaParams2 = dsaKey2.getParams();
				if (dsaKey.getY().equals(dsaKey2.getY())
						&& dsaParams.getG().equals(dsaParams2.getG())
						&& dsaParams.getP().equals(dsaParams2.getP())
						&& dsaParams.getQ().equals(dsaParams2.getQ())) {
					return true;
				}
			}
		}
		return false;
	}

}
