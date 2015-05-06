/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;


import com.aquenos.scm.ssh.server.ScmSshServer;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Filter that creates the SSH Server. This filter does not actually filter any
 * requests. It only purpose is to create the SSH server process, because there
 * is no generic service extension point in SCM.
 * 
 * @author Sebastian Marsching
 */
@Singleton
public class ScmSshFilter implements Filter {

	private ScmSshServer sshServer;

	/**
	 * Constructor. Meant to be called by Guice.
	 * 
	 * @param sshServer
	 *            the SSH server instance that will be started when this filter
	 *            is initialized and stopped when this filter is destroyed.
	 */
	@Inject
	public ScmSshFilter(ScmSshServer sshServer) {
		this.sshServer = sshServer;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		sshServer.start();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
		sshServer.stop();
	}

}
