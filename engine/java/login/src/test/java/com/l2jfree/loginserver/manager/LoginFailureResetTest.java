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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.beans.FailedLoginAttempt;
import com.l2jfree.loginserver.security.PasswordHasher;
import com.l2jfree.loginserver.services.AccountsServices;
import com.l2jfree.loginserver.services.exception.AccountWrongPasswordException;
import com.l2jfree.tools.codec.Base64;

class LoginFailureResetTest
{
	@Test
	@DisplayName("a valid password resets only its address's failed-login count")
	void successfulLoginResetsFailedAttemptsForItsAddress() throws Exception
	{
		LoginManager manager = mock(LoginManager.class, CALLS_REAL_METHODS);
		AccountsServices service = mock(AccountsServices.class);
		byte[] hash = MessageDigest.getInstance("SHA").digest("correct".getBytes(StandardCharsets.UTF_8));
		Accounts account = new Accounts("alice", Base64.encodeBytes(hash), BigDecimal.ZERO, 0, 0,
				1900, 1, 1, "192.0.2.1");
		when(service.getAccountById("alice")).thenReturn(account);
		setField(manager, "_service", service);

		Map<InetAddress, FailedLoginAttempt> attempts = new HashMap<InetAddress, FailedLoginAttempt>();
		setField(manager, "_hackProtection", attempts);
		InetAddress address = InetAddress.getByName("192.0.2.1");
		InetAddress otherAddress = InetAddress.getByName("198.51.100.2");
		attempts.put(otherAddress, new FailedLoginAttempt(otherAddress, "other-wrong"));

		int oldLimit = LoginConfig.LOGIN_TRY_BEFORE_BAN;
		LoginConfig.LOGIN_TRY_BEFORE_BAN = 3;
		try
		{
			assertThatThrownBy(() -> manager.loginValid("alice", "wrong-one", address))
					.isInstanceOf(AccountWrongPasswordException.class);
			assertThatThrownBy(() -> manager.loginValid("alice", "wrong-two", address))
					.isInstanceOf(AccountWrongPasswordException.class);
			assertThat(attempts.get(address).getCount()).isEqualTo(2);

			assertThat(manager.loginValid("alice", "correct", address)).isTrue();
			assertThat(attempts).doesNotContainKey(address);
			assertThat(attempts.get(otherAddress).getCount()).isEqualTo(1);

			assertThatThrownBy(() -> manager.loginValid("alice", "wrong-three", address))
					.isInstanceOf(AccountWrongPasswordException.class);
			assertThat(attempts.get(address).getCount()).isEqualTo(1);
		}
		finally
		{
			LoginConfig.LOGIN_TRY_BEFORE_BAN = oldLimit;
		}
	}

	/** The row read for the password check may be stale: last-active and address move, the password is a compare-and-swap. */
	@Test
	@DisplayName("a login writes the time and the address, and upgrades an old password only from the hash it read")
	void loginWritesOnlyWhatItChanges() throws Exception
	{
		LoginManager manager = mock(LoginManager.class, CALLS_REAL_METHODS);
		AccountsServices service = mock(AccountsServices.class);
		byte[] hash = MessageDigest.getInstance("SHA").digest("correct".getBytes(StandardCharsets.UTF_8));
		String legacy = Base64.encodeBytes(hash);
		Accounts account = new Accounts("alice", legacy, BigDecimal.ZERO, 0, 0,
				1900, 1, 1, "192.0.2.1");
		when(service.getAccountById("alice")).thenReturn(account);
		when(service.replacePasswordIfUnchanged(any(), any(), any())).thenReturn(true);
		setField(manager, "_service", service);
		setField(manager, "_hackProtection", new HashMap<InetAddress, FailedLoginAttempt>());
		
		assertThat(manager.loginValid("alice", "correct", InetAddress.getByName("192.0.2.7"))).isTrue();
		
		ArgumentCaptor<Accounts> written = ArgumentCaptor.forClass(Accounts.class);
		verify(service).updateGivenColumns(written.capture());
		assertThat(written.getValue().getLogin()).isEqualTo("alice");
		assertThat(written.getValue().getLastIp()).isEqualTo("192.0.2.7");
		assertThat(written.getValue().getLastactive()).isNotNull();
		assertThat(written.getValue().getAccessLevel()).isNull();
		assertThat(written.getValue().getPassword()).isNull();
		verify(service).replacePasswordIfUnchanged(eq("alice"), eq(legacy), argThat(upgraded ->
				upgraded.startsWith("pbkdf2-sha256$") && PasswordHasher.verify("correct", upgraded)));
	}

	@Test
	@DisplayName("a password that is already in the current form is not written again")
	void currentPasswordIsNotReplaced() throws Exception
	{
		LoginManager manager = mock(LoginManager.class, CALLS_REAL_METHODS);
		AccountsServices service = mock(AccountsServices.class);
		Accounts account = new Accounts("alice", PasswordHasher.hash("correct"), BigDecimal.ZERO, 0, 0,
				1900, 1, 1, "192.0.2.1");
		when(service.getAccountById("alice")).thenReturn(account);
		setField(manager, "_service", service);
		setField(manager, "_hackProtection", new HashMap<InetAddress, FailedLoginAttempt>());

		assertThat(manager.loginValid("alice", "correct", InetAddress.getByName("192.0.2.7"))).isTrue();

		verify(service, never()).replacePasswordIfUnchanged(any(), any(), any());
	}

	@Test
	@DisplayName("a password stored after the hash was read is left as it is, and the login still succeeds")
	void aNewerPasswordIsKept() throws Exception
	{
		LoginManager manager = mock(LoginManager.class, CALLS_REAL_METHODS);
		AccountsServices service = mock(AccountsServices.class);
		byte[] hash = MessageDigest.getInstance("SHA").digest("correct".getBytes(StandardCharsets.UTF_8));
		Accounts account = new Accounts("alice", Base64.encodeBytes(hash), BigDecimal.ZERO, 0, 0,
				1900, 1, 1, "192.0.2.1");
		when(service.getAccountById("alice")).thenReturn(account);
		when(service.replacePasswordIfUnchanged(any(), any(), any())).thenReturn(false);
		setField(manager, "_service", service);
		setField(manager, "_hackProtection", new HashMap<InetAddress, FailedLoginAttempt>());

		assertThat(manager.loginValid("alice", "correct", InetAddress.getByName("192.0.2.7"))).isTrue();
		verify(service).updateGivenColumns(any());
	}
	
	private static void setField(LoginManager manager, String name, Object value) throws Exception
	{
		Field field = LoginManager.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(manager, value);
	}
}
