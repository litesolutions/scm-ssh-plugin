/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh;


import sonia.scm.plugin.ext.Extension;

import com.aquenos.scm.ssh.server.ScmSshServerModule;
import com.google.inject.servlet.ServletModule;

/**
 * Guice module for the SSH server. This is the external module that exposes the
 * objects meant to be available to the public.
 * 
 * @author Sebastian Marsching
 */
@Extension
public class ScmSshModule extends ServletModule {

	@Override
	protected void configureServlets() {
		install(new ScmSshServerModule());
		filter("/scm-ssh-plugin/*").through(ScmSshFilter.class);
	}

}
