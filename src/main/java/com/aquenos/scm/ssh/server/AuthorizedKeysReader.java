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
import java.io.Reader;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAParameterSpec;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.mina.util.Base64;

/**
 * Utility for reading a file in the OpenSSH authorized_keys format.
 * 
 * @author Sebastian Marsching
 */
public class AuthorizedKeysReader {

	private static class DSAPublicKeyImpl implements DSAPublicKey {
		private static final long serialVersionUID = -6107973445753882071L;
		private final BigInteger pub;
		private final DSAParams params;

		private DSAPublicKeyImpl(BigInteger p, BigInteger q, BigInteger g,
				BigInteger pub) {
			this.pub = pub;
			this.params = new DSAParameterSpec(p, q, g);
		}

		@Override
		public String getFormat() {
			return null;
		}

		@Override
		public byte[] getEncoded() {
			return null;
		}

		@Override
		public String getAlgorithm() {
			return "DSA";
		}

		@Override
		public DSAParams getParams() {
			return params;
		}

		@Override
		public BigInteger getY() {
			return pub;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("DSA Public Key\n");
			sb.append("            y: ");
			sb.append(pub.toString(16));
			sb.append("\n");
			return sb.toString();
		}
	}

	private static class RSAPublicKeyImpl implements RSAPublicKey {
		private static final long serialVersionUID = -5914480141972018173L;
		private final BigInteger exponent;
		private final BigInteger modulus;

		private RSAPublicKeyImpl(BigInteger modulus, BigInteger exponent) {
			this.exponent = exponent;
			this.modulus = modulus;
		}

		@Override
		public BigInteger getModulus() {
			return modulus;
		}

		@Override
		public String getFormat() {
			return null;
		}

		@Override
		public byte[] getEncoded() {
			return null;
		}

		@Override
		public String getAlgorithm() {
			return "RSA";
		}

		@Override
		public BigInteger getPublicExponent() {
			return exponent;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("RSA Public Key\n");
			sb.append("            modulus: ");
			sb.append(modulus.toString(16));
			sb.append("\n");
			sb.append("    public exponent: ");
			sb.append(exponent.toString(16));
			sb.append("\n");
			return sb.toString();
		}
	}

