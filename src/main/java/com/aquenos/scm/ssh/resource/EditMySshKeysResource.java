/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.resource;

import java.io.IOException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.shiro.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sonia.scm.user.User;
import sonia.scm.user.UserException;
import sonia.scm.user.UserManager;

import com.aquenos.scm.ssh.Constants;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Resource serving HTTP requests for allowing a user to change his SSH keys.
 * 
 * @author Sebastian Marsching
 */
@Singleton
@Path("scm-ssh-plugin/my-ssh-keys")
public class EditMySshKeysResource {

	private UserManager userManager;
	private final static Logger LOGGER = LoggerFactory
			.getLogger(EditMySshKeysResource.class);

	/**
	 * Constructor. Meant to be called by Guice.
	 * 
	 * @param userManager
	 *            user manager providing access to SCM users.
	 */
	@Inject
	public EditMySshKeysResource(UserManager userManager) {
		this.userManager = userManager;
	}

	/**
	 * Handles GET requests.
	 * 
	 * @return a response body with the SSH keys for the user as a plain text
	 *         string.
	 */
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public Response getSshKeys() {
		// Load user from user manager to ensure that we have the newest
		// version.
		User user = SecurityUtils.getSubject().getPrincipals()
				.oneByType(User.class);
		if (user.getId().equalsIgnoreCase("anonymous")) {
			return Response.status(Status.FORBIDDEN).build();
		}
		// Make sure we have the most recent version of the user object.
		user = userManager.get(user.getId());
		if (user == null) {
			return Response.status(Status.NOT_FOUND).build();
		}
		String sshKeys = user
				.getProperty(Constants.USER_AUTHORIZED_KEYS_PROPERTY);
		if (sshKeys == null) {
			sshKeys = "";
		}
		return Response.ok(sshKeys).build();
	}

	/**
	 * Handles POST requests. Updates the SSH keys for the user that is
	 * currently logged in. Expects the SSH keys in the request body (either as
	 * plain text or URL-encoded).
	 * 
	 * @param sshKeys
	 *            string storing SSH keys in OpenSSH authorized_keys file
	 *            format.
	 * @return OK response or error response, depending on the result of the
	 *         action.
	 */
	@PUT
	@Consumes({ MediaType.TEXT_PLAIN, MediaType.APPLICATION_FORM_URLENCODED })
	public Response setSshKeys(String sshKeys) {
		User user = userManager.get(SecurityUtils.getSubject().getPrincipals()
				.oneByType(String.class));
		if (user.getId().equalsIgnoreCase("anonymous")) {
			return Response.status(Status.FORBIDDEN).build();
		}
		// Make sure we have the most recent version of the user object.
		user = userManager.get(user.getId());
		if (user == null) {
			return Response.status(Status.NOT_FOUND).build();
		}
		// Update ssh keys property.
		user.setProperty(Constants.USER_AUTHORIZED_KEYS_PROPERTY, sshKeys);
		// Save modified user.
		try {
			userManager.modify(user);
		} catch (UserException e) {
			LOGGER.error("Error while trying to modify user \"" + user.getId()
					+ "\": " + e.getMessage(), e);
			return Response.serverError().build();
		} catch (IOException e) {
			LOGGER.error("Error while trying to modify user \"" + user.getId()
					+ "\": " + e.getMessage(), e);
			return Response.serverError().build();
		}
		return Response.ok().build();
	}

}
