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

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class StartupTimingsTest
{
	private static final long MILLISECOND = 1_000_000L;
	
	@Test
	void everyStepTakesTheTimeSinceTheStepBefore()
	{
		AtomicLong clock = new AtomicLong(5_000 * MILLISECOND);
		StartupTimings timings = new StartupTimings(400, clock::get);
		
		clock.addAndGet(1_200 * MILLISECOND);
		timings.done("login prepared");
		clock.addAndGet(11_800 * MILLISECOND);
		timings.done("world started");
		clock.addAndGet(100 * MILLISECOND);
		timings.done("login opened");
		
		assertThat(timings.summary()).isEqualTo(
				"13500 ms (before main 400, login prepared 1200, world started 11800, login opened 100)");
		assertThat(timings.totalMillis()).isEqualTo(13_500);
	}
	
	@Test
	void withoutStepsTheTotalIsTheTimeBeforeMain()
	{
		StartupTimings timings = new StartupTimings(412, () -> 0);
		
		assertThat(timings.summary()).isEqualTo("412 ms (before main 412)");
	}
}
