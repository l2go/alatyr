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
package com.l2jfree.gameserver.bridge.wire;

/**
 * Bounds supplied with a profile. The framing rules themselves have no deployment defaults.
 */
final class Limits
{
	final int frameBytes;
	final int stringBytes;
	final int listItems;
	final int depth;
	final int records;
	final int fields;
	final int identifierBytes;
	final int worldBytes;
	final int clientBytes;
	
	private Limits(int frameBytes, int stringBytes, int listItems, int depth, int records, int fields,
			int identifierBytes, int worldBytes, int clientBytes)
	{
		this.frameBytes = frameBytes;
		this.stringBytes = stringBytes;
		this.listItems = listItems;
		this.depth = depth;
		this.records = records;
		this.fields = fields;
		this.identifierBytes = identifierBytes;
		this.worldBytes = worldBytes;
		this.clientBytes = clientBytes;
	}
	
	/**
	 * @return the frozen core and bridge/1 bounds
	 */
	static Limits profile()
	{
		return new Limits(65536, 4096, 1024, 3, 1024, 128, 64, 256, 128);
	}
}
