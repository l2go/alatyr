/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.loginserver.manager;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2Registry;
import com.l2jfree.contract.AdmissionResult;
import com.l2jfree.contract.SessionKey;
import com.l2jfree.contract.WorldPort;
import com.l2jfree.contract.WorldStatus;
import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.LoginModule;
import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.beans.FailedLoginAttempt;
import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.loginserver.security.PasswordHasher;
import com.l2jfree.loginserver.services.AccountsServices;
import com.l2jfree.loginserver.services.exception.AccountBannedException;
import com.l2jfree.loginserver.services.exception.AccountModificationException;
import com.l2jfree.loginserver.services.exception.AccountWrongPasswordException;
import com.l2jfree.loginserver.services.exception.HackingException;
import com.l2jfree.loginserver.services.exception.IPRestrictedException;
import com.l2jfree.loginserver.services.exception.MaintenanceException;
import com.l2jfree.loginserver.services.exception.MaturityException;
import com.l2jfree.tools.codec.Base64;
import com.l2jfree.tools.math.ScrambledKeyPair;
import com.l2jfree.tools.random.Rnd;
import com.l2jfree.tools.random.SecureRnd;

/**
 * This class handles login on loginserver.
 * It store connection for each account.
 * 
 * The ClientThread use LoginManager to :
 *  - store his connection identifier
 *  - retrieve basic information
 *  - delog an account
 */
public class LoginManager
{
	private static final Logger _log = LoggerFactory.getLogger(LoginManager.class);
	private static final Logger _logLogin = LoggerFactory.getLogger("login");
	private static final Logger _logLoginTries = LoggerFactory.getLogger("login.try");
	private static final Logger _logLoginFailed = LoggerFactory.getLogger("login.failed");
	
	private static final class SingletonHolder
	{
		private static final LoginManager INSTANCE = new LoginManager();
	}
	
	public static LoginManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	/** Authed Clients on LoginServer*/
	protected Map<String, L2Client> _loginServerClients = new ConcurrentHashMap<String, L2Client>();

	/**
	 * The client that held an account when a second login was refused. The refusal closes this client,
	 * not whoever is in the map when the account row comes back.
	 */
	private final Map<L2Client, L2Client> _occupiedLogin = new ConcurrentHashMap<L2Client, L2Client>();
	
	/** Serializes the automatic creation of accounts. */
	private final Object _accountCreation = new Object();
	
	/** Accounts that are in the world. Changed only under the lock of {@link #_loginServerClients}. */
	private final Set<String> _accountsInWorld = ConcurrentHashMap.newKeySet();
	
	/** Keep trace of login attempt for an inetadress*/
	private Map<InetAddress, FailedLoginAttempt> _hackProtection;
	
	private ScrambledKeyPair[] _keyPairs;
	
	protected byte[][] _blowfishKeys;
	
	private static final int BLOWFISH_KEYS = 20;
	
	private AccountsServices _service = null;
	
	public static enum AuthLoginResult
	{
		INVALID_PASSWORD,
		ACCOUNT_BANNED,
		ALREADY_ON_LS,
		ALREADY_ON_GS,
		AUTH_SUCCESS,
		SYSTEM_ERROR
	}
	
