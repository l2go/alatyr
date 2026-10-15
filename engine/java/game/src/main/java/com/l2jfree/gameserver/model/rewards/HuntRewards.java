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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Calculates an immutable reward plan without touching characters, inventories or the world. */
public final class HuntRewards
{
	public static final int ADENA = 57;
	private static final BigDecimal TEN = BigDecimal.TEN;
	private static final BigDecimal SIX = BigDecimal.valueOf(6);
	private static final BigDecimal TWO = BigDecimal.valueOf(2);

	public record Member(long id, int level, double xpRate, double spRate)
	{
		public Member(long id, int level)
		{
			this(id, level, 1, 1);
		}
	}

	public record Side(long id, double damage, int level, boolean neutral, List<Member> members, int coinCursor)
	{
		public Side
		{
			members = List.copyOf(members);
			if (!Double.isFinite(damage) || damage <= 0)
				throw new IllegalArgumentException("Positive finite damage required");
		}
	}

	public record Snapshot(String worldId, long lifeId, int level, long xp, int sp, double xpExponent,
			double spExponent, List<Side> sides)
	{
		public Snapshot
		{
			sides = sides.stream().sorted(Comparator.comparingLong(Side::id)).toList();
			Set<Long> ids = new HashSet<>();
			Set<Long> players = new HashSet<>();
			for (Side side : sides)
			{
				if (!ids.add(side.id()))
					throw new IllegalArgumentException("Duplicate side");
				for (Member member : side.members())
					if (!players.add(member.id()))
						throw new IllegalArgumentException("Player belongs to two reward sides");
			}
		}
	}

	public record Drop(int itemId, long count)
	{
		public Drop
		{
			if (itemId <= 0 || count <= 0)
				throw new IllegalArgumentException("Positive item and count required");
		}
	}

	public record PlayerReward(long playerId, long xp, int sp, long coins)
	{
	}

	/** Each entry assigns one table result to exactly one side, including an unclaimed neutral side. */
	public record ItemReward(int dropIndex, long sideId, Drop drop, boolean unclaimed)
	{
	}

	public record SideReward(long sideId, double damage, BigDecimal weight, boolean admitted, boolean unclaimed, long coins,
			int nextCoinCursor, List<PlayerReward> players)
	{
		public SideReward
		{
			players = List.copyOf(players);
		}
	}

	public record Result(List<SideReward> sides, List<ItemReward> items)
	{
		public Result
		{
			sides = List.copyOf(sides);
			items = List.copyOf(items);
		}

		public long unclaimedCoins()
		{
			return sides.stream().filter(SideReward::unclaimed).mapToLong(SideReward::coins).sum();
		}
	}

	private HuntRewards()
	{
	}

	public static double levelRate(int diff, double exponent, double otherExponent)
	{
		diff = Math.max(-5, diff);
		if (exponent != 0)
			return StrictMath.pow(2, -diff / exponent);
		return otherExponent == 0 && diff > 5 ? StrictMath.pow(5.0 / 6, diff - 5) : 1;
	}

	public static double lootRate(int sideLevel, int mobLevel)
	{
		return sideLevel - mobLevel <= 5 ? 1 : StrictMath.pow(0.5, (sideLevel - mobLevel - 5) / 3.0);
	}

	/** Equal integer shares, with the remainder starting at the party's saved cursor. */
	public static long[] equalCoins(long coins, int members, int cursor)
	{
		if (coins < 0 || members <= 0)
			throw new IllegalArgumentException("Nonnegative coins and positive member count required");
		long[] shares = new long[members];
		for (int i = 0; i < members; i++)
			shares[i] = coins / members;
		for (int i = 0; i < coins % members; i++)
			shares[Math.floorMod(cursor + i, members)]++;
		return shares;
	}

