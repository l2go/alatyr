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
package com.l2jfree.gameserver.handler;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Log lines written while an admin command runs also go to the GM who gave it. */
class AdminCommandLogTest
{
	private static final Logger LOG = LoggerFactory.getLogger(AdminCommandLogTest.class);
	
	@Test
	void theGmReadsTheLogOfTheirCommand()
	{
		@SuppressWarnings("unchecked")
		Consumer<String> gm = mock(Consumer.class);
		AdminCommandHandler.runAs(gm, () -> LOG.info("probe line of the command"));
		verify(gm).accept(endsWith("probe line of the command"));
	}
	
	@Test
	void theGmDoesNotReadLinesAfterTheCommand()
	{
		@SuppressWarnings("unchecked")
		Consumer<String> gm = mock(Consumer.class);
		AdminCommandHandler.runAs(gm, () -> {});
		LOG.info("probe line after the command");
		verify(gm, never()).accept(anyString());
	}
	
	@Test
	void theGmDoesNotReadLinesOfOtherThreads()
	{
		@SuppressWarnings("unchecked")
		Consumer<String> gm = mock(Consumer.class);
		AdminCommandHandler.runAs(gm, () -> CompletableFuture
				.runAsync(() -> LOG.info("probe line of another thread")).join());
		verify(gm, never()).accept(anyString());
	}
}
