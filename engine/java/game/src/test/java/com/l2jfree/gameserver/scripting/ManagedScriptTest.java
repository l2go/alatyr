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
package com.l2jfree.gameserver.scripting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;

import javax.script.ScriptException;

import org.junit.jupiter.api.Test;

/** A script object learns its file from the load that creates it. */
class ManagedScriptTest
{
	private static final File FILE = new File("data/scripts/quests/probe/__init__.py");
	
	@Test
	void aScriptCreatedWhileLoadingKnowsItsFile() throws Exception
	{
		ManagedScript script = ManagedScript.loading(FILE, Probe::new);
		assertThat(script.getScriptFile()).isEqualTo(FILE);
	}
	
	@Test
	void aScriptCreatedOutsideALoadHasNoFile()
	{
		assertThat(new Probe().getScriptFile()).isNull();
	}
	
	@Test
	void theFileIsUnboundAfterAFailedLoad()
	{
		assertThatThrownBy(() -> ManagedScript.loading(FILE, () -> {
			throw new ScriptException("expected by the test");
		})).isInstanceOf(ScriptException.class);
		assertThat(new Probe().getScriptFile()).isNull();
	}
	
	@Test
	void aNestedLoadSeesItsOwnFileAndRestoresTheOuterOne() throws Exception
	{
		File inner = new File("data/scripts/inner.py");
		ManagedScript[] scripts = ManagedScript.loading(FILE,
				() -> new ManagedScript[] { ManagedScript.loading(inner, Probe::new), new Probe() });
		assertThat(scripts[0].getScriptFile()).isEqualTo(inner);
		assertThat(scripts[1].getScriptFile()).isEqualTo(FILE);
	}
	
	private static final class Probe extends ManagedScript
	{
		@Override
		public boolean unload()
		{
			return true;
		}
		
		@Override
		public String getScriptName()
		{
			return "probe";
		}
		
		@Override
		public ScriptManager<?> getScriptManager()
		{
			return null;
		}
	}
}
