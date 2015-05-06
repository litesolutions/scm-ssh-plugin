/*
 * Copyright 2012 aquenos GmbH.
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 */

package com.aquenos.scm.ssh.git;

import com.aquenos.scm.ssh.server.AbstractCommand;
import com.aquenos.scm.ssh.server.ScmSshServer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.util.FS;
import sonia.scm.config.ScmConfiguration;
import sonia.scm.repository.GitRepositoryHandler;
import sonia.scm.repository.PermissionType;
import sonia.scm.repository.PermissionUtil;
import sonia.scm.repository.RepositoryManager;
import sonia.scm.repository.spi.HookEventFacade;
import sonia.scm.user.User;
import sonia.scm.web.GitReceiveHook;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Factory that creates SSH commands on the server-side for handling Git
 * commands from remote hosts.
 * 
 * @author Sebastian Marsching
 * @author Sergi Baila
 */
@Singleton
public class GitCommandFactory implements CommandFactory {

	private GitRepositoryHandler repositoryHandler;
	private RepositoryManager repositoryManager;
	private ScmConfiguration configuration;
	private GitReceiveHook hook;

	/**
	 * Constructor. Meant to be called by Guice.
	 *  @param repositoryHandler
	 *            repository handler for Git repositories.
	 * @param repositoryManager
 *            SCM repository manager.
	 * @param configuration
	 * @param hookEventFacade
	 */
	@Inject
	public GitCommandFactory(GitRepositoryHandler repositoryHandler,
							 RepositoryManager repositoryManager,
							 ScmConfiguration configuration,
							 HookEventFacade hookEventFacade) {
		this.repositoryHandler = repositoryHandler;
		this.repositoryManager = repositoryManager;
		this.configuration = configuration;
		this.hook = new GitReceiveHook(hookEventFacade, repositoryHandler);
	}

	@Override
	public Command createCommand(final String commandString) {
		List<String> commandParts = parseCommandLine(commandString);
		if (commandParts == null) {
			return invalidCommandLine();
		} else if (commandParts.size() < 2) {
			return unsupportedCommand();
		} else if (commandParts.get(0).equals("git")) {
			if (commandParts.get(1).equals("upload-pack")
					|| commandParts.get(1).equals("receive-pack")) {
				commandParts.remove(0);
				commandParts.set(0, "git-" + commandParts.get(0));
			} else {
				return unsupportedCommand();
			}
		}

		String directory = null;
		boolean strictMode = false;
		int timeout = 0;
		boolean gitUploadPack = false;
		boolean gitReceivePack = false;
		if (commandParts.get(0).equals("git-upload-pack")) {
			gitUploadPack = true;
		} else if (commandParts.get(0).equals("git-receive-pack")) {
			gitReceivePack = true;
		} else {
			return unsupportedCommand();
		}
		for (int i = 1; i < commandParts.size(); i++) {
			if (gitUploadPack && commandParts.get(i).equals("--strict")) {
				strictMode = true;
				continue;
			} else if (gitUploadPack
					&& commandParts.get(i).startsWith("--timeout=")) {
				String timeoutString = commandParts.get(i).substring(
						"--timeout=".length());
				try {
					timeout = Integer.parseInt(timeoutString);
				} catch (NumberFormatException e) {
					return unsupportedParameter();
				}
				if (timeout < 0) {
					return unsupportedParameter();
				}
				continue;
			} else {
				if (directory != null) {
					return unsupportedParameter();
				} else {
					directory = commandParts.get(i);
				}
			}
		}
		if (directory == null) {
			return unsupportedCommand();
		}
		// Verify that directory is a valid path, without path traversal.
		// Backslashes will be converted to forward slashes (or vice-versa)
		// by the File class, so we convert to forward slashes first and then
		// check the path.
		directory = directory.replace('\\', '/');
		if (directory.startsWith("../") || directory.endsWith("/..")
				|| directory.contains("/../")) {
			return unsupportedParameter();
		}
		if (gitUploadPack) {
			return new GitUploadPackCommand(directory, strictMode, timeout);
		} else if (gitReceivePack) {
			return new GitReceivePackCommand(directory);
		} else {
			return unsupportedCommand();
		}
	}

