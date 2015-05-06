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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;

/**
 * Base class for SSH commands. Handles common tasks like storing the stream
 * objects, environment and server session and starting and stopping a command
 * thread. Classes derived from this class should simply implement the
 * {@link #run()} method.
 * 
 * @author Sebastian Marsching
 */
public abstract class AbstractCommand implements Command, SessionAware {

	private Thread commandThread;
	private boolean interruptRequested = false;
	private final Object threadLock = new Object();

	private InputStream inputStream;
	private OutputStream outputStream;
	private OutputStream errorStream;
	private Environment environment;
	private ExitCallback exitCallback;
	private ServerSession session;

	@Override
	public void destroy() {
		synchronized (threadLock) {
			if (commandThread == null) {
				return;
			}
			interruptRequested = true;
			commandThread.interrupt();
		}

	}

	/**
	 * Returns the stream for command error messages (stderr).
	 * 
	 * @return standard error stream or <code>null</code> if no special error
	 *         stream is available.
	 */
	protected OutputStream getErrorStream() {
		return this.errorStream;
	}

	@Override
	public void setErrorStream(OutputStream oes) {
		this.errorStream = oes;
	}

	@Override
	public void setExitCallback(ExitCallback exitCallback) {
		this.exitCallback = exitCallback;
	}

	/**
	 * Returns the stream for command input (stdin).
	 * 
	 * @return standard input stream or <code>null</code> if no input stream is
	 *         available.
	 */
	protected InputStream getInputStream() {
		return this.inputStream;
	}

	@Override
	public void setInputStream(InputStream is) {
		this.inputStream = is;
	}

	/**
	 * Returns the stream for command output (stdout).
	 * 
	 * @return standard output stream or <code>null</code> if no output stream
	 *         is available.
	 */
	protected OutputStream getOutputStream() {
		return this.outputStream;
	}

	@Override
	public void setOutputStream(OutputStream os) {
		this.outputStream = os;
	}

	/**
	 * Returns the session in which this command is executed.
	 * 
	 * @return server session for this command.
	 */
	protected ServerSession getSession() {
		return this.session;
	}

	@Override
	public void setSession(ServerSession session) {
		this.session = session;
	}

	@Override
	public void start(Environment environment) throws IOException {
		synchronized (threadLock) {
			if (commandThread != null) {
				throw new IllegalStateException(
						"Command has already been started.");
			}
			Runnable commandRunner = new Runnable() {
				@Override
				public void run() {
					int exitCode = -1;
					try {
						exitCode = AbstractCommand.this.run();
					} finally {
						synchronized (threadLock) {
							commandThread = null;
							interruptRequested = false;
							exitCallback.onExit(exitCode);
						}
					}
				}
			};
			this.environment = environment;
			this.commandThread = new Thread(commandRunner, getThreadName());
			this.commandThread.start();
		}
	}

	/**
	 * Returns the environment in which this command is executed. This is mainly
	 * used to access environment variables.
	 * 
	 * @return environment for this command.
	 */
	protected Environment getEnvironment() {
		return this.environment;
	}

	/**
	 * Returns the name used for the command thread. This can be overridden by
	 * child-classes in order to provide a more meaningful name.
	 * 
	 * @return name for the command thread.
	 */
	protected String getThreadName() {
		return "SSH-Command-Thread";
	}

	/**
	 * Returns true if the command thread should be stopped. This flag is set by
	 * {@link #destroy()}.
	 * 
	 * @return <code>true</code> if the command thread shall be stopped,
	 *         <code>false</code> otherwise.
	 */
	protected boolean isInterruptRequested() {
		synchronized (threadLock) {
			return interruptRequested;
		}
	}

	/**
	 * Writes an error message. The message is usually written to stderr. If
	 * stderr is not availble, the message is written to stdout. If stdout is
	 * not available either, the message is discarded and the passed status code
	 * is returned.
	 * 
	 * @param statusCode
	 *            statusCode to return from this method.
	 * @param message
	 *            message to write to stderr or stdout.
	 * @return the <code>statusCode</code> passed to this method.
	 */
	protected int errorMessage(int statusCode, String message) {
		OutputStream os = getErrorStream();
		if (os == null) {
			os = getOutputStream();
		}
		if (os == null) {
			return statusCode;
		}
		try {
			PrintStream printStream = new PrintStream(os, true, "UTF-8");
			printStream.println(message);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Unexpected exception: "
					+ e.getMessage(), e);
		}
		return statusCode;
	}

	/**
	 * Runs the actual command code. This method is called from the command
	 * thread. Child classes should implement their command logic in this
	 * method.
	 * 
	 * @return exit code to return to the client.
	 */
	protected abstract int run();
}
