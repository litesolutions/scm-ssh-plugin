/**
 * Copyright (c) 2012, Sebastian Marsching (modified for use without passwords)
 * 
 * Heavily based on ScmRealm.java
 * Copyright (c) 2010, Sebastian Sdorra
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of SCM-Manager; nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * http://bitbucket.org/sdorra/scm-manager
 *
 */

package com.aquenos.scm.ssh.auth;

//~--- non-JDK imports --------------------------------------------------------

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.shiro.authc.AccountException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.pam.UnsupportedTokenException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sonia.scm.HandlerEvent;
import sonia.scm.cache.Cache;
import sonia.scm.cache.CacheManager;
import sonia.scm.config.ScmConfiguration;
import sonia.scm.group.Group;
import sonia.scm.group.GroupManager;
import sonia.scm.group.GroupNames;
import sonia.scm.repository.Permission;
import sonia.scm.repository.PermissionType;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryDAO;
import sonia.scm.repository.RepositoryListener;
import sonia.scm.repository.RepositoryManager;
import sonia.scm.security.RepositoryPermission;
import sonia.scm.security.Role;
import sonia.scm.user.User;
import sonia.scm.user.UserDAO;
import sonia.scm.user.UserException;
import sonia.scm.user.UserListener;
import sonia.scm.user.UserManager;
import sonia.scm.util.Util;
import sonia.scm.web.security.AuthenticationResult;
import sonia.scm.web.security.AuthenticationState;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Modified version of {@link sonia.scm.security.ScmRealm}. This realm uses a
 * public key instead of a password for authenticating a user. The actual
 * authentication process is delegated to {@link PublicKeyAuthenticator}.
 * 
 * @author Sebastian Sdorra
 * @author Sebastian Marsching
 */
