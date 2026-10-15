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
package com.l2jfree.gameserver.instancemanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.l2jfree.util.LookupTable;

class InstanceNamesParsingTest
{
	private static final String SYNTHETIC_NAMES = """
			<?xml version="1.0" encoding="UTF-8"?>
			<!-- a comment -->
			<instances_list xmlns="http://www.l2jfree.com">
			  <instance id="1" name="North Hall"/>
			  <instance name="South Hall &amp; Co" id="2"></instance>
			  <other id="9" name="ignored"/>
			  <instance id="3" name="East Yard é"/>
			  <instance id="1" name="North Hall again"/>
			</instances_list>
			""";

	@Test
	void readsIdAndNameOfEveryInstanceElement() throws Exception
	{
		LookupTable<String> names = parse(new ByteArrayInputStream(SYNTHETIC_NAMES.getBytes(StandardCharsets.UTF_8)));

		assertThat(names.size()).isEqualTo(3);
		assertThat(names.get(1)).isEqualTo("North Hall again");
		assertThat(names.get(2)).isEqualTo("South Hall & Co");
		assertThat(names.get(3)).isEqualTo("East Yard é");
		assertThat(names.get(9)).isNull();
	}

	@Test
	void readsASyntheticFileLikeADomParser(@TempDir Path dir) throws Exception
	{
		Path file = dir.resolve("instance-names.xml");
		Files.writeString(file, SYNTHETIC_NAMES);

		Map<Integer, String> expected = new LinkedHashMap<Integer, String>();
		NodeList elements = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile())
				.getElementsByTagName("instance");
		for (int i = 0; i < elements.getLength(); i++)
		{
			Element element = (Element)elements.item(i);
			expected.put(Integer.valueOf(element.getAttribute("id")), element.getAttribute("name"));
		}

		LookupTable<String> names;
		try (InputStream in = Files.newInputStream(file))
		{
			names = parse(in);
		}

		assertThat(expected).hasSize(3);
		assertThat(names.size()).isEqualTo(expected.size());
		for (Map.Entry<Integer, String> entry : expected.entrySet())
			assertThat(names.get(entry.getKey())).as("instance %d", entry.getKey()).isEqualTo(entry.getValue());
	}

	private static LookupTable<String> parse(InputStream in) throws Exception
	{
		LookupTable<String> names = new LookupTable<String>();
		InstanceManager.parseInstanceNames(in, names);
		return names;
	}
}
