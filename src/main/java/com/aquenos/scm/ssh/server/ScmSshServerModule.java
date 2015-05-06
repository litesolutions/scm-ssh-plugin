/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.server;

import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;

import com.aquenos.scm.ssh.git.GitCommandFactory;
import com.google.inject.PrivateModule;

/**
 * Internal Guice module for the SSH server. This module contains all the
 * components that are only internally needed by the server. Only the
 * {@link ScmSshServer} and {@link ScmSshServerConfigurationStore} are exported
 * for use by other modules.
 * 
 * @author Sebastian Marsching
 */
public class ScmSshServerModule extends PrivateModule {

	@Override
	protected void configure() {
		bind(PasswordAuthenticator.class).to(ScmPasswordAuthenticator.class);
		bind(PublickeyAuthenticator.class).to(ScmPublickeyAuthenticator.class);
		bind(CommandFactory.class).to(GitCommandFactory.class);
		bind(ScmSshServer.class);
		expose(ScmSshServer.class);
		bind(ScmSshServerConfigurationStore.class);
		expose(ScmSshServerConfigurationStore.class);
	}

}