	/**
	 * Reads a list of public keys from a file in the OpenSSH authorized_keys
	 * format. Lines that do not match the expected format or use an unsupported
	 * key type are silently ignored. Only SSH v2 RSA and DSA keys are
	 * supported.
	 * 
	 * @param reader
	 *            the reader to read the public keys from.
	 * @return list of public keys found in the file.
	 * @throws IOException
	 *             if an I/O error occurs while reading the file.
	 */
	public static List<PublicKey> readAuthorizedKeys(Reader reader)
			throws IOException {
		LinkedList<PublicKey> publicKeys = new LinkedList<PublicKey>();
		int c;
		lineloop: do {
			do {
				c = reader.read();
				if (!Character.isWhitespace((char) c)) {
					break;
				}
			} while (c != -1);
			// We have skipped the leading whitespace (and possibly empty
			// lines).
			// Now, we expect either the start of a comment, the parameters
			// list, or the key type.
			if (c == '#') {
				// Comment, skip everything until the end of line
				c = skipToLineBreak(reader, c);
				continue lineloop;
			}
			// We apply quotation rules for the parameter list. If there is no
			// parameter list and we are reading the key type, this does not
			// hurt us.
			StringBuilder sb = new StringBuilder();
			boolean expectingStartOfKey = true;
			boolean inKey = false;
			boolean expectingStartOfValue = false;
			boolean inValue = false;
			boolean expectingNextParameter = false;
			boolean inEscapeSequence = false;
			while (c != -1) {
				if (expectingStartOfKey) {
					if (Character.isLetterOrDigit((char) c) || c == '-') {
						// First character of key.
						sb.append((char) c);
						expectingStartOfKey = false;
						inKey = true;
					} else {
						// Malformed parameter list, so we skip the current
						// line.
						c = skipToLineBreak(reader, c);
						continue lineloop;
					}
				} else if (inKey) {
					if (c == ',') {
						// Key without value ended, so expect next key.
						sb.append((char) c);
						inKey = false;
						expectingStartOfKey = true;
					} else if (c == '=') {
						// Key with value ended, so expect value.
						sb.append((char) c);
						inKey = false;
						expectingStartOfValue = true;
					} else if (Character.isWhitespace((char) c)) {
						// End of parameter list, so exit loop.
						break;
					} else if (Character.isLetterOrDigit((char) c) || c == '-') {
						// Part of key.
						sb.append((char) c);
					} else {
						// Malformed key, so skip line.
						c = skipToLineBreak(reader, c);
						continue lineloop;
					}
				} else if (expectingStartOfValue) {
					if (c == '\"') {
						sb.append((char) c);
						expectingStartOfValue = false;
						inValue = true;
					} else {
						// Malformed value, so skip line.
						c = skipToLineBreak(reader, c);
						continue lineloop;
					}
				} else if (inValue) {
					if (inEscapeSequence) {
						sb.append((char) c);
						inEscapeSequence = false;
					} else {
						if (isLineBreak((char) c)) {
							// Malformed value, so skip line.
							continue lineloop;
						} else if (c == '\\') {
							sb.append((char) c);
							inEscapeSequence = true;
						} else if (c == '"') {
							sb.append((char) c);
							inValue = false;
							expectingNextParameter = true;
						} else {
							// Normal part of value.
							sb.append((char) c);
						}
					}
				} else if (expectingNextParameter) {
					if (c == ',') {
						sb.append((char) c);
						expectingNextParameter = false;
						expectingStartOfKey = true;
					} else if (Character.isWhitespace((char) c)) {
						// End of parameter list.
						sb.append((char) c);
						break;
					}
				}
				c = reader.read();
			}
			String parameterList = sb.toString();
			// Reset string builder.
			sb.setLength(0);
			String keyType = null;
			if (isSupportedKeyType(parameterList)) {
				// The text we just read was not the parameter list, but the key
				// type.
				keyType = parameterList;
				parameterList = null;
			} else {
				// Read key type.
				c = skipWhitespaceNoLineBreak(reader);
				while (c != -1) {
					if (Character.isLetterOrDigit((char) c) || c == '-') {
						sb.append((char) c);
					} else if (Character.isWhitespace((char) c)) {
						break;
					} else {
						// Malformed key type, skip line.
						c = skipToLineBreak(reader, c);
						continue lineloop;
					}
				}
				keyType = sb.toString();
				// Reset string builder.
				sb.setLength(0);
				if (!isSupportedKeyType(keyType)) {
					// Unsupported key type, skip line.
					c = skipToLineBreak(reader, c);
					continue lineloop;
				}
			}
			// Now we expect the public key (possibly separated by more
			// whitespace).
			c = skipWhitespaceNoLineBreak(reader);
			while (c != -1) {
				if (Character.isLetterOrDigit((char) c) || c == '+' || c == '/'
						|| c == '=') {
					sb.append((char) c);
				} else if (Character.isWhitespace((char) c)) {
					// End of public key.
					break;
				} else {
					// Malformed key, skip line.
					c = skipToLineBreak(reader, c);
					continue lineloop;
				}
				c = reader.read();
			}
			String keyString = sb.toString();
			// Reset string builder.
			sb.setLength(0);
			// Finally, there can be a comment, but we just skip the rest of the
			// line.
			c = skipToLineBreak(reader, c);
			PublicKey pubKey;
			try {
				pubKey = readPublicKey(keyType, keyString);
			} catch (IllegalArgumentException e) {
				// Malformed key, so skip to next line
				continue lineloop;
			}
			publicKeys.add(pubKey);
		} while (c != -1);
		return publicKeys;
	}

	private static boolean isLineBreak(char c) {
		return c == '\r' || c == '\n';
	}

	private static int skipWhitespaceNoLineBreak(Reader reader)
			throws IOException {
		int c;
		do {
			c = reader.read();
		} while (c != -1 && Character.isWhitespace((char) c)
				&& !isLineBreak((char) c));
		return c;
	}

	private static int skipToLineBreak(Reader reader, int c) throws IOException {
		while (c != -1 && !isLineBreak((char) c)) {
			c = reader.read();
		}
		return c;
	}

	private static boolean isSupportedKeyType(String keyType) {
		return keyType.equals("ssh-rsa") || keyType.equals("ssh-dss");
	}

