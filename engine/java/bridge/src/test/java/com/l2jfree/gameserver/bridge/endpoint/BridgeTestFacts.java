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

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;
import com.l2jfree.gameserver.network.bridge.BridgeMessageArg;
import com.l2jfree.gameserver.network.bridge.FactSink;

final class BridgeTestFacts implements FactSink
{
	final List<BridgeFact> facts = new ArrayList<>();
	final List<BridgeMessageArg> arguments = new ArrayList<>();
	int message;
	long time = 100;

	@Override public void emit(BridgeFact fact) { facts.add(fact); }
	@Override public long now() { return time; }
	@Override public void systemMessage(int id, List<BridgeMessageArg> args)
	{
		message = id;
		arguments.addAll(args);
	}

	static BridgeField field(BridgeFact fact, int number)
	{
		return fact.fields().stream().filter(field -> field.number() == number).findFirst().orElse(null);
	}
}
