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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.network.bridge.BridgeHtml;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

class BridgeHtmlTest
{
	@Test
	void displayTokenizerRemovesExecutableContentAndPreservesEntitiesAndPre()
	{
		var window = BridgeHtml.parse("<HTML>  One\t two<BR1>three &amp; &lt; &bogus; &#65; &#x1f600;"
				+ "<script>if (a < b) { fetch('remote'); }</script><STYLE>x < y</STYLE>"
				+ "<img src='remote'><p> end </p><pre>  p\r\n q </pre></HTML>");
		assertThat(window.text()).isEqualTo("One two\nthree & < &bogus; A \ud83d\ude00 end\n  p\n q ");
		assertThat(window.options()).isEmpty();
		assertThat(BridgeHtml.parse("a&#0;b").text()).isEqualTo("a\0b");
		assertThat(BridgeHtml.parse("&#xD800; &#1114112;").text()).isEqualTo("&#xD800; &#1114112;");
	}

	@Test
	void actionableOptionsUseExactSavedStringsAndSourceOrder()
	{
		var window = BridgeHtml.parse("<A ACTION=\"bypass -h  npc_70_Quest Example &amp; x \" >"
				+ "First <b>label</b><button action=\"bypass -h second\" value=\"Second\"></a>"
				+ "<a action=\"bypass -h second\">Duplicate</a>");
		assertThat(window.options()).extracting(option -> option.option()).containsExactly(1, 2, 3);
		assertThat(window.options()).extracting(option -> option.savedAction())
				.containsExactly("npc_70_Quest Example &amp; x", "second", "second");
		assertThat(window.options().get(0).label()).isEqualTo("First label");
		assertThat(window.options().get(1).label()).isEqualTo("Second");
	}

	@Test
	void otherCommandsVariablesAndPrefixLookalikesKeepLabelsButGrantNoActions()
	{
		var window = BridgeHtml.parse("<a action=\"bypass -hfoo\">Lookalike</a> "
				+ "<button action=\"bypass -h cmd $edit\" value=\"Variable\"> "
				+ "<a action=\"admin_command\">Other</a>");
		assertThat(window.text()).isEqualTo("Lookalike Variable Other");
		assertThat(window.options()).isEmpty();
	}

	@Test
	void malformedActionsAndInvalidUtf8FailWithoutPartialOptions()
	{
		for (String html : new String[] { "<a action=\"bypass -h one\">ok</a><a action=\"broken>",
				"<a action=\"bypass -h one\">unclosed", "<button action=\"bypass -h one\" ACTION=\"bypass -h two\">",
				"<a action=\"bypass -h bad\0action\">bad</a>", "<a action=\"bypass -h bad&#0;action\">bad</a>",
				"\ud800", "x".repeat(4097), "\u00e9".repeat(2049) })
			assertThatIllegalArgumentException().isThrownBy(() -> BridgeHtml.parse(html));
		assertThat(BridgeHtml.parse("x".repeat(4096)).text()).hasSize(4096);
	}

	@Test
	void onlyTheCurrentNpcDialogueIsSpokenAndNoticesNeverExposeActions()
	{
		var player = BridgeTestPlayers.player();
		var npc = mock(L2Npc.class);
		when(npc.getObjectId()).thenReturn(70);
		when(player.getLastFolkNPC()).thenReturn(npc);
		when(player.getTarget()).thenReturn(npc);
		var sink = new BridgeTestFacts();
		String html = "<a action=\"bypass -h npc_70_Quest Example\">Label</a>";
		new NpcHtmlMessage(71, html).describe(sink, player);
		new NpcHtmlMessage(70, html).describe(sink, player);
		assertThat(sink.facts.get(0).kind()).isEqualTo(18);
		assertThat(BridgeTestFacts.field(sink.facts.get(0), 68)).isNull();
		assertThat(sink.facts.get(1).kind()).isEqualTo(11);
		assertThat(BridgeTestFacts.field(sink.facts.get(1), 68)).isNotNull();
		assertThat(BridgeTestFacts.field(sink.facts.get(1), 74)).isNotNull();
	}
}