	private static Command invalidCommandLine() {
		return new AbstractCommand() {
			@Override
			protected int run() {
				return errorMessage(-2, "Invalid command line.");
			}
		};
	}

	private static Command unsupportedCommand() {
		return new AbstractCommand() {
			@Override
			protected int run() {
				return errorMessage(-2, "Not a supported Git command.");
			}
		};
	}

	private static Command unsupportedParameter() {
		return new AbstractCommand() {
			@Override
			protected int run() {
				return errorMessage(-3,
						"Unsupported parameter specified for Git command.");
			}
		};
	}

	private static List<String> parseCommandLine(String commandLine) {
		// Parse the command line in a similar way like a shell would.
		// However, we simplify things by not supporting variables and
		// only allowing a single command. Any command issued by a
		// valid Git client will match these criteria anyway.
		StringReader reader = new StringReader(commandLine);
		LinkedList<String> args = new LinkedList<String>();
		try {
			int c;
			boolean inSingleQuotes = false;
			boolean inDoubleQuotes = false;
			boolean inEscapeSequence = false;
			StringBuilder sb = new StringBuilder();
			do {
				c = reader.read();
				if (c == 0) {
					// Unexpected null-byte. Probably someone is trying
					// something nasty.
					return null;
				} else if (c == -1) {
					// End of stream.
					break;
				}
				if (inSingleQuotes || inDoubleQuotes) {
					if (inEscapeSequence) {
						if ((c == '\'' && inSingleQuotes)
								|| (c == '"' && inDoubleQuotes) || c == '\\') {
							sb.append((char) c);
						} else {
							sb.append('\\');
							sb.append((char) c);
						}
						inEscapeSequence = false;
					} else {
						if (c == '\'' && inSingleQuotes) {
							inSingleQuotes = false;
						} else if (c == '"' && inDoubleQuotes) {
							inDoubleQuotes = false;
						} else if (c == '\\') {
							inEscapeSequence = true;
						} else {
							sb.append((char) c);
						}
					}
				} else {
					if (inEscapeSequence) {
						if (Character.isWhitespace((char) c) || c == '\\'
								|| c == '\'' || c == '"' || c == ';'
								|| c == '&' || c == '|' || c == '>' || c == '<'
								|| c == '(' || c == ')' || c == '`' || c == '{'
								|| c == '}' || c == '!' || c == '*' || c == '#') {
							sb.append((char) c);
						} else {
							sb.append('\\');
							sb.append((char) c);
						}
						inEscapeSequence = false;
					} else {
						if (c == '\\') {
							inEscapeSequence = true;
						} else if (c == '\'') {
							inSingleQuotes = true;
						} else if (c == '"') {
							inDoubleQuotes = true;
						} else if (c == ';' || c == '&' || c == '|' || c == '>'
								|| c == '<' || c == '(' || c == ')' || c == '`'
								|| c == '{' || c == '}' || c == '\n'
								|| c == '\r') {
							// These characters have special meanings in a
							// shell, but we do not support them.
							return null;
						} else if (Character.isWhitespace((char) c)) {
							// Next argument.
							if (sb.length() > 0) {
								args.add(sb.toString());
								sb.setLength(0);
							}
						} else if (c == '#') {
							// Treat this like an end of line.
							break;
						} else {
							sb.append((char) c);
						}
					}
				}
			} while (c != -1);
			if (inSingleQuotes || inDoubleQuotes) {
				// Unfinished quotes.
				return null;
			}
			if (sb.length() > 0) {
				args.add(sb.toString());
				sb.setLength(0);
			}
			return args;
		} catch (IOException e) {
			throw new RuntimeException("Unexpected IOException.", e);
		}
	}

	private abstract class AbstractGitCommand extends AbstractCommand {

		protected String directory;
		private boolean strictMode;
		protected Repository gitRepository;
		protected String username;
		protected String remoteHost;

		public AbstractGitCommand(String directory, boolean strictMode) {
			this.directory = directory;
			this.strictMode = strictMode;
		}

