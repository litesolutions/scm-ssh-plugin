/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.server;

import java.security.PublicKey;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.subject.Subject;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import com.aquenos.scm.ssh.auth.PublicKeyToken;
import com.aquenos.scm.ssh.auth.SshPublicKeySecurityManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * SSH public-key authenticator that delegates authentication to a
 * {@link SecurityManager}. The authenticated subject is stored in the
 * {@link ServerSession} in order to reuse it later (e.g. when executing a
 * command). The created subject is also equipped with a simple session that is
 * implicitly tied to the SSH session.
 * 
 * @author Sebastian Marsching
 */
@Singleton
public class ScmPublickeyAuthenticator implements PublickeyAuthenticator {

	private SecurityManager securityManager;

	/**
	 * Constructor. Meant to be called by Guice.
	 * 
	 * @param securityManager
	 *            the security manager used for authentication.
	 */
	@Inject
	public ScmPublickeyAuthenticator(SshPublicKeySecurityManager securityManager) {
		this.securityManager = securityManager;
	}

	@Override
	public boolean authenticate(String username, PublicKey publicKey,
			ServerSession session) {
		if (username == null || publicKey == null) {
			return false;
		}
		SimpleSession shiroSession = new SimpleSession();
		shiroSession.setTimeout(-1L);
		Subject subject = new Subject.Builder(securityManager)
				.session(shiroSession)
				.host(session.getIoSession().getRemoteAddress().toString())
				.buildSubject();
		try {
			subject.login(new PublicKeyToken(username, publicKey));
		} catch (AuthenticationException e) {
			return false;
		}
		// Store subject in session.
		session.setAttribute(ScmSshServer.SUBJECT_SESSION_ATTRIBUTE_KEY,
				subject);
		return true;
	}

}
