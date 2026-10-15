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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.l2jfree.contract.AdmissionResult;
import com.l2jfree.contract.SessionKey;
import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.network.L2Client;

/** The admission of the world into the login module: the key, the session, and the account in the world. */
class LoginManagerAdmissionTest
{
	private static final SessionKey KEY = new SessionKey(11, 12, 13, 14);
	
	private boolean _showLicence;
	private LoginManager _manager;
	
	@BeforeEach
	void createTheManager()
	{
		_showLicence = LoginConfig.SHOW_LICENCE;
		_manager = new LoginManager(null);
	}
	
	@AfterEach
	void restoreTheConfiguration()
	{
		LoginConfig.SHOW_LICENCE = _showLicence;
	}
	
	private L2Client loggedIn(String account, SessionKey key, String ip)
	{
		L2Client client = mock(L2Client.class);
		when(client.getSessionKey()).thenReturn(key);
		when(client.getIp()).thenReturn(ip);
		_manager.assignSessionKeyToClient(account, client);
		return client;
	}
	
	@Test
	void theKeyThatWasIssuedAdmitsTheAccountOnceAndDropsTheLoginSession()
	{
		loggedIn("anna", KEY, "198.51.100.5");
		
		AdmissionResult first = _manager.beginPlaySession("anna", KEY);
		
		assertThat(first.admitted()).isTrue();
		assertThat(first.clientHost()).isEqualTo("198.51.100.5");
		assertThat(_manager.isAccountInWorld("anna")).isTrue();
		assertThat(_manager.isAccountInLoginServer("anna")).isFalse();
		assertThat(_manager.beginPlaySession("anna", KEY).admitted()).isFalse();
	}
	
	@Test
	void aWrongKeyIsRefusedAndChangesNothing()
	{
		loggedIn("anna", KEY, "198.51.100.5");
		
		AdmissionResult result = _manager.beginPlaySession("anna", new SessionKey(11, 12, 13, 99));
		
		assertThat(result.admitted()).isFalse();
		assertThat(_manager.isAccountInWorld("anna")).isFalse();
		assertThat(_manager.isAccountInLoginServer("anna")).isTrue();
	}
	
	@Test
	void anAccountWithoutALoginSessionIsRefused()
	{
		assertThat(_manager.beginPlaySession("nobody", KEY).admitted()).isFalse();
		assertThat(_manager.isAccountInWorld("nobody")).isFalse();
	}
	
	@Test
	void anOldKeyDoesNotAdmitANewerSessionOfTheSameAccount()
	{
		SessionKey oldKey = new SessionKey(1, 1, 1, 1);
		SessionKey newKey = new SessionKey(2, 2, 2, 2);
		loggedIn("anna", oldKey, "198.51.100.5");
		loggedIn("anna", newKey, "198.51.100.6"); // the account logged in again
		
		assertThat(_manager.beginPlaySession("anna", oldKey).admitted()).isFalse();
		assertThat(_manager.isAccountInLoginServer("anna")).isTrue();
		
		AdmissionResult result = _manager.beginPlaySession("anna", newKey);
		assertThat(result.admitted()).isTrue();
		assertThat(result.clientHost()).isEqualTo("198.51.100.6");
	}
	
	@Test
	void leavingFreesTheAccountForANewLogin()
	{
		loggedIn("anna", KEY, "198.51.100.5");
		_manager.beginPlaySession("anna", KEY);
		
		_manager.endPlaySession("anna");
		
		assertThat(_manager.isAccountInWorld("anna")).isFalse();
		loggedIn("anna", KEY, "198.51.100.5");
		assertThat(_manager.beginPlaySession("anna", KEY).admitted()).isTrue();
	}
	
	@Test
	void theLoginPairOfTheKeyCountsOnlyWhenTheLicenceWasShown()
	{
		SessionKey otherLoginPair = new SessionKey(99, 99, 13, 14);
		
		LoginConfig.SHOW_LICENCE = true;
		loggedIn("with_licence", KEY, "198.51.100.5");
		assertThat(_manager.beginPlaySession("with_licence", otherLoginPair).admitted()).isFalse();
		
		LoginConfig.SHOW_LICENCE = false;
		loggedIn("without_licence", KEY, "198.51.100.5");
		assertThat(_manager.beginPlaySession("without_licence", otherLoginPair).admitted()).isTrue();
	}
	
	@Test
	void exactlyOneOfManyConcurrentAdmissionsOfTheSameKeyWins() throws Exception
	{
		loggedIn("anna", KEY, "198.51.100.5");
		
		final int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<Future<Boolean>>();
		try
		{
			for (int i = 0; i < threads; i++)
				results.add(pool.submit(() -> {
					start.await();
					return _manager.beginPlaySession("anna", KEY).admitted();
				}));
			
			start.countDown();
			
			int admitted = 0;
			for (Future<Boolean> result : results)
				if (result.get())
					admitted++;
			
			assertThat(admitted).isEqualTo(1);
		}
		finally
		{
			pool.shutdownNow();
		}
	}
}
