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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;
import com.l2jfree.gameserver.network.bridge.BridgeMessageArg;
import com.l2jfree.gameserver.network.bridge.BridgeOption;
import com.l2jfree.gameserver.network.bridge.BridgeValue;

class BridgeSemanticValueTest
{
	@Test
	void semanticSnapshotsDoNotRetainMutableLists()
	{
		var rows = new ArrayList<BridgeValue>();
		rows.add(BridgeField.object(1, BridgeField.number(3, 57), BridgeField.number(4, 10)).value());
		var fields = new ArrayList<BridgeField>();
		fields.add(BridgeField.list(24, rows));
		var fact = new BridgeFact(14, fields);
		rows.clear();
		fields.clear();
		assertThat(fact.fields()).hasSize(1);
		var values = (BridgeValue.ListValue)fact.fields().get(0).value();
		assertThat(values.items()).hasSize(1);
		assertThatThrownBy(() -> values.items().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> fact.fields().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void theGameCannotAssignSessionHeadersRefsOrParticipantOptions()
	{
		for (int number : new int[] { 1, 2, 3, 4, 5, 6, 10, 16, 21, 32 })
			assertThatIllegalArgumentException().isThrownBy(() -> BridgeFact.of(1, BridgeField.number(number, 1)));
		assertThatIllegalArgumentException().isThrownBy(() -> BridgeFact.of(1,
				BridgeField.number(64, 1), BridgeField.number(64, 2)));
		assertThatIllegalArgumentException().isThrownBy(() -> new BridgeValue.ObjectValue(List.of(
				BridgeField.number(1, 1), BridgeField.number(1, 2))));
	}

	@Test
	void messageArgumentsKeepAllIntegerComponentsWithoutFloatingPoint()
	{
		var amount = BridgeMessageArg.number(6, Long.MAX_VALUE);
		assertThat(amount.number()).isEqualTo(Long.MAX_VALUE);
		assertThat(new BridgeMessageArg(7, null, -1, 2, 3).value())
				.isEqualTo(BridgeField.object(1, BridgeField.number(1, 7), BridgeField.number(3, -1),
						BridgeField.number(4, 2), BridgeField.number(5, 3)).value());
		assertThatIllegalArgumentException().isThrownBy(() -> new BridgeMessageArg(1, null, 1, 2, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new BridgeMessageArg(8, null, 1, 0, 0));
	}

	@Test
	void savedActionsAreNotExposedByOptionDiagnostics()
	{
		var option = new BridgeOption(1, "Label", "npc_70_Quest Example");
		assertThat(option.toString()).doesNotContain(option.savedAction(), option.label());
		assertThatIllegalArgumentException().isThrownBy(() -> new BridgeOption(1, "Label", "bad\0action"));
	}
}
