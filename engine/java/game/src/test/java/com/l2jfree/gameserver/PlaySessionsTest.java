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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.l2jfree.contract.AdmissionResult;
import com.l2jfree.contract.LoginPort;
import com.l2jfree.contract.SessionKey;

class PlaySessionsTest
{
	private static final SessionKey KEY = new SessionKey(1, 2, 3, 4);
	
	/** A login module that records what it hears and answers as told. */
	private static final class FakeLogin implements LoginPort
	{
		final List<String> admitted = new ArrayList<String>();
		final List<String> left = new ArrayList<String>();
		AdmissionResult answer = AdmissionResult.admitted("203.0.113.9");
		RuntimeException failure;
		Consumer<String> whileDeciding = account -> {
		};
		
		@Override
		public AdmissionResult admit(String account, SessionKey key)
		{
			admitted.add(account);
			whileDeciding.accept(account);
			if (failure != null)
				throw failure;
			return answer;
		}
		
		@Override
		public void leave(String account)
		{
			left.add(account);
		}
		
		@Override
		public void changeAccessLevel(String account, int level)
		{
		}
	}
	
	/** Game clients are plain strings; the fake records what happens to them. */
	private static final class FakeClients implements PlaySessions.Clients<String>
	{
		final List<String> events = new ArrayList<String>();
		
		@Override
		public void claim(String client, String account)
		{
			events.add("claim " + client + " " + account);
		}
		
		@Override
		public void admit(String client, SessionKey key, String hostAddress)
		{
			events.add("admit " + client + " " + hostAddress);
		}
		
		@Override
		public void refuse(String client)
		{
			events.add("refuse " + client);
		}
		
		@Override
		public void close(String client)
		{
			events.add("close " + client);
		}
	}
	
	private final FakeLogin _login = new FakeLogin();
	private final FakeClients _clients = new FakeClients();
	private final PlaySessions<String> _sessions = new PlaySessions<String>(_login, _clients);
	
	@Test
	void anAdmittedClientIsInTheWorldAndGetsTheHostTheLoginSaw()
	{
		assertThat(_sessions.authenticate("anna", "client-1", KEY)).isTrue();
		
		assertThat(_clients.events).containsExactly("claim client-1 anna", "admit client-1 203.0.113.9");
		assertThat(_sessions.isInWorld("anna")).isTrue();
		assertThat(_sessions.isWaiting("anna")).isFalse();
		assertThat(_sessions.inWorldCount()).isEqualTo(1);
		assertThat(_login.left).isEmpty();
	}
	
	@Test
	void aRefusedClientIsRefusedAndNeverEntersTheWorld()
	{
		_login.answer = AdmissionResult.refused();
		
		assertThat(_sessions.authenticate("anna", "client-1", KEY)).isTrue();
		
		assertThat(_clients.events).containsExactly("claim client-1 anna", "refuse client-1");
		assertThat(_sessions.isInWorld("anna")).isFalse();
		assertThat(_sessions.isWaiting("anna")).isFalse();
		assertThat(_login.left).isEmpty();
	}
	
	@Test
	void aFailureOfTheLoginCountsAsARefusal()
	{
		_login.failure = new IllegalStateException("the login module is down");
		
		assertThat(_sessions.authenticate("anna", "client-1", KEY)).isTrue();
		
		assertThat(_clients.events).containsExactly("claim client-1 anna", "refuse client-1");
		assertThat(_sessions.isInWorld("anna")).isFalse();
	}
	
	@Test
	void anAccountThatPlaysCannotBeClaimedAgainAndTheLoginIsNotAsked()
	{
		_sessions.authenticate("anna", "client-1", KEY);
		
		assertThat(_sessions.authenticate("anna", "client-2", KEY)).isFalse();
		
		assertThat(_login.admitted).containsExactly("anna");
	}
	
	@Test
	void aClaimWhileAnotherIsWaitingIsRefusedWithoutAskingTheLogin()
	{
		_login.whileDeciding = account -> assertThat(_sessions.authenticate(account, "client-2", KEY)).isFalse();
		
		_sessions.authenticate("anna", "client-1", KEY);
		
		assertThat(_login.admitted).containsExactly("anna");
		assertThat(_sessions.isInWorld("anna")).isTrue();
	}
	
	@Test
	void theLoginHearsOfALeaveOnce()
	{
		_sessions.authenticate("anna", "client-1", KEY);
		
		_sessions.logout("anna", "client-1");
		_sessions.logout("anna", "client-1");
		
		assertThat(_sessions.isInWorld("anna")).isFalse();
		assertThat(_login.left).containsExactly("anna");
	}
	
	@Test
	void aClientThatLogsOutWhileItWaitsIsLoggedOutOnceByTheAdmittingThread()
	{
		_login.whileDeciding = account -> _sessions.logout(account, "client-1");
		
		_sessions.authenticate("anna", "client-1", KEY);
		
		// the login admitted it, so the login must hear that it left, exactly once, and the client is not let in
		assertThat(_login.admitted).containsExactly("anna");
		assertThat(_login.left).containsExactly("anna");
		assertThat(_clients.events).containsExactly("claim client-1 anna");
		assertThat(_sessions.isInWorld("anna")).isFalse();
		assertThat(_sessions.isWaiting("anna")).isFalse();
	}
	
	@Test
	void aClientThatLogsOutWhileItWaitsAndIsRefusedLeavesNothingBehind()
	{
		_login.answer = AdmissionResult.refused();
		_login.whileDeciding = account -> _sessions.logout(account, "client-1");
		
		_sessions.authenticate("anna", "client-1", KEY);
		
		assertThat(_login.left).isEmpty();
		assertThat(_clients.events).containsExactly("claim client-1 anna");
		assertThat(_sessions.isWaiting("anna")).isFalse();
	}
	
	@Test
	void anAccountWithoutANameIsIgnoredByLogout()
	{
		_sessions.logout(null, "client-1");
		_sessions.logout("", "client-1");
		
		assertThat(_login.left).isEmpty();
	}
	
	@Test
	void kickClosesTheClientInTheWorldAndTheOneThatWaits()
	{
		_sessions.authenticate("anna", "client-1", KEY);
		_clients.events.clear();
		
		_sessions.kick("anna");
		
		assertThat(_clients.events).containsExactly("close client-1");
	}
}