@Singleton
public class ScmPublicKeyRealm extends AuthorizingRealm implements
		RepositoryListener, UserListener {

	/** Field description */
	public static final String NAME = "scm-git-ssh-public-key";

	/** Field description */
	private static final String CACHE_NAME = ScmPublicKeyRealm.class.getName()
			+ "#cache";

	/**
	 * the logger for ScmRealm
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(ScmPublicKeyRealm.class);

	// ~--- constructors
	// ---------------------------------------------------------

	/**
	 * Constructs ...
	 * 
	 * 
	 * 
	 * @param configuration
	 * @param cacheManager
	 * @param userManager
	 * @param groupManager
	 * @param repositoryManager
	 * @param repositoryDAO
	 * @param userDAO
	 * @param authenticator
	 * @param requestProvider
	 * @param responseProvider
	 */
	@Inject
	public ScmPublicKeyRealm(ScmConfiguration configuration,
			CacheManager cacheManager, UserManager userManager,
			GroupManager groupManager, RepositoryManager repositoryManager,
			RepositoryDAO repositoryDAO, UserDAO userDAO,
			PublicKeyAuthenticator authenticator) {
		this.configuration = configuration;
		this.userManager = userManager;
		this.groupManager = groupManager;
		this.repositoryDAO = repositoryDAO;
		this.userDAO = userDAO;
		this.authenticator = authenticator;

		// init cache
		this.cache = cacheManager.getCache(String.class,
				AuthorizationInfo.class, CACHE_NAME);

		// set token class
		setAuthenticationTokenClass(PublicKeyToken.class);

		// use own custom caching
		setCachingEnabled(false);
		setAuthenticationCachingEnabled(false);
		setAuthorizationCachingEnabled(false);

		// set components
		setPermissionResolver(new RepositoryPermissionResolver());

		// add listeners for caching
		userManager.addListener(this);
		repositoryManager.addListener(this);
	}

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param repository
	 * @param event
	 */
	@Override
	public void onEvent(Repository repository, HandlerEvent event) {
		if (event.isPost()) {

			if (logger.isDebugEnabled()) {
				logger.debug("clear cache, because repository {} has changed",
						repository.getName());
			}

			cache.clear();
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param user
	 * @param event
	 */
	@Override
	public void onEvent(User user, HandlerEvent event) {
		if (event.isPost()) {
			if (logger.isDebugEnabled()) {
				logger.debug(
						"clear cache of user {}, because user properties have changed",
						user.getName());
			}

			cache.remove(user.getId());
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param token
	 * 
	 * @param authToken
	 * 
	 * @return
	 * 
	 * @throws AuthenticationException
	 */
	@Override
	protected AuthenticationInfo doGetAuthenticationInfo(
			AuthenticationToken authToken) throws AuthenticationException {
		if (!(authToken instanceof PublicKeyToken)) {
			throw new UnsupportedTokenException("PublicKeyToken is required");
		}

		PublicKeyToken token = (PublicKeyToken) authToken;

		AuthenticationInfo info = null;
		AuthenticationResult result = authenticator.authenticate(
				token.getUsername(), token.getPublicKey());

		if ((result != null)
				&& (AuthenticationState.SUCCESS == result.getState())) {
			info = createAuthenticationInfo(token, result);
		} else if ((result != null)
				&& (AuthenticationState.NOT_FOUND == result.getState())) {
			throw new UnknownAccountException("unknown account ".concat(token
					.getUsername()));
		} else {
			throw new AccountException("authentication failed");
		}

		return info;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param principals
	 * 
	 * @return
	 */
	@Override
	protected AuthorizationInfo doGetAuthorizationInfo(
			PrincipalCollection principals) {
		User user = principals.oneByType(User.class);

		AuthorizationInfo info = cache.get(user.getId());

		if (info == null) {
			if (logger.isTraceEnabled()) {
				logger.trace("collect AuthorizationInfo for user {}",
						user.getName());
			}

			GroupNames groups = principals.oneByType(GroupNames.class);

			info = createAuthorizationInfo(user, groups);
			cache.put(user.getId(), info);
		} else if (logger.isTraceEnabled()) {
			logger.trace("retrieve AuthorizationInfo for user {} from cache",
					user.getName());
		}

		return info;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param ar
	 * 
	 * @return
	 */
	private Set<String> authenticate(AuthenticationResult ar) {
		Set<String> groupSet = null;
		User user = ar.getUser();

		try {
			groupSet = createGroupSet(ar);

			// check for admin user
			checkForAuthenticatedAdmin(user, groupSet);

			// store user
			User dbUser = userDAO.get(user.getName());

			if (dbUser != null) {
				checkDBForAdmin(user, dbUser);
				checkDBForActive(user, dbUser);
			}

			// we assume that the user has logged in through the web-interface
			// before,
			// thus we ommit the creation process.

			if (user.isActive()) {

				if (logger.isDebugEnabled()) {
					logGroups(user, groupSet);
				}

			} else {

				String msg = "user ".concat(user.getName()).concat(
						" is deactivated");

				if (logger.isWarnEnabled()) {
					logger.warn(msg);
				}

				throw new DisabledAccountException(msg);

			}
		} catch (Exception ex) {
			logger.error("authentication failed", ex);

			throw new AuthenticationException("authentication failed", ex);
		}

		return groupSet;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param user
	 * @param dbUser
	 */
	private void checkDBForActive(User user, User dbUser) {

		// user is deactivated by database
		if (!dbUser.isActive()) {
			if (logger.isDebugEnabled()) {
				logger.debug(
						"user {} is marked as deactivated by local database",
						user.getName());
			}

			user.setActive(false);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param user
	 * @param dbUser
	 * 
	 * @throws IOException
	 * @throws UserException
	 */
	private void checkDBForAdmin(User user, User dbUser) throws UserException,
			IOException {

		// if database user is an admin, set admin for the current user
		if (dbUser.isAdmin()) {
			if (logger.isDebugEnabled()) {
				logger.debug(
						"user {} of type {} is marked as admin by local database",
						user.getName(), user.getType());
			}

			user.setAdmin(true);
		}

		// modify existing user, copy properties except password and admin
		if (user.copyProperties(dbUser, false)) {
			userManager.modify(dbUser);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param user
	 * @param groupSet
	 */
	private void checkForAuthenticatedAdmin(User user, Set<String> groupSet) {
		if (!user.isAdmin()) {
			user.setAdmin(isAdmin(user, groupSet));

			if (logger.isDebugEnabled() && user.isAdmin()) {
				logger.debug("user {} is marked as admin by configuration",
						user.getName());
			}
		} else if (logger.isDebugEnabled()) {
			logger.debug("authenticator {} marked user {} as admin",
					user.getType(), user.getName());
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param user
	 * @param groups
	 * 
	 * @return
	 */
	private List<org.apache.shiro.authz.Permission> collectRepositoryPermissions(
			User user, GroupNames groups) {
		List<org.apache.shiro.authz.Permission> permissions = Lists
				.newArrayList();

		for (Repository repository : repositoryDAO.getAll()) {
			if (logger.isTraceEnabled()) {
				logger.trace(
						"collect permissions for repository {} and user {}",
						repository.getName(), user.getName());
			}

			collectRepositoryPermissions(permissions, repository, user, groups);
		}

		return permissions;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param permissions
	 * @param repository
	 * @param user
	 * @param groups
	 */
	private void collectRepositoryPermissions(
			List<org.apache.shiro.authz.Permission> permissions,
			Repository repository, User user, GroupNames groups) {
		List<Permission> repositoryPermissions = repository.getPermissions();

		if (Util.isNotEmpty(repositoryPermissions)) {

			for (Permission permission : repositoryPermissions) {
				if (isUserPermission(user, groups, permission)) {
					RepositoryPermission rp = new RepositoryPermission(
							repository, permission.getType());

					if (logger.isTraceEnabled()) {
						logger.trace(
								"add repository permission {} for user {}", rp,
								user.getName());
					}

					permissions.add(rp);
				}
			}
		} else if (logger.isTraceEnabled()) {
			logger.trace("repository {} has not permission entries",
					repository.getName());
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param token
	 * @param result
	 * 
	 * @return
	 */
	private AuthenticationInfo createAuthenticationInfo(PublicKeyToken token,
			AuthenticationResult result) {
		User user = result.getUser();
		Collection<String> groups = authenticate(result);

		SimplePrincipalCollection collection = new SimplePrincipalCollection();

		/*
		 * the first (primary) principal should be a unique identifier
		 */
		collection.add(user.getId(), NAME);
		collection.add(user, NAME);
		collection.add(new GroupNames(groups), NAME);

		return new SimpleAuthenticationInfo(collection, token.getPublicKey());
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param user
	 * @param groups
	 * 
	 * @return
	 */
	private AuthorizationInfo createAuthorizationInfo(User user,
			GroupNames groups) {
		Set<String> roles = Sets.newHashSet();
		List<org.apache.shiro.authz.Permission> permissions = null;

		roles.add(Role.USER);

		if (user.isAdmin()) {
			if (logger.isDebugEnabled()) {
				logger.debug("grant admin role for user {}", user.getName());
			}

			roles.add(Role.ADMIN);
			permissions = Lists.newArrayList();
			permissions.add(new RepositoryPermission(
					RepositoryPermission.WILDCARD, PermissionType.OWNER));
		} else {
			permissions = collectRepositoryPermissions(user, groups);
		}

		SimpleAuthorizationInfo info = new SimpleAuthorizationInfo(roles);

		info.addObjectPermissions(permissions);

		return info;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param ar
	 * 
	 * @return
	 */
	private Set<String> createGroupSet(AuthenticationResult ar) {
		Set<String> groupSet = Sets.newHashSet();

		// load external groups
		Collection<String> extGroups = ar.getGroups();

		if (extGroups != null) {
			groupSet.addAll(extGroups);
		}

		// load internal groups
		loadGroups(ar.getUser(), groupSet);

		return groupSet;
	}

	/**
	 * Method description
	 * 
	 * 
	 * 
	 * @param user
	 * @param groupSet
	 */
	private void loadGroups(User user, Set<String> groupSet) {
		Collection<Group> groupCollection = groupManager
				.getGroupsForMember(user.getName());

		if (groupCollection != null) {
			for (Group group : groupCollection) {
				groupSet.add(group.getName());
			}
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param user
	 * @param groups
	 */
	private void logGroups(User user, Set<String> groups) {
		StringBuilder msg = new StringBuilder("user ");

		msg.append(user.getName());

		if (Util.isNotEmpty(groups)) {
			msg.append(" is member of ");

			Joiner.on(", ").appendTo(msg, groups);
		} else {
			msg.append(" is not a member of a group");
		}

		logger.debug(msg.toString());
	}

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * 
	 * 
	 * @param user
	 * @param groups
	 * @return
	 */
	private boolean isAdmin(User user, Collection<String> groups) {
		boolean result = false;
		Set<String> adminUsers = configuration.getAdminUsers();

		if (adminUsers != null) {
			result = adminUsers.contains(user.getName());
		}

		if (!result) {
			Set<String> adminGroups = configuration.getAdminGroups();

			if (adminGroups != null) {
				result = Util.containsOne(adminGroups, groups);
			}
		}

		return result;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param user
	 * @param groups
	 * @param perm
	 * 
	 * @return
	 */
	private boolean isUserPermission(User user, GroupNames groups,
			Permission perm) {
		// J-
		return (perm.isGroupPermission() && groups.contains(perm.getName()))
				|| ((!perm.isGroupPermission()) && user.getName().equals(
						perm.getName()));
		// J+
	}

	// ~--- fields
	// ---------------------------------------------------------------

	/** Field description */
	private PublicKeyAuthenticator authenticator;

	/** Field description */
	private Cache<String, AuthorizationInfo> cache;

	/** Field description */
	private ScmConfiguration configuration;

	/** Field description */
	private GroupManager groupManager;

	/** Field description */
	private RepositoryDAO repositoryDAO;

	/** Field description */
	private UserDAO userDAO;

	/** Field description */
	private UserManager userManager;
}
