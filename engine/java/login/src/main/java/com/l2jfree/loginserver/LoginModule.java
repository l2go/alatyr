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
package com.l2jfree.loginserver;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2Registry;
import com.l2jfree.contract.AdmissionResult;
import com.l2jfree.contract.LoginPort;
import com.l2jfree.contract.SessionKey;
import com.l2jfree.contract.WorldPort;
import com.l2jfree.loginserver.manager.BanManager;
import com.l2jfree.loginserver.manager.LoginManager;
import com.l2jfree.loginserver.network.L2ClientSelectorThread;

/**
 * The login module of the platform: accounts, password check, session keys, and the login port.
 * <p>
 * The platform starts it in two steps. {@link #prepare()} loads the configuration, opens the database pool,
 * migrates the schema, and creates the managers, but opens no port. {@link #start(WorldPort)} receives the world
 * and opens the login port, so a client that reaches the login finds a world.
 * <p>
 * The world reaches the login module through {@link LoginPort}, which this class implements.
 */
public final class LoginModule implements LoginPort
{
	private static final Logger _log = LoggerFactory.getLogger(LoginModule.class);
	
	private static LoginModule _instance;
	
	private volatile WorldPort _world;
	
	/**
	 * Prepares the module. A second call returns the module that the first call prepared.
	 */
	public static synchronized LoginModule prepare()
	{
		if (_instance != null)
			return _instance;
		
		// Initialize config
		LoginConfig.load();
		
		// Initialize JDBC services and migrate the schema
		L2Registry.loadRegistry();
		
		// Initialize LoginManager
		LoginManager.getInstance();
		
		// Initialize ban list
		BanManager.getInstance();
		
		_instance = new LoginModule();
		return _instance;
	}
	
	/**
	 * @return the prepared module, or null before {@link #prepare()}
	 */
	public static synchronized LoginModule getInstance()
	{
		return _instance;
	}
	
	/**
	 * @return the world the module was started with, or null when the module is not started
	 */
	public static WorldPort currentWorld()
	{
		LoginModule module = getInstance();
		return module == null ? null : module._world;
	}
	
	private LoginModule()
	{
	}
	
	/**
	 * Connects the world and opens the login port.
	 * 
	 * @throws IOException when the login port cannot be opened
	 */
	public void start(WorldPort world) throws IOException
	{
		Objects.requireNonNull(world, "world");
		if (_world != null)
			throw new IllegalStateException("The login module is already started");
		
		L2ClientSelectorThread selector = L2ClientSelectorThread.getInstance();
		selector.openServerSocket(InetAddress.getByName(LoginConfig.LOGIN_SERVER_HOSTNAME), LoginConfig.LOGIN_SERVER_PORT);
		_world = world;
		selector.start();
		
		_log.info("Login Server ready on " + LoginConfig.LOGIN_SERVER_HOSTNAME + ":" + LoginConfig.LOGIN_SERVER_PORT);
	}
	
	/**
	 * @return the world, or null until {@link #start(WorldPort)}
	 */
	public WorldPort world()
	{
		return _world;
	}
	
	@Override
	public AdmissionResult admit(String account, SessionKey key)
	{
		AdmissionResult result = LoginManager.getInstance().beginPlaySession(account, key);
		if (_log.isDebugEnabled())
			_log.debug("Admission of " + account + ": " + (result.admitted() ? "OK" : "refused"));
		return result;
	}
	
	@Override
	public void leave(String account)
	{
		LoginManager.getInstance().endPlaySession(account);
		if (_log.isDebugEnabled())
			_log.debug("Account " + account + " left the world");
	}
	
	@Override
	public void changeAccessLevel(String account, int level)
	{
		try
		{
			LoginManager.getInstance().setAccountAccessLevel(account, level);
			_log.info("Changed " + account + " access level to " + level);
		}
		catch (Exception e)
		{
			_log.warn("Access level could not be changed. Reason: ", e);
		}
	}
}
