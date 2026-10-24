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
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.network.bridge.SemanticPacket;

class BridgeDescribeContractTest
{
	private static final Path GAME = Path.of("../game/src/main/java/com/l2jfree/gameserver");
	private static final Path ENDPOINT = Path.of("src/main/java/com/l2jfree/gameserver/bridge/endpoint");

	@Test
	void everyExactTranslatedClassImplementsTheSemanticSpi() throws Exception
	{
		String registry = Files.readString(ENDPOINT.resolve("BridgePacketRegistry.java"));
		String translated = registry.substring(registry.indexOf("TRANSLATED = Set.of("), registry.indexOf("NONESSENTIAL ="));
		var names = Pattern.compile("([A-Za-z0-9_.]+)\\.class").matcher(translated);
		int count = 0;
		while (names.find())
		{
			String name = names.group(1).replace('.', '$');
			Class<?> packet = Class.forName("com.l2jfree.gameserver.network.packets.server." + name, false,
					getClass().getClassLoader());
			assertThat(SemanticPacket.class.isAssignableFrom(packet)).as(name).isTrue();
			assertThat(BridgePacketRegistry.classify(packet)).isEqualTo(BridgePacketRegistry.Kind.TRANSLATED);
			count++;
		}
		assertThat(count).isEqualTo(45);
	}

	@Test
	void describersNeverSerializeReflectOrCompleteTeleport() throws Exception
	{
		int count = 0;
		try (var paths = Files.list(GAME.resolve("network/packets/server")))
		{
			for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList())
			{
				String source = Files.readString(path);
				int from = 0;
				while ((from = source.indexOf("public void describe(", from)) >= 0)
				{
					int start = source.indexOf('{', from);
					int end = start + 1;
					int depth = 1;
					while (depth != 0)
					{
						char c = source.charAt(end++);
						if (c == '{') depth++;
						if (c == '}') depth--;
					}
					assertThat(source.substring(start, end)).as(path.getFileName().toString())
							.doesNotContain("writeImpl", "writeD(", "writeS(", "getDeclared", "setAccessible",
									"Class.forName", "completeBridgeTeleport", "getBridgeTeleportGeneration");
					from = end;
					count++;
				}
			}
		}
		assertThat(count).isEqualTo(45);
		assertThat(Files.readString(ENDPOINT.resolve("BridgePacketRegistry.java")))
				.doesNotContain("getDeclared", "isAssignableFrom", "setAccessible");
	}

	@Test
	void checkpointCaptureChecksRelocationBeforeEnteringTheLeafSink() throws Exception
	{
		String source = Files.readString(GAME.resolve("gameobjects/L2Player.java"));
		int start = source.indexOf("public boolean captureBridgeScene(");
		int end = source.indexOf("private boolean acceptsBridgeCompletion", start);
		String body = source.substring(start, end);
		assertThat(body).contains("_teleportLock.lock()", "isTeleporting()", "_bridgeCompleting",
				"client.isBridgeDisconnectRequested()", "capture.run()", "_teleportLock.unlock()");
		assertThat(body.indexOf("isTeleporting()")).isLessThan(body.indexOf("capture.run()"));
		assertThat(body).doesNotContain(".get()", ".await(", "Thread.sleep", "store(", "deleteMe(");
	}

	@Test
	void ownTeleportGenerationIsCapturedUnderItsSourceLockBeforeCompletion() throws Exception
	{
		String source = Files.readString(GAME.resolve("gameobjects/L2Creature.java"));
		int method = source.indexOf("private void teleToLocationBridge");
		int captured = source.indexOf("TeleportToLocation.forBridgeRelocation", method);
		assertThat(captured).isGreaterThan(method);
		assertThat(source.substring(method, captured)).contains("_teleportLock.lock()", "generation");
		int completion = source.indexOf("publishOwnTeleport(generation)", captured);
		assertThat(completion).isGreaterThan(captured);
	}
}
