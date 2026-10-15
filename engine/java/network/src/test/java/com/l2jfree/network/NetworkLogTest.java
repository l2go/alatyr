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
package com.l2jfree.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class NetworkLogTest
{
	@Test
	void flushWritesEqualRecordsOnceInTheOrderTheyWereFirstSeenAndEmptiesTheBuffer()
	{
		Logger logger = (Logger)LoggerFactory.getLogger(NetworkLogTest.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
		appender.setContext(logger.getLoggerContext());
		appender.start();
		logger.addAppender(appender);
		logger.setLevel(Level.ALL);
		try
		{
			NetworkLog log = new NetworkLog(NetworkLogTest.class, Integer.MAX_VALUE);
			for (String message : new String[] { "c", "a", "b", "a", "c", "a" })
				log.warn(message);
			
			log.flush();
			assertThat(messages(appender.list)).containsExactly("2 x c", "3 x a", "b");
			
			log.flush();
			assertThat(appender.list).hasSize(3);
		}
		finally
		{
			logger.detachAppender(appender);
		}
	}
	
	@Test
	void recordsWithADifferentLevelAreNotEqual()
	{
		Logger logger = (Logger)LoggerFactory.getLogger(NetworkLogTest.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
		appender.setContext(logger.getLoggerContext());
		appender.start();
		logger.addAppender(appender);
		logger.setLevel(Level.ALL);
		try
		{
			NetworkLog log = new NetworkLog(NetworkLogTest.class, Integer.MAX_VALUE);
			log.warn("same");
			log.error("same");
			log.warn("same");
			
			log.flush();
			assertThat(messages(appender.list)).containsExactly("2 x same", "same");
		}
		finally
		{
			logger.detachAppender(appender);
		}
	}
	
	private static List<String> messages(List<ILoggingEvent> events)
	{
		return events.stream().map(ILoggingEvent::getFormattedMessage).toList();
	}
}
