/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.SemanticFacts;
import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;
import com.l2jfree.gameserver.network.bridge.BridgeValue;

/**
 * Bounded, immutable-value projection of what this receiver has been told. The owning sink serializes access;
 * this class never reads the model. Capture preserves unknown domains and never replays historical effects.
 */
final class BridgeProjection
{
	static final int RETAINED_BYTES = 2097152;
	static final int ROWS = 1024;
	static final int OBJECTS = 4096;
	static final int WINDOWS = 8;
	static final int WINDOW_BYTES = 65536;
	static final int HISTORY_BYTES = 65536;
	private final Map<Integer, BridgeFact> actors = new TreeMap<>();
	private final Map<Integer, BridgeFact> motion = new TreeMap<>();
	private final Map<Integer, BridgeFact> vitals = new TreeMap<>();
	private final Map<Integer, BridgeValue.ObjectValue> inventory = new TreeMap<>();
	private final Map<WindowKey, Window> windows = new LinkedHashMap<>();
	private final ArrayDeque<History> history = new ArrayDeque<>();
	private int historyBytes;
	private int own;
	private Long coin;
	private boolean objectsComplete;
	private boolean inventoryComplete;
	private BridgeFact skills;
	private BridgeFact quests;

	private record WindowKey(int kind, long list) {}
	private record Window(BridgeFact fact, long seq, long born) {}
	private record History(BridgeValue entry, int bytes) {}
	record Snapshot(List<BridgeFact> facts, boolean ready) {
		Snapshot { facts = List.copyOf(facts); }
	}

	void observe(BridgeFact fact, long seq, long ms)
	{
		int id = Math.toIntExact(number(fact.fields(), 64, 0));
		switch (fact.kind())
		{
			case 1, 8 ->
			{
				if (flag(fact.fields(), 19))
					own = id;
				BridgeFact old = actors.get(id);
				actors.put(id, old == null ? fact : merge(old, fact));
			}
			case 2, 3, 4 ->
			{
				if (fact.kind() == 4 && flag(fact.fields(), 67))
				{
					BridgeFact self = actors.get(own);
					actors.clear();
					if (self != null)
						actors.put(own, self);
					motion.clear();
					vitals.keySet().removeIf(key -> key != own);
					windows.clear();
					objectsComplete = false;
				}
				BridgeFact old = actors.get(id);
				if (old != null)
				{
					var fields = new ArrayList<>(old.fields());
					fields.removeIf(field -> field.number() == 13);
					BridgeField at = field(fact.fields(), 13);
					if (at != null)
						fields.add(at);
					actors.put(id, new BridgeFact(old.kind(), fields));
				}
				if (fact.kind() == 2)
					motion.put(id, fact);
				else
					motion.remove(id);
			}
			case 6, 7 ->
			{
				BridgeFact old = actors.get(id);
				if (old != null)
					actors.put(id, merge(old, BridgeFact.of(old.kind(), BridgeField.flag(70, fact.kind() == 7))));
			}
			case 9 -> removeObject(Math.toIntExact(number(fact.fields(), 65, 0)));
			case 10 -> removeObject(id);
			case 11, 12, 13, 22 ->
			{
				if (fact.kind() == 11)
					windows.keySet().removeIf(key -> key.kind() == 11);
				long list = number(fact.fields(), 22, 0);
				var fields = new ArrayList<>(fact.fields());
				fields.removeIf(value -> value.number() == 74);
				fields.add(BridgeField.number(74, ms));
				BridgeFact proposal = new BridgeFact(fact.kind(), fields);
				if (encodedSize(proposal) > WINDOW_BYTES)
					throw new IllegalArgumentException("bridge window capacity");
				windows.put(new WindowKey(fact.kind(), list), new Window(proposal, seq, ms));
				if (fact.kind() == 11)
					rememberText(fact, ms);
			}
			case 14 ->
			{
				inventory.clear();
				for (BridgeValue row : list(fact.fields(), 24))
					putGood((BridgeValue.ObjectValue)row);
				inventoryComplete = flag(fact.fields(), 31);
				if (inventoryComplete)
					updateCoin();
				else if (field(fact.fields(), 23) != null)
					coin = number(fact.fields(), 23, 0);
			}
			case 15 ->
			{
				for (BridgeValue row : list(fact.fields(), 24))
				{
					var good = (BridgeValue.ObjectValue)row;
					int object = Math.toIntExact(number(good.fields(), 2, 0));
					if (number(good.fields(), 7, 0) == 3)
						inventory.remove(object);
					else
						putGood(good);
				}
				if (inventoryComplete)
					updateCoin();
				else if (field(fact.fields(), 23) != null)
					coin = number(fact.fields(), 23, 0);
			}
			case 16 ->
			{
				BridgeFact old = vitals.get(id);
				vitals.put(id, old == null ? fact : merge(old, fact));
			}
			case 18 -> rememberText(fact, ms);
			case 21 ->
			{
				if (list(fact.fields(), 29).size() > ROWS)
					throw new IllegalArgumentException("bridge quest capacity");
				quests = fact;
			}
			case 23 ->
			{
				if (list(fact.fields(), 30).size() > ROWS)
					throw new IllegalArgumentException("bridge skill capacity");
				skills = fact;
			}
			case 24 ->
			{
				BridgeFact old = actors.get(id);
				if (old != null)
					actors.put(id, merge(old, fact));
			}
			default -> { /* Historical damage, rewards, and quest events are not current state. */ }
		}
		windows.values().removeIf(window -> ms - window.born() >= 60000);
		if (actors.size() > OBJECTS || vitals.size() > OBJECTS || inventory.size() > ROWS || windows.size() > WINDOWS)
			throw new IllegalArgumentException("bridge retained capacity");
		long bytes = 0;
		for (BridgeFact retained : capture(ms).facts())
			bytes += encodedSize(retained);
		if (bytes > RETAINED_BYTES)
			throw new IllegalArgumentException("bridge retained byte capacity");
	}

