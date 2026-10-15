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
package com.l2jfree.gameserver.model.world.spawn;

public class SpawnData
{
	public final int npcId;
	public final int x;
	public final int y;
	public final int z;
	public final int heading;
	public final int respawnDelay;
	public final int respawnMinDelay;
	public final int respawnMaxDelay;
	
	public SpawnData(int npcId, int x, int y, int z, int heading, int respawnDelay)
	{
		this.npcId = npcId;
		this.x = x;
		this.y = y;
		this.z = z;
		this.heading = heading;
		this.respawnDelay = respawnDelay;
		this.respawnMinDelay = respawnDelay;
		this.respawnMaxDelay = respawnDelay;
	}
	
	public SpawnData(int npcId, int x, int y, int z, int heading, int respawnDelay, int respawnMinDelay,
			int respawnMaxDelay)
	{
		this.npcId = npcId;
		this.x = x;
		this.y = y;
		this.z = z;
		this.heading = heading;
		this.respawnDelay = respawnDelay;
		this.respawnMinDelay = respawnMinDelay;
		this.respawnMaxDelay = respawnMaxDelay;
	}
}
