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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.l2jfree.loginserver.LoginConfig;

/** The packets of the login run on virtual threads, so failed logins of one address arrive from many threads at once. */
class FailedLoginCountingTest
{
	private static final int THREADS = 8;
	private static final int PER_THREAD = 2000;
	
	private int _limit;
	private LoginManager _manager;
	
	@BeforeEach
	void createTheManager()
	{
		_limit = LoginConfig.LOGIN_TRY_BEFORE_BAN;
		LoginConfig.LOGIN_TRY_BEFORE_BAN = Integer.MAX_VALUE;
		_manager = new LoginManager(null);
	}
	
	@AfterEach
	void restoreTheConfiguration()
	{
		LoginConfig.LOGIN_TRY_BEFORE_BAN = _limit;
	}
	
	@Test
	void everyFailedLoginOfOneAddressIsCounted() throws Exception
	{
		InetAddress address = InetAddress.getByName("198.51.100.9");
		AtomicInteger password = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(THREADS);
		try
		{
			CountDownLatch start = new CountDownLatch(1);
			List<Future<?>> futures = new ArrayList<>();
			for (int t = 0; t < THREADS; t++)
			{
				futures.add(executor.submit(() -> {
					start.await();
					for (int i = 0; i < PER_THREAD; i++)
						_manager.handleBadLogin("anna", "wrong-" + password.incrementAndGet(), address);
					return null;
				}));
			}
			start.countDown();
			for (Future<?> future : futures)
				future.get();
		}
		finally
		{
			executor.shutdownNow();
		}
		
		assertThat(_manager.failedLoginCount(address)).isEqualTo(THREADS * PER_THREAD);
	}
	
	@Test
	void aRepeatedPasswordIsNotCountedAgain() throws Exception
	{
		InetAddress address = InetAddress.getByName("198.51.100.10");
		
		_manager.handleBadLogin("anna", "same", address);
		_manager.handleBadLogin("anna", "same", address);
		_manager.handleBadLogin("anna", "other", address);
		
		assertThat(_manager.failedLoginCount(address)).isEqualTo(2);
	}
}
