/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.server;

import java.io.File;
import java.util.LinkedHashSet;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import sonia.scm.ConfigurationException;
import sonia.scm.SCMContext;

import com.google.inject.Singleton;

/**
 * Provides load and save operations for the SSH server configuration file.
 * 
 * @author Sebastian Marsching
 */
@Singleton
public class ScmSshServerConfigurationStore {

	private final static String PATH = "config" + File.separator
			+ "scm-ssh-plugin.xml";
	private JAXBContext context;
	private File configurationFile;
	private long configurationLastModified = -1L;
	private ScmSshServerConfiguration configuration = null;
	private final Object configLock = new Object();

	private LinkedHashSet<ConfigurationChangeListener> listeners = new LinkedHashSet<ConfigurationChangeListener>();
	private final Object listenerLock = new Object();

	/**
	 * Interface implemented by configuration change listeners.
	 * 
	 * @author Sebastian Marsching
	 */
	public static interface ConfigurationChangeListener {

		/**
		 * Listener method called every time the configuration changes. Please
		 * note that this method will not be called the first time the
		 * configuration is loaded (when no configuration has been loaded
		 * before).
		 * 
		 * @param newConfiguration
		 *            the new configuration.
		 */
		void configurationChanged(ScmSshServerConfiguration newConfiguration);

	}

	/**
	 * Creates a new configuration store. This object should be managed by Guice
	 * to ensure that there is only a single shared instance.
	 */
	public ScmSshServerConfigurationStore() {
		try {
			this.context = JAXBContext
					.newInstance(ScmSshServerConfiguration.class);
			this.configurationFile = new File(SCMContext.getContext()
					.getBaseDirectory(), PATH);
		} catch (JAXBException e) {
			throw new RuntimeException(
					"Could not create JAXB context for class "
							+ ScmSshServerConfiguration.class.getName() + ": "
							+ e.getMessage(), e);
		}
	}

	/**
	 * Loads the configuration from the configuration file. If the configuration
	 * file does not exist, the default configuration is returned.
	 * 
	 * @return configuration loaded from file or default configuration if
	 *         configuration file does not exist.
	 * @throws ConfigurationException
	 *             if configuration file exists but the configuration cannot be
	 *             read from this file.
	 */
	public ScmSshServerConfiguration load() {
		ScmSshServerConfiguration oldConfiguration;
		ScmSshServerConfiguration newConfiguration;
		synchronized (configLock) {
			oldConfiguration = configuration;
			if (configuration == null) {
				if (!configurationFile.exists()) {
					configuration = new ScmSshServerConfiguration();
				} else {
					doLoad();
				}
			} else {
				if (configurationFile.exists()
						&& configurationFile.lastModified() > configurationLastModified) {
					doLoad();
				}
			}
			newConfiguration = configuration;
		}
		if (oldConfiguration != null
				&& !oldConfiguration.equals(newConfiguration)) {
			notifyListeners(newConfiguration);
		}
		return configuration.clone();
	}

	private void doLoad() {
		try {
			long lastModified = configurationFile.lastModified();
			Unmarshaller unmarshaller = context.createUnmarshaller();
			ScmSshServerConfiguration loadedConfiguration = (ScmSshServerConfiguration) unmarshaller
					.unmarshal(configurationFile);
			if (loadedConfiguration != null) {
				configuration = loadedConfiguration;
				configurationLastModified = lastModified;
			} else {
				throw new ConfigurationException(
						"Got null result from unmarshaller while trying to load ssh-server configuration from "
								+ configurationFile);
			}
		} catch (JAXBException e) {
			throw new ConfigurationException(
					"Error while trying to load ssh-server configuration from "
							+ configurationFile, e);
		}
	}

	/**
	 * Stores the configuration to the configuration file.
	 * 
	 * @param configuration
	 *            configuration to be saved.
	 * @throws ConfigurationException
	 *             if configuration cannot be saved.
	 */
	public void store(ScmSshServerConfiguration configuration) {
		// Make a copy of the object that has been passed in.
		configuration = configuration.clone();
		ScmSshServerConfiguration oldConfiguration;
		// Create parent directory.
		if (!configurationFile.getParentFile().exists()) {
			configurationFile.getParentFile().mkdirs();
		}
		synchronized (configLock) {
			oldConfiguration = this.configuration;
			try {
				Marshaller marshaller = context.createMarshaller();
				marshaller.marshal(configuration, configurationFile);
			} catch (JAXBException e) {
				throw new ConfigurationException(
						"Error while trying to store ssh-server configuration in "
								+ configurationFile, e);
			}
			this.configuration = configuration;
			// There is a slight chance of a race condition here, when the file
			// is modified using the store method and directly on the filesystem
			// at the same moment. However this case is very unlikely and even
			// if it happens the only harm will be, that the changes on the
			// filesystem will be ignored.
			this.configurationLastModified = configurationFile.lastModified();
		}
		if (oldConfiguration != null && !oldConfiguration.equals(configuration)) {
			notifyListeners(configuration);
		}
	}

	private void notifyListeners(ScmSshServerConfiguration newConfiguration) {
		synchronized (listenerLock) {
			for (ConfigurationChangeListener listener : listeners) {
				listener.configurationChanged(newConfiguration.clone());
			}
		}
	}

	/**
	 * Registers a listener that will be informed of configuration changes. The
	 * configuration store does not actively monitor the configuration file for
	 * changes. However, if a change is detected (either because the
	 * {@link #load()} method is called and the file has changed in between or
	 * because the {@link #store(ScmSshServerConfiguration)} method is called
	 * with a changed configuration), the listeners are informed.
	 * 
	 * @param listener
	 *            listener to register for configuration change events.
	 * @return <code>true</code> if the listener has not been registered before
	 *         and <code>false</code> if the listener was already registered.
	 */
	public boolean addConfigurationChangeListener(
			ConfigurationChangeListener listener) {
		synchronized (listenerLock) {
			return this.listeners.add(listener);
		}
	}

	/**
	 * Removes a listener. The listener will not receive any more change
	 * notifications after it has been removed.
	 * 
	 * @param listener
	 *            listener to unregister from configuration change events.
	 * @return <code>true</code> if the listener has been registered before and
	 *         has been unregistered now, <code>false</code> if the listener is
	 *         not registered.
	 */
	public boolean removeConfigurationChangeListener(
			ConfigurationChangeListener listener) {
		// There is a slight risk of a race condition here. It could happen that
		// an earlier configuration change somehow got stalled between updating
		// the configuration and notifying the listeners. In this case, the
		// listeners will receive the older configuration version later.
		// However, this situation is so unlikely (the rate of configuration
		// changes is expected to be low), that we do not spend the extra
		// overhead of maintaining a queue with the change events.
		synchronized (listenerLock) {
			return this.listeners.remove(listener);
		}
	}

}
