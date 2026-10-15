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
package com.l2jfree.gameserver.handler.admincommands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.persistence.WorldTransaction;

public class AdminRepairChar implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_restore", "admin_repair" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		handleRepair(command, activeChar);
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private void handleRepair(String command, L2Player activeChar)
	{
		String[] parts = command.split(" ");
		if (parts.length != 2)
			return;
		
		final Integer objId = CharNameTable.getInstance().getByName(parts[1]);
		
		if (objId == null || objId == 0)
			return;
		
		// The three changes belong together
		boolean repaired = WorldTransaction.run("Repair of character " + parts[1], () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
			
				PreparedStatement statement =
						con.prepareStatement("UPDATE player SET x=17867, y=170259, z=-3450 WHERE id=?");
				statement.setInt(1, objId);
				statement.execute();
				statement.close();
			
				statement = con.prepareStatement("DELETE FROM player_shortcut WHERE player_id=?");
				statement.setInt(1, objId);
				statement.execute();
				statement.close();
			
				statement =
						con.prepareStatement("UPDATE item SET location='INVENTORY' WHERE owner_player_id=? AND location='PAPERDOLL'");
				statement.setInt(1, objId);
				statement.execute();
				statement.close();
			}
			catch (SQLException e)
			{
				throw new IllegalStateException("Could not repair character", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
			
		if (repaired)
			activeChar.sendMessage("Character " + parts[1] + " got repaired.");
	}
}
