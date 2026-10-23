package com.l2jfree.gameserver.bridge.endpoint;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.mockito.MockedStatic;

import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;

/** Prevents player class initialization from opening a database in endpoint unit tests. */
final class BridgeTestPlayers
{
	private BridgeTestPlayers()
	{
	}

	static L2Player player()
	{
		try (MockedStatic<SkillTreeTable> tables = mockStatic(SkillTreeTable.class))
		{
			SkillTreeTable tree = mock(SkillTreeTable.class);
			when(tree.getExpertiseLevel(anyInt())).thenReturn(-1);
			when(tree.getExpertiseLevel(0)).thenReturn(0);
			tables.when(SkillTreeTable::getInstance).thenReturn(tree);
			return mock(L2Player.class);
		}
	}
}