	/**
	 * Private constructor to avoid direct instantiation.
	 * Initialize a key generator.
	 */
	private LoginManager()
	{
		try
		{
			_log.info("LoginManager: initializing.");
			
			_hackProtection = new ConcurrentHashMap<InetAddress, FailedLoginAttempt>();
			
			_keyPairs = new ScrambledKeyPair[10];
			
			_service = L2Registry.getAccountsServices();
			
			KeyPairGenerator keygen = null;
			
			try
			{
				keygen = KeyPairGenerator.getInstance("RSA");
				RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4);
				keygen.initialize(spec);
			}
			catch (GeneralSecurityException e)
			{
				_log.error("Error in RSA setup:", e);
				_log.info("Server shutting down now");
				System.exit(1);
				return;
			}
			
			//generate the initial set of keys
			for (int i = 0; i < 10; i++)
			{
				_keyPairs[i] = new ScrambledKeyPair(keygen.generateKeyPair());
			}
			_log.info("LoginManager: Cached 10 KeyPairs for RSA communication");
			
			testCipher((RSAPrivateKey)_keyPairs[0].getPair().getPrivate());
			
			// Store keys for blowfish communication
			generateBlowFishKeys();
		}
		catch (GeneralSecurityException e)
		{
			_log.error("FATAL: Failed initializing LoginManager. Reason: " + e.getMessage(), e);
			System.exit(1);
		}
		
	}
	
	/**
	 * For tests: a manager that works on the given accounts service and has no RSA and Blowfish keys, so the admission
	 * rules can be tested without the key generation and without the database registry.
	 */
	public LoginManager(AccountsServices service)
	{
		_hackProtection = new ConcurrentHashMap<InetAddress, FailedLoginAttempt>();
		_keyPairs = new ScrambledKeyPair[0];
		_service = service;
		_blowfishKeys = new byte[0][];
	}
	
	/**
	 * This is mostly to force the initialization of the Crypto Implementation, avoiding it being done on runtime when its first needed.<BR>
	 * In short it avoids the worst-case execution time on runtime by doing it on loading.
	 * @param key Any private RSA Key just for testing purposes.
	 * @throws GeneralSecurityException if a underlying exception was thrown by the Cipher
	 */
	private void testCipher(RSAPrivateKey key) throws GeneralSecurityException
	{
		// avoid worst-case execution, KenM
		Cipher rsaCipher = Cipher.getInstance("RSA/ECB/nopadding");
		rsaCipher.init(Cipher.DECRYPT_MODE, key);
	}
	
	private void generateBlowFishKeys()
	{
		_blowfishKeys = new byte[BLOWFISH_KEYS][16];
		
		for (int i = 0; i < BLOWFISH_KEYS; i++)
		{
			for (int j = 0; j < _blowfishKeys[i].length; j++)
			{
				_blowfishKeys[i][j] = (byte)(Rnd.nextInt(255) + 1);
			}
		}
		_log.info("Stored " + _blowfishKeys.length + " keys for Blowfish communication");
	}
	
	/**
	 * @return Returns a random key
	 */
	public byte[] getBlowfishKey()
	{
		return _blowfishKeys[(int)(Math.random() * BLOWFISH_KEYS)];
	}
	
	/**
	 * 
	 * @param account
	 * @param client
	 * @return a SessionKey
	 */
	public SessionKey assignSessionKeyToLogin(String account, L2Client client)
	{
		SessionKey key;
		
		key =
				new SessionKey(SecureRnd.nextInt(Integer.MAX_VALUE), SecureRnd.nextInt(Integer.MAX_VALUE),
						SecureRnd.nextInt(Integer.MAX_VALUE), SecureRnd.nextInt(Integer.MAX_VALUE));
		_loginServerClients.put(account, client);
		return key;
	}
	
	public void removeAuthedLoginClient(String account)
	{
		_loginServerClients.remove(account);
	}
	
	/** Removes the entry of the account only if it belongs to this client. */
	public void removeAuthedLoginClient(String account, L2Client client)
	{
		_loginServerClients.remove(account, client);
	}
	
	public boolean isAccountInLoginServer(String account)
	{
		return _loginServerClients.containsKey(account);
	}
	
	public SessionKey assignSessionKeyToClient(String account, L2Client client)
	{
		SessionKey key;
		
		key =
				new SessionKey(SecureRnd.nextInt(Integer.MAX_VALUE), SecureRnd.nextInt(Integer.MAX_VALUE),
						SecureRnd.nextInt(Integer.MAX_VALUE), SecureRnd.nextInt(Integer.MAX_VALUE));
		_loginServerClients.put(account, client);
		return key;
	}
	
	/**
	 * Admits an account to the world: checks the key the client presented, publishes the account as in the world
	 * and drops its login-server session, all under the lock that guards the login-server map.
	 *
	 * @return the admission, or a refusal when the key does not match or the account is already in the world
	 */
	public AdmissionResult beginPlaySession(String account, SessionKey presented)
	{
		synchronized (_loginServerClients)
		{
			SessionKey stored = getKeyForAccount(account);
			if (stored == null || !stored.matches(presented, LoginConfig.SHOW_LICENCE))
				return AdmissionResult.refused();
			
			if (!PlaySessionAdmission.reserveGameServer(isAccountInWorld(account)))
				return AdmissionResult.refused();
			
			String host = getHostForAccount(account);
			_accountsInWorld.add(account);
			_loginServerClients.remove(account);
			return AdmissionResult.admitted(host);
		}
	}
	
	/**
	 * The account left the world, so it may log in again.
	 */
	public void endPlaySession(String account)
	{
		synchronized (_loginServerClients)
		{
			_accountsInWorld.remove(account);
		}
	}
	
	public boolean isAccountInWorld(String account)
	{
		return _accountsInWorld.contains(account);
	}
	
	/**
	 * 
	 * @param account
	 * @param password
	 * @param client
	 * @return true if validation succeed or false if we have technical problems
	 * @throws HackingException if we detect a hacking attempt
	 * @throws AccountBannedException if the use was banned
	 * @throws AccountWrongPasswordException if the password was wrong
	 */
	public AuthLoginResult tryAuthLogin(String account, String password, L2Client client)
			throws AccountBannedException, AccountWrongPasswordException, IPRestrictedException
	{
		AuthLoginResult ret = AuthLoginResult.INVALID_PASSWORD;
		
		try
		{
			// check auth
			if (loginValid(account, password, client))
			{
				ret = reserveLoginSlot(account, client);
				if (ret == AuthLoginResult.AUTH_SUCCESS || ret == AuthLoginResult.ALREADY_ON_LS)
				{
					Accounts acc = _service.getAccountById(account);
					// keep access level in the L2LoginClient
					client.setAccessLevel(acc.getAccessLevel());
					// keep last server choice
					client.setLastServerId(acc.getLastServerId());
					client.setAge(acc.getBirthYear(), acc.getBirthMonth(), acc.getBirthDay());
				}
			}
		}
		catch (NoSuchAlgorithmException e)
		{
			_log.error("could not check password:", e);
			ret = AuthLoginResult.SYSTEM_ERROR;
		}
		catch (UnsupportedEncodingException e)
		{
			_log.error("could not check password:", e);
			ret = AuthLoginResult.SYSTEM_ERROR;
		}
		catch (AccountModificationException e)
		{
			_log.warn("could not check password:", e);
			ret = AuthLoginResult.SYSTEM_ERROR;
		}
		catch (RuntimeException e)
		{
			_occupiedLogin.remove(client);
			throw e;
		}
		return ret;
	}

	/**
	 * Reserves the login slot, or remembers the client that already holds it. The account row is read
	 * after this returns, so the refusal must not look the occupant up again.
	 */
	AuthLoginResult reserveLoginSlot(String account, L2Client client)
	{
		// The world check and the login-server map share one lock with beginPlaySession.
		synchronized (_loginServerClients)
		{
			boolean onGameServer = isAccountInWorld(account);
			boolean onLoginServer = _loginServerClients.containsKey(account);
			if (!PlaySessionAdmission.mayAuthenticate(onGameServer, onLoginServer))
			{
				if (onGameServer)
				{
					_occupiedLogin.remove(client);
					return AuthLoginResult.ALREADY_ON_GS;
				}
				L2Client occupied = _loginServerClients.get(account);
				if (occupied != null)
					_occupiedLogin.put(client, occupied);
				else
					_occupiedLogin.remove(client);
				return AuthLoginResult.ALREADY_ON_LS;
			}
			_loginServerClients.put(account, client);
			_occupiedLogin.remove(client);
			return AuthLoginResult.AUTH_SUCCESS;
		}
	}

	/** The client captured with {@link #reserveLoginSlot}, once. Null when this login was not refused. */
	public L2Client takeOccupiedLogin(L2Client client)
	{
		return _occupiedLogin.remove(client);
	}
	
	public L2Client getAuthedClient(String account)
	{
		return _loginServerClients.get(account);
	}
	
	public SessionKey getKeyForAccount(String account)
	{
		L2Client client = _loginServerClients.get(account);
		if (client != null)
		{
			return client.getSessionKey();
		}
		return null;
	}
	
	public String getHostForAccount(String account)
	{
		L2Client client = getAuthedClient(account);
		
		return client != null ? client.getIp() : "-1";
	}
	
	/**
	 * Login is possible if the world is online, the number of players < max players of the world
	 * and the status of the world != STATUS_GM_ONLY, see {@link WorldAccess}.
	 * The player-count and GM-only conditions are not applied if the player is a GM
	 * @return false when the world is full and the player is not a GM
	 * @throws MaintenanceException when there is no world, or it is down
	 */
	public boolean isLoginPossible(int age, int access) throws MaintenanceException, MaturityException
	{
		WorldPort world = LoginModule.currentWorld();
		if (world == null)
			throw MaintenanceException.MAINTENANCE;
		
		WorldStatus status = world.status();
		WorldAccess.check(status, age, access, LoginConfig.GM_MIN);
		return WorldAccess.hasPlaceFor(status, access, LoginConfig.GM_MIN);
	}
	
	/**
	 * @return online player count of the world, 0 when there is no world
	 */
	public int getOnlinePlayerCount()
	{
		WorldPort world = LoginModule.currentWorld();
		return world == null ? 0 : world.status().onlinePlayers();
	}
	
	/**
	 * @return max allowed online players of the world, 0 when there is no world
	 */
	public int getMaxAllowedOnlinePlayers()
	{
		WorldPort world = LoginModule.currentWorld();
		return world == null ? 0 : world.status().maxPlayers();
	}
	
	/**
	 * 
	 * @param user
	 * @param banLevel
	 */
	public void setAccountAccessLevel(String account, int banLevel)
	{
		try
		{
			_service.changeAccountLevel(account, banLevel);
		}
		catch (AccountModificationException e)
		{
			_log.error("Could not set accessLevel for user: " + account, e);
		}
	}
	
	/**
	 * 
	 * @param user
	 * @param lastServerId
	 */
	public void setAccountLastServerId(String account, int lastServerId)
	{
		try
		{
			// only the column that changes: the rest of the row may have been changed since it was read
			Accounts acc = new Accounts(account);
			acc.setLastServerId(lastServerId);
			_service.updateGivenColumns(acc);
		}
		catch (AccountModificationException e)
		{
			_log.error("Could not set last server for user: " + account, e);
		}
	}
	
	/**
	 * 
	 * @param user
	 * @return true if a user is a GM account
	 */
	public boolean isGM(Accounts acc)
	{
		if (acc != null)
			return acc.getAccessLevel() >= LoginConfig.GM_MIN;
		else
			return false;
	}
	
	/**
	 * 
	 * @param user
	 * @return account if exist, null if not
	 */
	public Accounts getAccount(String user)
	{
		return _service.getAccountById(user);
	}
	
	/**
	 * <p>This method returns one of the 10 {@link ScrambledKeyPair}.</p>
	 * <p>One of them the renewed asynchronously using a {@link UpdateKeyPairTask} if necessary.</p>
	 * @return a scrambled keypair
	 */
	public ScrambledKeyPair getScrambledRSAKeyPair()
	{
		return _keyPairs[Rnd.nextInt(10)];
	}
	
	/**
	 * user name is not case sensitive any more
	 * @param user
	 * @param password
	 * @param address
	 * @return true if all operations succeed
	 * @throws NoSuchAlgorithmException if SHA is not supported
	 * @throws UnsupportedEncodingException if UTF-8 is not supported
	 * @throws AccountModificationException  if we were unable to modify the account
	 * @throws AccountBannedException  if account is banned
	 * @throws AccountWrongPasswordException if the password is wrong
	 */
	public boolean loginValid(String user, String password, L2Client client) throws NoSuchAlgorithmException,
			UnsupportedEncodingException, AccountModificationException, AccountBannedException,
			AccountWrongPasswordException, IPRestrictedException
	{
		InetAddress address = client.getInetAddress();
		if (BanManager.getInstance().isRestrictedAddress(address))
			throw new IPRestrictedException();
		else if (BanManager.getInstance().isBannedAddress(address))
			throw new IPRestrictedException(BanManager.getInstance().getBanExpiry(address));
		// player disconnected meanwhile
		if (address == null)
			return false;
		return loginValid(user, password, address);
	}
	
	/**
	 * user name is not case sensitive any more
	 * @param user
	 * @param password
	 * @param address
	 * @return true if all operations succeed
	 * @throws NoSuchAlgorithmException if SHA is not supported
	 * @throws UnsupportedEncodingException if UTF-8 is not supported
	 * @throws AccountModificationException  if we were unable to modify the account
	 * @throws AccountBannedException  if account is banned
	 * @throws AccountWrongPasswordException if the password is wrong
	 */
	public boolean loginValid(String user, String password, InetAddress address) throws NoSuchAlgorithmException,
			UnsupportedEncodingException, AccountModificationException, AccountBannedException,
			AccountWrongPasswordException
	{
		_logLoginTries.info("User trying to connect  '" + user + "' "
				+ (address == null ? "null" : address.getHostAddress()));
		
		// o find Account
		// -------------
		Accounts acc = _service.getAccountById(user);
		
		// If account is not found
		// try to create it if AUTO_CREATE_ACCOUNTS is activated
		// or return false
		// ------------------------------------------------------
		if (acc == null)
		{
			if (handleAccountNotFound(user, address, password))
				return true;
			else
				throw new AccountWrongPasswordException(user);
		}
		// If account is found
		// check ban state
		// check password and update last ip/last active
		// ---------------------------------------------
		else
		{
			// check the account is not ban
			if (acc.getAccessLevel() < 0)
			{
				throw new AccountBannedException(user);
			}
			try
			{
				checkPassword(password, acc);
				// Write only what a login changes. The row read above may be stale by now: a ban set meanwhile
				// must not be written back. The password is a separate compare-and-swap, so a newer password
				// stored after this hash was read is not replaced by the upgrade.
				Accounts seen = new Accounts(acc.getLogin());
				seen.setLastactive(new BigDecimal(System.currentTimeMillis()));
				if (address != null)
				{
					seen.setLastIp(address.getHostAddress());
				}
				_service.updateGivenColumns(seen);
				if (PasswordHasher.needsRehash(acc.getPassword()))
				{
					String upgraded = PasswordHasher.hash(password);
					if (!_service.replacePasswordIfUnchanged(acc.getLogin(), acc.getPassword(), upgraded))
					{
						_log.info("Password of account " + user
								+ " changed before the hash upgrade; the newer password was kept.");
					}
				}
				handleGoodLogin(user, address);
			}
			// If password are different
			// -------------------------
			catch (AccountWrongPasswordException e)
			{
				handleBadLogin(user, password, address);
				throw e;
			}
		}
		
		return true;
	}
	
	/**
	 * @param user
	 * @param address
	 */
	private void handleGoodLogin(String user, InetAddress address)
	{
		// for long running servers, this should prevent blocking
		// of users that mistype their passwords once every day :)
		if (address != null)
		{
			_hackProtection.remove(address);
		}
		if (_logLogin.isDebugEnabled())
			_logLogin.debug("login successfull for '" + user + "' "
					+ (address == null ? "null" : address.getHostAddress()));
	}
	
	/**
	 * 
	 * If login are different, increment hackProtection counter. It's maybe a hacking attempt
	 * 
	 * @param user
	 * @param password
	 * @param address
	 */
	void handleBadLogin(String user, String password, InetAddress address)
	{
		_logLoginFailed.info("login failed for user : '" + user + "' "
				+ (address == null ? "null" : address.getHostAddress()));
		
		// In special case, adress is null, so this protection is useless
		if (address != null)
		{
			// the logins of one address arrive from several threads at once
			int failedCount = _hackProtection.compute(address, (key, failedAttempt) -> {
				if (failedAttempt == null)
					return new FailedLoginAttempt(address, password);
				failedAttempt.increaseCounter(password);
				return failedAttempt;
			}).getCount();
			
			if (failedCount >= LoginConfig.LOGIN_TRY_BEFORE_BAN)
			{
				_log.info("Temporary auto-ban for " + address.getHostAddress() + " (" + LoginConfig.LOGIN_BLOCK_AFTER_BAN
						+ " seconds, " + failedCount + " login tries)");
				BanManager.getInstance().addBanForAddress(address, LoginConfig.LOGIN_BLOCK_AFTER_BAN * 1000);
			}
		}
	}
	
	/**
	 * @param hash
	 * @param acc
	 * @throws AccountWrongPasswordException if password is wrong
	 */
	private void checkPassword(String password, Accounts acc) throws AccountWrongPasswordException
	{
		if (_log.isDebugEnabled())
			_log.debug("account exists");
		
		if (!PasswordHasher.verify(password, acc.getPassword()))
			throw new AccountWrongPasswordException(acc.getLogin());
	}
	
	/**
	 * @param user
	 * @param address
	 * @param hash
	 * @return true if accounts was successfully created or false is AUTO_CREATE_ACCOUNTS = false or creation failed
	 * @throws AccountModificationException
	 */
	private boolean handleAccountNotFound(String user, InetAddress address, String password)
			throws AccountModificationException
	{
		Accounts acc;
		if (LoginConfig.AUTO_CREATE_ACCOUNTS)
		{
			if ((user.length() >= 2) && (user.length() <= 14))
			{
				// the creation is an upsert: of two clients that create the same name at once, the second would
				// replace the password of the first
				synchronized (_accountCreation)
				{
					if (_service.getAccountById(user) != null)
						return false;
					
					acc =
							new Accounts(user, PasswordHasher.hash(password), new BigDecimal(System.currentTimeMillis()), 0,
									0, 1900, 1, 1, (address == null ? null : address.getHostAddress()));
					_service.addOrUpdateAccount(acc);
				}
				
				_logLogin.info("Account created: " + user);
				_log.info("An account was newly created: " + user);
				
				return true;
				
			}
			_logLogin.warn("Invalid username creation/use attempt: " + user);
			return false;
		}
		_logLogin.warn("No such account exists: " + user);
		return false;
	}
	
	/** @return how many failed logins are counted against the address, 0 if none */
	int failedLoginCount(InetAddress address)
	{
		FailedLoginAttempt attempt = _hackProtection.get(address);
		return attempt == null ? 0 : attempt.getCount();
	}
}
