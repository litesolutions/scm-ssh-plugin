/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.auth;

import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;

/**
 * Base class for security managers that operate without a session store.
 * 
 * @author Sebastian Marsching
 */
public class SessionlessSecurityManager extends DefaultSecurityManager {

	/**
	 * Constructs a new session-less security manager.
	 */
	public SessionlessSecurityManager() {
		super();
		DefaultSubjectDAO subjectDAO = new DefaultSubjectDAO();
		DefaultSessionStorageEvaluator sessionStorageEvaluator = new DefaultSessionStorageEvaluator();
		sessionStorageEvaluator.setSessionStorageEnabled(false);
		subjectDAO.setSessionStorageEvaluator(sessionStorageEvaluator);
		setSubjectDAO(subjectDAO);
	}

}
