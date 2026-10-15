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
package com.l2jfree.gameserver;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.contract.AdmissionResult;
import com.l2jfree.contract.LoginPort;
import com.l2jfree.contract.SessionKey;
import com.l2jfree.gameserver.network.AuthLoginGuard;

/**
 * Which game client plays which account: the clients that wait for the login to admit them, and the clients in the
 * world.
 * <p>
 * The login module is called without the lock, so a slow login never blocks the other packet threads. A client that
 * disconnects while it waits is logged out by the admitting thread once the login answered, so the login hears of
 * every admission exactly once and of the leave exactly once.
 *
 * @param <C> the game client
 */
public final class PlaySessions<C>
{
	private static final Logger _log = LoggerFactory.getLogger(PlaySessions.class);
	
	/** What happens to a game client. */
	public interface Clients<C>
	{
		/** The client claims the account, before the login is asked. */
		void claim(C client, String account);
		
		/** The login admitted the client, which the login saw connect from {@code hostAddress}. */
		void admit(C client, SessionKey key, String hostAddress);
		
		/** The login refused the client. */
		void refuse(C client);
		
		/** Disconnect the client. */
		void close(C client);
	}
	
	private final LoginPort _login;
	private final Clients<C> _clients;
	private final Object _accountLock = new Object();
	private final Map<String, C> _waitingClients = new HashMap<String, C>();
	private final Map<String, C> _accountsInWorld = new HashMap<String, C>();
	
	public PlaySessions(LoginPort login, Clients<C> clients)
	{
		_login = login;
		_clients = clients;
	}
	
	/**
	 * Asks the login to admit the client to the account, and lets it in on success.
	 * 
	 * @return false when the account is already waiting or in the world, so the login is not asked; true when the
	 *         claim was accepted, whatever the login answered
	 */
	public boolean authenticate(String account, C client, SessionKey key)
	{
		synchronized (_accountLock)
		{
			if (!AuthLoginGuard.canClaim(_waitingClients.containsKey(account), _accountsInWorld.containsKey(account)))
				return false;
			
			_clients.claim(client, account);
			_waitingClients.put(account, client);
		}
		
		AdmissionResult result;
		try
		{
			result = _login.admit(account, key);
		}
		catch (RuntimeException e)
		{
			_log.warn("The login module failed to answer the admission of " + account, e);
			result = AdmissionResult.refused();
		}
		
		if (!result.admitted())
		{
			final boolean stillWaiting;
			synchronized (_accountLock)
			{
				stillWaiting = _waitingClients.remove(account, client);
			}
			
			if (stillWaiting)
			{
				_log.warn("session key is not correct. closing connection");
				_clients.refuse(client);
			}
			return true;
		}
		
		final boolean loggedOutMeanwhile;
		C previous = null;
		synchronized (_accountLock)
		{
			loggedOutMeanwhile = !_waitingClients.remove(account, client);
			if (!loggedOutMeanwhile)
				previous = _accountsInWorld.put(account, client);
		}
		
		if (loggedOutMeanwhile)
		{
			// the client went away while the login decided: the admission must not outlive it
			leave(account);
			return true;
		}
		
		_clients.admit(client, key, result.clientHost());
		
		if (previous != null && previous != client)
			_clients.close(previous);
		return true;
	}
	
	/**
	 * The client disconnected. The login hears of it only when this client was in the world: a client that was refused,
	 * that never got in, or that logged out before, changes nothing for the login. A client that waits for the
	 * admission is logged out by the admitting thread.
	 */
	public void logout(String account, C client)
	{
		if (account == null || account.isEmpty())
			return;
		
		boolean wasInWorld = false;
		synchronized (_accountLock)
		{
			if (_waitingClients.get(account) == client)
				_waitingClients.remove(account);
			
			if (_accountsInWorld.get(account) == client)
			{
				_accountsInWorld.remove(account);
				wasInWorld = true;
			}
		}
		
		if (wasInWorld)
			leave(account);
	}
	
	/** Disconnects the clients of the account: it logged in again, or the login asks for it. */
	public void kick(String account)
	{
		final C inWorld;
		final C waiting;
		synchronized (_accountLock)
		{
			inWorld = _accountsInWorld.get(account);
			waiting = _waitingClients.get(account);
		}
		
		if (inWorld != null)
			_clients.close(inWorld);
		
		if (waiting != null)
			_clients.close(waiting);
	}
	
	public boolean isInWorld(String account)
	{
		synchronized (_accountLock)
		{
			return _accountsInWorld.containsKey(account);
		}
	}
	
	public boolean isWaiting(String account)
	{
		synchronized (_accountLock)
		{
			return _waitingClients.containsKey(account);
		}
	}
	
	/** @return the number of accounts in the world */
	public int inWorldCount()
	{
		synchronized (_accountLock)
		{
			return _accountsInWorld.size();
		}
	}
	
	private void leave(String account)
	{
		try
		{
			_login.leave(account);
		}
		catch (RuntimeException e)
		{
			_log.warn("The login module failed to close the session of " + account, e);
		}
	}
}
