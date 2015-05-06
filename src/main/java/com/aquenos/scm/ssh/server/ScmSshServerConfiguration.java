/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.server;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Configuration for a SSH server.
 * 
 * @author Sebastian Marsching
 */
@XmlRootElement(name = "scm-ssh-plugin-config")
@XmlAccessorType(XmlAccessType.FIELD)
public class ScmSshServerConfiguration implements Cloneable, Serializable {

	private static final long serialVersionUID = 3500751234991530599L;

	private String listenAddress = "";
	private int listenPort = 8022;
	private String rsaHostKey = "";
	private String dsaHostKey = "";

	/**
	 * Returns the listen address. This is the hostname or IP address that the
	 * SSH server will bind to. If empty, the SSH server will bind to all
	 * locally available addresses.
	 * 
	 * @return listen address (defaults to the empty string).
	 */
	public String getListenAddress() {
		return listenAddress;
	}

	/**
	 * Sets the listen address. This is the hostname or IP address that the SSH
	 * server will bind to. If empty, the SSH server will bind to all locally
	 * available addresses,
	 * 
	 * @param listenAddress
	 *            listen address (IP address or hostname).
	 */
	public void setListenAddress(String listenAddress) {
		this.listenAddress = listenAddress;
	}

	/**
	 * Returns the TCP port the SSH server listens on. Defaults to 8022.
	 * 
	 * @return TCP port for the SSH server.
	 */
	public int getListenPort() {
		return listenPort;
	}

	/**
	 * Sets the TCP port the SSH server listens on.
	 * 
	 * @param listenPort
	 *            TCP port for the SSH server.
	 */
	public void setListenPort(int listenPort) {
		this.listenPort = listenPort;
	}

	/**
	 * Returns the RSA host key in PEM format. If empty, a new key is generated
	 * on server startup. Defaults to the empty string.
	 * 
	 * @return RSA private key in PEM format.
	 */
	public String getRsaHostKey() {
		return rsaHostKey;
	}

	/**
	 * Sets the RSA host key in PEM format. If empty, a new key is genrated on
	 * server startup.
	 * 
	 * @param rsaHostKey
	 *            RSA private key in PEM format.
	 */
	public void setRsaHostKey(String rsaHostKey) {
		this.rsaHostKey = rsaHostKey;
	}

	/**
	 * Returns the DSA host key in PEM format. If empty, a new key is generated
	 * on server startup. Defaults to the empty string.
	 * 
	 * @return DSA private key in PEM format.
	 */
	public String getDsaHostKey() {
		return dsaHostKey;
	}

	/**
	 * Sets the DSA host key in PEM format. If empty, a new key is genrated on
	 * server startup.
	 * 
	 * @param dsaHostKey
	 *            DSA private key in PEM format.
	 */
	public void setDsaHostKey(String dsaHostKey) {
		this.dsaHostKey = dsaHostKey;
	}

	@Override
	public int hashCode() {
		int seed = 23;
		int hashCode = seed;
		if (listenAddress != null) {
			hashCode += listenAddress.hashCode();
		}
		hashCode *= seed;
		hashCode += listenPort;
		hashCode *= seed;
		if (rsaHostKey != null) {
			hashCode += rsaHostKey.hashCode();
		}
		hashCode *= seed;
		if (dsaHostKey != null) {
			hashCode += dsaHostKey.hashCode();
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ScmSshServerConfiguration)) {
			return false;
		}
		ScmSshServerConfiguration c = (ScmSshServerConfiguration) obj;
		return objectEquals(this.listenAddress, c.listenAddress)
				&& this.listenPort == c.listenPort
				&& objectEquals(this.rsaHostKey, c.rsaHostKey)
				&& objectEquals(this.dsaHostKey, c.dsaHostKey);
	}

	private static boolean objectEquals(Object o1, Object o2) {
		if (o1 == null) {
			if (o2 == null) {
				return true;
			} else {
				return false;
			}
		} else {
			return o1.equals(o2);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(ScmSshServerConfiguration.class.getSimpleName());
		sb.append(" { listenAddress=\"");
		sb.append(listenAddress);
		sb.append("\", listenPort=");
		sb.append(listenPort);
		sb.append(", rsaHostKey=\"");
		sb.append(rsaHostKey);
		sb.append("\", dsaHostKey=\"");
		sb.append(dsaHostKey);
		sb.append("\" }");
		return sb.toString();
	}

	@Override
	protected ScmSshServerConfiguration clone() {
		try {
			return (ScmSshServerConfiguration) super.clone();
		} catch (CloneNotSupportedException e) {
			// We know that this class supports cloning, thus this exception
			// should never be thrown.
			throw new RuntimeException(e);
		}
	}

}
