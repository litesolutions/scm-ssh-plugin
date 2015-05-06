/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.resource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.shiro.SecurityUtils;

import sonia.scm.security.Role;

import com.aquenos.scm.ssh.server.ScmSshServerConfiguration;
import com.aquenos.scm.ssh.server.ScmSshServerConfigurationStore;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Resource that provides access to the configuration of the SSH server.
 * 
 * @author Sebastian Marsching
 */
@Singleton
@Path("scm-ssh-plugin/server-config")
public class SshServerConfigurationResource {

	private ScmSshServerConfigurationStore configurationStore;

	/**
	 * Constructor. Meant to be called by Guice.
	 * 
	 * @param configurationStore
	 *            the configuration store that is used to load and save the
	 *            configuration.
	 */
	@Inject
	public SshServerConfigurationResource(
			ScmSshServerConfigurationStore configurationStore) {
		this.configurationStore = configurationStore;
	}

	/**
	 * Handles GET requests. Returns the SSH server configuration in the body of
	 * the response in JSON or XML format.
	 * 
	 * @return response with the SSH server configuration or an error response,
	 *         if the user does not have administrative privileges.
	 */
	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public Response getConfiguration() {
		if (SecurityUtils.getSubject().hasRole(Role.ADMIN)) {
			return Response.ok(configurationStore.load()).build();
		} else {
			return Response.status(Status.FORBIDDEN).build();
		}
	}

	/**
	 * Handles POST requests. Stores the SSH server configuration passed in the
	 * request body in JSON or XML format.
	 * 
	 * @param config
	 *            the configuration to save.
	 * @return OK or error response depending on the result of the save
	 *         operation.
	 */
	@PUT
	@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	public Response setConfiguration(ScmSshServerConfiguration config) {
		if (SecurityUtils.getSubject().hasRole(Role.ADMIN)) {
			if (config.getListenAddress() == null || config.getListenPort() < 1
					|| config.getListenPort() > 65535
					|| config.getRsaHostKey() == null
					|| config.getDsaHostKey() == null) {
				return Response.status(Status.BAD_REQUEST).build();
			}
			configurationStore.store(config);
			return Response.ok().build();
		} else {
			return Response.status(Status.FORBIDDEN).build();
		}
	}
}