	public static Result calculate(Snapshot snapshot, List<Drop> drops)
	{
		if (snapshot.sides().isEmpty())
			return new Result(List.of(), List.of());
		HuntRandom random = new HuntRandom(snapshot.worldId(), snapshot.lifeId());
		Map<Long, BigDecimal> weights = new LinkedHashMap<>();
		BigDecimal totalWeight = BigDecimal.ZERO;
		BigDecimal totalDamage = BigDecimal.ZERO;
		for (Side side : snapshot.sides())
		{
			BigDecimal damage = BigDecimal.valueOf(side.damage());
			BigDecimal weight = damage.multiply(BigDecimal.valueOf(side.neutral() ? 1 : lootRate(side.level(), snapshot.level())));
			weights.put(side.id(), weight);
			totalWeight = totalWeight.add(weight);
			totalDamage = totalDamage.add(damage);
		}
		List<Side> admitted = new ArrayList<>();
		Side dominant = null;
		for (Side side : snapshot.sides())
		{
			BigDecimal weight = weights.get(side.id());
			if (weight.multiply(TEN).compareTo(totalWeight) >= 0)
				admitted.add(side);
			if (weight.multiply(TEN).compareTo(totalWeight.multiply(SIX)) >= 0)
				dominant = side;
		}
		if (admitted.isEmpty())
		{
			BigDecimal maximum = weights.values().stream().max(BigDecimal::compareTo).orElseThrow();
			for (Side side : snapshot.sides())
				if (weights.get(side.id()).multiply(TWO).compareTo(maximum) >= 0)
					admitted.add(side);
		}
		long coins = 0;
		for (Drop drop : drops)
			if (drop.itemId() == ADENA)
				coins = Math.addExact(coins, drop.count());
		Map<Long, Long> sideCoins = divideCoins(coins, admitted, weights, random);
		List<ItemReward> items = new ArrayList<>();
		for (int i = 0; i < drops.size(); i++)
		{
			Drop drop = drops.get(i);
			if (drop.itemId() != ADENA)
			{
				Side winner = dominant != null ? dominant : drawSide(admitted, weights, random);
				items.add(new ItemReward(i, winner.id(), drop, winner.neutral() || winner.members().isEmpty()));
			}
		}
		List<SideReward> results = new ArrayList<>();
		for (Side side : snapshot.sides())
		{
			long sideCount = sideCoins.getOrDefault(side.id(), 0L);
			List<PlayerReward> players = new ArrayList<>();
			int cursor = side.coinCursor();
			if (!side.neutral() && !side.members().isEmpty())
			{
				int count = side.members().size();
				long[] split = equalCoins(sideCount, count, cursor);
				BigDecimal damage = BigDecimal.valueOf(side.damage());
				BigDecimal divisor = totalDamage.multiply(BigDecimal.valueOf(count));
				for (int i = 0; i < count; i++)
				{
					Member member = side.members().get(i);
					int diff = member.level() - snapshot.level();
					double xpRate = member.xpRate() * levelRate(diff, snapshot.xpExponent(), snapshot.spExponent());
					double spRate = member.spRate() * levelRate(diff, snapshot.spExponent(), snapshot.xpExponent());
					long xp = BigDecimal.valueOf(snapshot.xp()).multiply(damage).multiply(BigDecimal.valueOf(xpRate)).divideToIntegralValue(divisor).longValueExact();
					int sp = BigDecimal.valueOf(snapshot.sp()).multiply(damage).multiply(BigDecimal.valueOf(spRate)).divideToIntegralValue(divisor).intValueExact();
					players.add(new PlayerReward(member.id(), xp, sp, split[i]));
				}
				cursor = (int)((Math.floorMod(cursor, count) + sideCount % count) % count);
			}
			results.add(new SideReward(side.id(), side.damage(), weights.get(side.id()), admitted.contains(side),
					side.neutral() || side.members().isEmpty(), sideCount, cursor, players));
		}
		return new Result(results, items);
	}

	private static Map<Long, Long> divideCoins(long coins, List<Side> sides, Map<Long, BigDecimal> weights, HuntRandom random)
	{
		BigDecimal total = sides.stream().map(side -> weights.get(side.id())).reduce(BigDecimal.ZERO, BigDecimal::add);
		Map<Long, Long> result = new HashMap<>();
		Map<Long, BigDecimal> remainders = new HashMap<>();
		long left = coins;
		for (Side side : sides)
		{
			BigDecimal numerator = weights.get(side.id()).multiply(BigDecimal.valueOf(coins));
			long whole = numerator.divideToIntegralValue(total).longValueExact();
			result.put(side.id(), whole);
			remainders.put(side.id(), numerator.remainder(total));
			left -= whole;
		}
		List<Side> order = new ArrayList<>(sides);
		order.sort((a, b) -> remainders.get(b.id()).compareTo(remainders.get(a.id())));
		for (int start = 0; start < order.size() && start < left;)
		{
			int end = start + 1;
			while (end < order.size() && remainders.get(order.get(start).id()).compareTo(remainders.get(order.get(end).id())) == 0)
				end++;
			// Only ties crossing the remainder boundary need a random decision.
			if (end > left)
				for (int i = end - 1; i > start; i--)
					java.util.Collections.swap(order, i, start + random.nextInt(i - start + 1));
			start = end;
		}
		for (int i = 0; i < left; i++)
			result.merge(order.get(i).id(), 1L, Math::addExact);
		return result;
	}

	private static Side drawSide(List<Side> sides, Map<Long, BigDecimal> weights, HuntRandom random)
	{
		// Scale the decimal weights to integers; use all 64 random bits, including the sign bit.
		int scale = sides.stream().mapToInt(side -> weights.get(side.id()).scale()).max().orElseThrow();
		BigInteger total = BigInteger.ZERO;
		for (Side side : sides)
			total = total.add(weights.get(side.id()).movePointRight(scale).toBigIntegerExact());
		BigInteger draw = new BigInteger(Long.toUnsignedString(random.nextLong())).multiply(total).shiftRight(64);
		for (Side side : sides)
		{
			draw = draw.subtract(weights.get(side.id()).movePointRight(scale).toBigIntegerExact());
			if (draw.signum() < 0)
				return side;
		}
		throw new IllegalStateException("Weighted draw outside its range");
	}
}