	/** The caller has completed EnterWorld or the latest own relocation and its known-list walk. */
	void completeObjects()
	{
		objectsComplete = true;
	}

	Snapshot capture(long ms)
	{
		var copy = new ArrayList<BridgeFact>();
		copy.addAll(actors.values());
		copy.addAll(motion.values());
		copy.addAll(vitals.values());
		if (inventoryComplete || !inventory.isEmpty() || coin != null)
		{
			var fields = new ArrayList<BridgeField>();
			fields.add(BridgeField.list(24, List.copyOf(inventory.values())));
			fields.add(BridgeField.flag(31, inventoryComplete));
			if (coin != null)
				fields.add(BridgeField.number(23, coin));
			copy.add(new BridgeFact(14, fields));
		}
		if (skills != null)
			copy.add(skills);
		if (quests != null)
			copy.add(quests);
		for (Window window : windows.values())
			if (ms - window.born() < 60000)
				copy.add(window.fact());
		if (!history.isEmpty())
			copy.add(BridgeFact.of(19, BridgeField.list(28, history.stream().map(History::entry).toList())));
		boolean complete = objectsComplete && own != 0 && actors.containsKey(own) && vitals.containsKey(own)
				&& inventoryComplete && skills != null && flag(skills.fields(), 31)
				&& quests != null && flag(quests.fields(), 31);
		return new Snapshot(copy, complete);
	}

	private void putGood(BridgeValue.ObjectValue good)
	{
		var fields = new ArrayList<>(good.fields());
		fields.removeIf(value -> value.number() == 7);
		inventory.put(Math.toIntExact(number(fields, 2, 0)), new BridgeValue.ObjectValue(fields));
	}

	private void updateCoin()
	{
		long sum = 0;
		for (var good : inventory.values())
			if (number(good.fields(), 3, 0) == 57)
				sum = Math.addExact(sum, number(good.fields(), 4, 0));
		coin = sum;
	}

	private void removeObject(int id)
	{
		actors.remove(id);
		motion.remove(id);
		vitals.remove(id);
	}

	private void rememberText(BridgeFact fact, long ms)
	{
		BridgeField text = field(fact.fields(), 20);
		if (text == null)
			return;
		BridgeField category = field(fact.fields(), 26);
		String label = category == null ? fact.kind() == 11 ? "dialogue" : "world-message"
				: ((BridgeValue.StringValue)category.value()).value();
		BridgeValue entry = BridgeField.object(1, BridgeField.number(1, ms), BridgeField.string(2, label),
				new BridgeField(3, text.value())).value();
		int bytes = encodedSize(BridgeFact.of(19, BridgeField.list(28, List.of(entry))));
		if (bytes > HISTORY_BYTES)
			throw new IllegalArgumentException("bridge history entry capacity");
		history.add(new History(entry, bytes));
		historyBytes += bytes;
		while (history.size() > 256 || historyBytes > HISTORY_BYTES)
			historyBytes -= history.remove().bytes();
	}

	private static BridgeFact merge(BridgeFact old, BridgeFact update)
	{
		var fields = new TreeMap<Integer, BridgeField>();
		for (BridgeField value : old.fields())
			fields.put(value.number(), value);
		for (BridgeField value : update.fields())
		{
			if (value.number() == 25 && fields.get(25) != null)
			{
				var stats = new TreeMap<Integer, BridgeField>();
				for (BridgeField stat : ((BridgeValue.ObjectValue)fields.get(25).value()).fields())
					stats.put(stat.number(), stat);
				for (BridgeField stat : ((BridgeValue.ObjectValue)value.value()).fields())
					stats.put(stat.number(), stat);
				value = new BridgeField(25, new BridgeValue.ObjectValue(List.copyOf(stats.values())));
			}
			fields.put(value.number(), value);
		}
		return new BridgeFact(old.kind(), List.copyOf(fields.values()));
	}

	private static BridgeField field(List<BridgeField> fields, int number)
	{
		return fields.stream().filter(value -> value.number() == number).findFirst().orElse(null);
	}

	private static long number(List<BridgeField> fields, int number, long fallback)
	{
		BridgeField value = field(fields, number);
		return value == null ? fallback : ((BridgeValue.Number)value.value()).value();
	}

	private static boolean flag(List<BridgeField> fields, int number)
	{
		BridgeField value = field(fields, number);
		return value != null && ((BridgeValue.Flag)value.value()).value();
	}

	private static List<BridgeValue> list(List<BridgeField> fields, int number)
	{
		BridgeField value = field(fields, number);
		return value == null ? List.of() : ((BridgeValue.ListValue)value.value()).items();
	}

	private static int encodedSize(BridgeFact fact)
	{
		String domain = fact.kind() <= 10 || fact.kind() == 24 ? "objects" : switch (fact.kind())
		{
			case 11, 18 -> "dialogue";
			case 12, 13 -> "trade";
			case 14, 15 -> "inventory";
			case 16, 17 -> "self";
			case 19 -> "history";
			case 20, 21 -> "quests";
			default -> "skills";
		};
		return Codec.bridge().encode(List.of(SemanticFacts.envelope(fact, 1, 0, 1, domain))).length;
	}
}