	private static PublicKey readPublicKey(String keyType, String keyString) {
		if (keyType.equals("ssh-rsa")) {
			return readRSAPublicKey(keyString);
		} else if (keyType.equals("ssh-dss")) {
			return readDSAPublicKey(keyString);
		} else {
			throw new IllegalArgumentException("Unsupported key type: "
					+ keyType);
		}
	}

	private static RSAPublicKey readRSAPublicKey(String keyString) {
		byte[] keyData = Base64.decodeBase64(keyString.getBytes());
		int keyLength = keyData.length;
		int i = 0;
		if (keyLength - i < 4) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		int fieldLength = readPositiveInt(keyData, i);
		i += 4;
		if (keyLength - i < fieldLength) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		String keyType = new String(keyData, i, fieldLength);
		i += fieldLength;
		if (!keyType.equals("ssh-rsa")) {
			throw new IllegalArgumentException(
					"Specified key is not a valid SSH RSA public key.");
		}
		if (keyLength - i < 4) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		fieldLength = readPositiveInt(keyData, i);
		i += 4;
		if (keyLength - i < fieldLength) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		final BigInteger exponent = new BigInteger(Arrays.copyOfRange(keyData,
				i, i + fieldLength));
		i += fieldLength;
		if (keyLength - i < 4) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		fieldLength = readPositiveInt(keyData, i);
		i += 4;
		if (keyLength - i < fieldLength) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		final BigInteger modulus = new BigInteger(Arrays.copyOfRange(keyData,
				i, i + fieldLength));
		i += fieldLength;
		if (i < keyLength) {
			throw new IllegalArgumentException(
					"Unexpcted surplus data at end of key.");
		}
		return new RSAPublicKeyImpl(modulus, exponent);
	}

	private static DSAPublicKey readDSAPublicKey(String keyString) {
		byte[] keyData = Base64.decodeBase64(keyString.getBytes());
		int keyLength = keyData.length;
		int i = 0;
		if (keyLength - i < 4) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		int fieldLength = readPositiveInt(keyData, i);
		i += 4;
		if (keyLength - i < fieldLength) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		String keyType = new String(keyData, i, fieldLength);
		i += fieldLength;
		if (!keyType.equals("ssh-dss")) {
			throw new IllegalArgumentException(
					"Specified key is not a valid SSH DSA public key.");
		}
		if (keyLength - i < 4) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		fieldLength = readPositiveInt(keyData, i);
		i += 4;
		if (keyLength - i < fieldLength) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		final BigInteger p = new BigInteger(Arrays.copyOfRange(keyData, i, i
				+ fieldLength));
		i += fieldLength;
		if (keyLength - i < 4) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		fieldLength = readPositiveInt(keyData, i);
		i += 4;
		if (keyLength - i < fieldLength) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		final BigInteger q = new BigInteger(Arrays.copyOfRange(keyData, i, i
				+ fieldLength));
		i += fieldLength;
		if (keyLength - i < 4) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		fieldLength = readPositiveInt(keyData, i);
		i += 4;
		if (keyLength - i < fieldLength) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		final BigInteger g = new BigInteger(Arrays.copyOfRange(keyData, i, i
				+ fieldLength));
		i += fieldLength;
		if (keyLength - i < 4) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		fieldLength = readPositiveInt(keyData, i);
		i += 4;
		if (keyLength - i < fieldLength) {
			throw new IllegalArgumentException("Unexpected end of data.");
		}
		final BigInteger pub = new BigInteger(Arrays.copyOfRange(keyData, i, i
				+ fieldLength));
		i += fieldLength;
		if (i < keyLength) {
			throw new IllegalArgumentException(
					"Unexpcted surplus data at end of key.");
		}
		return new DSAPublicKeyImpl(p, q, g, pub);
	}

	private static int readPositiveInt(byte[] data, int offset) {
		int result = readInt(data, offset);
		if (result < 0) {
			throw new IllegalArgumentException(
					"Read negative number, where positive one was expected.");
		}
		return result;
	}

	private static int readInt(byte[] data, int offset) {
		// Byte 0 is MSB
		int byte0 = data[offset] & 0xFF;
		int byte1 = data[offset + 1] & 0xFF;
		int byte2 = data[offset + 2] & 0xFF;
		int byte3 = data[offset + 3] & 0xFF;
		int result = (byte0 << 24) | (byte1 << 16) | (byte2 << 8) | byte3;
		return result;
	}

}
