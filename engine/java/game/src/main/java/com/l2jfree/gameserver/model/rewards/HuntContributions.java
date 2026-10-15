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
package com.l2jfree.gameserver.model.rewards;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** HP credit belongs to an NPC life, independently of its current hate list. */
public final class HuntContributions
{
	private static final AtomicLong NEXT_LIFE = new AtomicLong();
	private final Map<Long, Long> _sides = new HashMap<>();
	private final Map<Long, Double> _damage = new LinkedHashMap<>();
	private long _lifeId = NEXT_LIFE.incrementAndGet();

	public synchronized long lifeId()
	{
		return _lifeId;
	}

	public synchronized Long sideOf(long playerId)
	{
		return _sides.get(playerId);
	}

	public synchronized double add(long playerId, long sideId, double damage, double currentHp)
	{
		if (!Double.isFinite(damage) || !Double.isFinite(currentHp) || damage <= 0 || currentHp <= 0)
			return 0;
		double credited = Math.min(damage, currentHp);
		long fixedSide = _sides.computeIfAbsent(playerId, ignored -> sideId);
		_damage.merge(fixedSide, credited, Double::sum);
		return credited;
	}

	public synchronized Map<Long, Double> damage()
	{
		return Map.copyOf(_damage);
	}

	public synchronized void reset()
	{
		_sides.clear();
		_damage.clear();
	}

	public synchronized void spawn()
	{
		reset();
		_lifeId = NEXT_LIFE.incrementAndGet();
	}
}
