/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.server;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.util.SecurityUtils;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * A key-pair provider that stores the keys in the
 * {@link ScmSshServerConfiguration}. If no keys are stored yet, this key-pair
 * provider generates them and stores the configuration.
 * 
 * @author Sebastian Marsching
 */
@Singleton
public class ScmKeyPairProvider implements KeyPairProvider {

	private final static String ALGORITHM_RSA = "RSA";
	private final static String ALGORITHM_DSA = "DSA";

	private final static String SSH_RSA_AND_DSS = SSH_RSA + "," + SSH_DSS;

	private KeyPair rsaKeyPair;
	private KeyPair dsaKeyPair;

	private final Object lock = new Object();
	private ScmSshServerConfigurationStore configurationStore;

	private final static Logger LOGGER = LoggerFactory
			.getLogger(ScmKeyPairProvider.class);

	static {
		// Ensure that the bouncy castle providers are registered. We will need
		// them later.
		try {
			SecurityUtils.getKeyFactory(ALGORITHM_RSA);
			SecurityUtils.getKeyFactory(ALGORITHM_DSA);
		} catch (Exception e) {
			// Ignore any exception.
		}
	}

	/**
	 * Constructor. Meant to be called by Guice.
	 * 
	 * @param configurationStore
	 *            the store used to load and save the configuration.
	 */
	@Inject
	public ScmKeyPairProvider(ScmSshServerConfigurationStore configurationStore) {
		this.configurationStore = configurationStore;
		configurationStore
				.addConfigurationChangeListener(new ScmSshServerConfigurationStore.ConfigurationChangeListener() {

					@Override
					public void configurationChanged(
							ScmSshServerConfiguration newConfiguration) {
						reloadKeys(newConfiguration);
					}
				});
		ScmSshServerConfiguration configuration = configurationStore.load();
		reloadKeys(configuration);
	}

	private void reloadKeys(ScmSshServerConfiguration configuration) {
		synchronized (lock) {
			boolean generatedKey = false;
			String rsaKeyString = configuration.getRsaHostKey();
			if (rsaKeyString.trim().isEmpty()) {
				rsaKeyPair = generateKeyPair(ALGORITHM_RSA, 2048);
				rsaKeyString = keyPairToString(rsaKeyPair);
				configuration.setRsaHostKey(rsaKeyString);
				generatedKey = true;
			} else {
				try {
					rsaKeyPair = stringToKeyPair(rsaKeyString, ALGORITHM_RSA);
				} catch (IOException e) {
					LOGGER.error(
							"Failed to read RSA host key: " + e.getMessage(), e);
				}
			}
			String dsaKeyString = configuration.getDsaHostKey();
			if (dsaKeyString.trim().isEmpty()) {
				this.dsaKeyPair = generateKeyPair(ALGORITHM_DSA, 1024);
				dsaKeyString = keyPairToString(dsaKeyPair);
				configuration.setDsaHostKey(dsaKeyString);
				generatedKey = true;
			} else {
				try {
					dsaKeyPair = stringToKeyPair(dsaKeyString, ALGORITHM_DSA);
				} catch (IOException e) {
					LOGGER.error(
							"Failed to read DSA host key: " + e.getMessage(), e);
				}
			}
			if (generatedKey) {
				configurationStore.store(configuration);
			}
		}
	}

	private KeyPair generateKeyPair(String algorithm, int keyLength) {
		KeyPairGenerator generator;
		try {
			generator = SecurityUtils.getKeyPairGenerator(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(algorithm + " algorithm not supported",
					e);
		} catch (NoSuchProviderException e) {
			throw new RuntimeException("No provider for " + algorithm
					+ " algorithm found", e);
		}
		generator.initialize(keyLength);
		return generator.generateKeyPair();
	}

	private KeyPair stringToKeyPair(String keyString, String expectedAlgorithm)
			throws IOException {
		KeyPair kp = stringToKeyPair(keyString);
		String keyAlgorithm = kp.getPrivate().getAlgorithm();
		if (!keyAlgorithm.equals(expectedAlgorithm)) {
			throw new IOException("Loaded key does use " + keyAlgorithm
					+ " instead of expected " + expectedAlgorithm
					+ " algorithm");
		}
		return kp;
	}

	private KeyPair stringToKeyPair(String keyPairString) throws IOException {
		StringReader stringReader = new StringReader(keyPairString);
		PEMReader pemReader = new PEMReader(stringReader);
		Object obj = pemReader.readObject();
		if (obj instanceof KeyPair) {
			return (KeyPair) obj;
		} else {
			throw new IOException(
					"Reader did not produce a key pair but an object of type "
							+ obj.getClass().getName());
		}
	}

	private String keyPairToString(KeyPair keyPair) {
		StringWriter stringWriter = new StringWriter();
		PEMWriter pemWriter = new PEMWriter(stringWriter);
		try {
			pemWriter.writeObject(keyPair);
			pemWriter.flush();
			pemWriter.close();
		} catch (IOException e) {
			throw new RuntimeException("Unexpected IOException: "
					+ e.getMessage(), e);
		}
		return stringWriter.getBuffer().toString();
	}

	@Override
	public KeyPair loadKey(String type) {
		synchronized (lock) {
			if (type.equals(SSH_RSA)) {
				return rsaKeyPair;
			} else if (type.equals(SSH_DSS)) {
				return dsaKeyPair;
			} else {
				return null;
			}
		}
	}

	@Override
	public String getKeyTypes() {
		synchronized (lock) {
			if (rsaKeyPair != null && dsaKeyPair != null) {
				return SSH_RSA_AND_DSS;
			} else if (rsaKeyPair != null) {
				return SSH_RSA;
			} else if (dsaKeyPair != null) {
				return SSH_DSS;
			} else {
				return "";
			}
		}
	}

}