		@Override
		protected int run() {
			// Get subject from session and set it for this thread.
			Subject subject = getSession().getAttribute(
					ScmSshServer.SUBJECT_SESSION_ATTRIBUTE_KEY);
			if (subject == null) {
				return errorMessage(-6, "Internal error");
			}
			try {
				return subject.associateWith(new Callable<Integer>() {
					@Override
					public Integer call() throws Exception {
						return runWithSubject();
					}
				}).call();
			} catch (Exception e) {
				throw new RuntimeException(
						"Error while trying to execute Git command: "
								+ e.getMessage(), e);
			}
		}

		private int runWithSubject() {
			this.username = SecurityUtils.getSubject().getPrincipals()
					.oneByType(User.class).getId();
			SocketAddress remoteSocketAddress = getSession().getIoSession()
					.getRemoteAddress();
			if (remoteSocketAddress instanceof InetSocketAddress) {
				this.remoteHost = ((InetSocketAddress) remoteSocketAddress)
						.getHostName();
			} else {
				this.remoteHost = "unknown";
			}
			// Check path for path traversal has already been done by
			// GitCommandFactory, so we can just create the File instance.
			String directoryPath = this.directory;
			if (directoryPath.startsWith("/")) {
				directoryPath = directoryPath.substring(1);
			}
			String type = repositoryHandler.getType().getName();
			if (!directoryPath.startsWith(type + "/")) {
				return errorMessage(-4,
						"The requested repository does not exist.");
			}
			directoryPath = directoryPath.substring(type.length() + 1);
			File repositoryDir = new File(repositoryHandler.getConfig()
					.getRepositoryDirectory(), directoryPath);
			sonia.scm.repository.Repository scmRepository = repositoryManager
					.getFromUri(directory);
			if (scmRepository == null || !scmRepository.getType().equals("git")) {
				return errorMessage(-4,
						"The requested repository does not exist.");
			}
			try {
				FileKey key;
				if (strictMode) {
					key = FileKey.exact(repositoryDir, FS.DETECTED);
				} else {
					key = FileKey.lenient(repositoryDir, FS.DETECTED);
				}
				gitRepository = RepositoryCache.open(key, true);
			} catch (RepositoryNotFoundException e) {
				return errorMessage(-4,
						"The requested repository does not exist.");
			} catch (IOException e) {
				return errorMessage(-4,
						"Error while trying to open requested repository.");
			}
			boolean permitted;
			if (isWriteCommand()) {
				permitted = PermissionUtil.isWritable(configuration,
						scmRepository);
			} else {
				permitted = PermissionUtil.hasPermission(configuration,
						scmRepository, PermissionType.READ);
			}
			if (!permitted) {
				return errorMessage(-5, "Permission denied.");
			}
			// Repository request listeners are tied to HTTP request and
			// response, thus we cannot call them here.
			return runGitCommand();
		}

		protected abstract int runGitCommand();

		protected abstract boolean isWriteCommand();
	}

	private class GitReceivePackCommand extends AbstractGitCommand {

		public GitReceivePackCommand(String directory) {
			super(directory, true);
		}

		@Override
		protected int runGitCommand() {
			ReceivePack receivePack = new ReceivePack(gitRepository);
			receivePack.setPreReceiveHook(hook);
			receivePack.setPostReceiveHook(hook);
			receivePack.setRefLogIdent(new PersonIdent(username, username + "@"
					+ remoteHost));
			try {
				receivePack.receive(getInputStream(), getOutputStream(),
						getErrorStream());
			} catch (IOException e) {
				return -4;
			}
			return 0;
		}

		@Override
		protected boolean isWriteCommand() {
			return true;
		}

	}

	private class GitUploadPackCommand extends AbstractGitCommand {

		private int timeout;

		public GitUploadPackCommand(String directory, boolean strictMode,
				int timeout) {
			super(directory, strictMode);
			this.timeout = timeout;
		}

		@Override
		protected int runGitCommand() {
			UploadPack uploadPack = new UploadPack(gitRepository);
			uploadPack.setTimeout(timeout);
			try {
				uploadPack.upload(getInputStream(), getOutputStream(),
						getErrorStream());
			} catch (IOException e) {
				return -4;
			}
			return 0;
		}

		@Override
		protected boolean isWriteCommand() {
			return false;
		}

	}

}
