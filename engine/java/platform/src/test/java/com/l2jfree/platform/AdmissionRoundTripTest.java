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
package com.l2jfree.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.l2jfree.contract.AdmissionResult;
import com.l2jfree.contract.LoginPort;
import com.l2jfree.contract.SessionKey;
import com.l2jfree.gameserver.PlaySessions;
import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.manager.LoginManager;
import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.loginserver.services.AccountsServices;

/**
 * The real admission of the login module behind the real play sessions of the world, wired as the platform wires them:
 * what the two processes of the 2.x line did over a socket with PlayerAuthRequest, PlayerInGame, and PlayerLogout. Only
 * the platform module sees both sides, so the test lives here.
 */
class AdmissionRoundTripTest
{
	private final List<String> _events = new ArrayList<String>();
	private boolean _showLicence;
	private LoginManager _login;
	private PlaySessions<String> _world;
	
	@BeforeEach
	void wire()
	{
		_showLicence = LoginConfig.SHOW_LICENCE;
		_login = new LoginManager(mock(AccountsServices.class));
		_world = new PlaySessions<String>(new LoginPort() {
			@Override
			public AdmissionResult admit(String account, SessionKey key)
			{
				return _login.beginPlaySession(account, key);
			}
			
			@Override
			public void leave(String account)
			{
				_login.endPlaySession(account);
			}
			
			@Override
			public void changeAccessLevel(String account, int level)
			{
				_login.setAccountAccessLevel(account, level);
			}
		}, new PlaySessions.Clients<String>() {
			@Override
			public void claim(String client, String account)
			{
				_events.add("claim " + client);
			}
			
			@Override
			public void admit(String client, SessionKey key, String hostAddress)
			{
				_events.add("admit " + client + " from " + hostAddress);
			}
			
			@Override
			public void refuse(String client)
			{
				_events.add("refuse " + client);
			}
			
			@Override
			public void close(String client)
			{
				_events.add("close " + client);
			}
		});
	}
	
	@AfterEach
	void restoreTheConfiguration()
	{
		LoginConfig.SHOW_LICENCE = _showLicence;
	}
	
	@Test
	void theKeyFromTheLoginAdmitsTheClientIntoTheWorldOnce()
	{
		SessionKey key = loggedIn("alice", "192.0.2.7");
		
		_world.authenticate("alice", "client-1", key);
		
		assertThat(_events).containsExactly("claim client-1", "admit client-1 from 192.0.2.7");
		assertThat(_world.isInWorld("alice")).isTrue();
		assertThat(_login.isAccountInLoginServer("alice")).isFalse();
		
		// the key is used up: a second client with the same key does not get in
		_events.clear();
		_world.authenticate("alice", "client-2", key);
		assertThat(_events).doesNotContain("admit client-2 from 192.0.2.7");
		assertThat(_login.isAccountInWorld("alice")).isTrue();
	}
	
	@Test
	void leavingTheWorldFreesTheAccountOnTheLogin()
	{
		SessionKey key = loggedIn("alice", "192.0.2.7");
		_world.authenticate("alice", "client-1", key);
		
		_world.logout("alice", "client-1");
		
		assertThat(_world.isInWorld("alice")).isFalse();
		assertThat(_login.isAccountInWorld("alice")).isFalse();
	}
	
	@Test
	void aWrongKeyIsRefusedAndNothingIsCounted()
	{
		SessionKey key = loggedIn("alice", "192.0.2.7");
		SessionKey wrong = new SessionKey(key.loginOk1(), key.loginOk2(), key.playOk1(), key.playOk2() + 1);
		
		_world.authenticate("alice", "client-1", wrong);
		
		assertThat(_events).containsExactly("claim client-1", "refuse client-1");
		assertThat(_world.isInWorld("alice")).isFalse();
		assertThat(_login.isAccountInWorld("alice")).isFalse();
	}
	
	@Test
	void aKickFromTheLoginClosesTheClientInTheWorld()
	{
		_world.authenticate("alice", "client-1", loggedIn("alice", "192.0.2.7"));
		_events.clear();
		
		_world.kick("alice");
		
		assertThat(_events).contains("close client-1");
	}
	
	private SessionKey loggedIn(String account, String address)
	{
		SessionKey key = new SessionKey(21, 22, 23, 24);
		L2Client client = mock(L2Client.class);
		when(client.getSessionKey()).thenReturn(key);
		when(client.getIp()).thenReturn(address);
		_login.assignSessionKeyToClient(account, client);
		return key;
	}
}
