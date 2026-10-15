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
package com.l2jfree.gameserver.document;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.l2jfree.gameserver.model.items.templates.L2Equip;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.skills.conditions.Condition;
import com.l2jfree.gameserver.model.skills.conditions.ConditionParser;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.funcs.FuncTemplate;

/**
 * @author mkizub
 */
abstract class DocumentBase
{
	static final Logger _log = LoggerFactory.getLogger(DocumentBase.class);
	
	final File _file;
	
	DocumentBase(File pFile)
	{
		_file = pFile;
	}
	
	final void parse()
	{
		try
		{
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(true);
			factory.setIgnoringComments(true);
			parseDocument(factory.newDocumentBuilder().parse(_file));
		}
		catch (Exception e)
		{
			_log.error("Error in file: " + _file, e);
		}
	}
	
	final void parseDocument(Document doc)
	{
		final String defaultNodeName = getDefaultNodeName();
		
		for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("list".equalsIgnoreCase(n.getNodeName()))
			{
				for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
				{
					if (defaultNodeName.equalsIgnoreCase(d.getNodeName()))
					{
						parseDefaultNode(d);
					}
					else if (d.getNodeType() == Node.ELEMENT_NODE)
					{
						throw new IllegalStateException("Invalid tag <" + d.getNodeName() + ">");
					}
				}
			}
			else if (defaultNodeName.equalsIgnoreCase(n.getNodeName()))
			{
				parseDefaultNode(n);
			}
			else if (n.getNodeType() == Node.ELEMENT_NODE)
			{
				throw new IllegalStateException("Invalid tag <" + n.getNodeName() + ">");
			}
		}
	}
	
	abstract String getDefaultNodeName();
	
	abstract void parseDefaultNode(Node n);
	
	final void parseTemplate(Node n, Object template)
	{
		n = n.getFirstChild();
		
		for (; n != null; n = n.getNextSibling())
		{
			parseTemplateNode(n, template);
		}
	}
	
	void parseTemplateNode(Node n, Object template)
	{
		if ("add".equalsIgnoreCase(n.getNodeName()))
			attachFunc(n, template, "Add");
		
		else if ("sub".equalsIgnoreCase(n.getNodeName()))
			attachFunc(n, template, "Sub");
		
		else if ("mul".equalsIgnoreCase(n.getNodeName()))
			attachFunc(n, template, "Mul");
		
		else if ("basemul".equalsIgnoreCase(n.getNodeName()))
			attachFunc(n, template, "BaseMul");
		
		else if ("div".equalsIgnoreCase(n.getNodeName()))
			attachFunc(n, template, "Div");
		
		else if ("set".equalsIgnoreCase(n.getNodeName()))
			attachFunc(n, template, "Set");
		
		else if (n.getNodeType() == Node.ELEMENT_NODE)
			throw new IllegalStateException("Invalid tag <" + n.getNodeName() + "> in template");
	}
	
	final void attachFunc(Node n, Object template, String name)
	{
		final NamedNodeMap attrs = n.getAttributes();
		
		final Stats stat = Stats.valueOfXml(attrs.getNamedItem("stat").getNodeValue());
		final int ord = Integer.decode(attrs.getNamedItem("order").getNodeValue());
		final double lambda = getLambda(n, template);
		
		final Condition applayCond = parseConditionIfExists(n.getFirstChild(), template);
		
		final FuncTemplate ft = new FuncTemplate(applayCond, name, stat, ord, lambda);
		
		if (template instanceof L2Equip)
			((L2Equip)template).attach(ft);
		
		else if (template instanceof L2Skill)
			((L2Skill)template).attach(ft);
		
		else if (template instanceof EffectTemplate)
			((EffectTemplate)template).attach(ft);
		
		else
			throw new IllegalStateException("Invalid template for a Func");
	}
	
	final double getLambda(Node n, Object template)
	{
		return Double.parseDouble(getValue(n.getAttributes().getNamedItem("val").getNodeValue(), template));
	}
	
	final String getValue(String value, Object template)
	{
		if (value != null && value.length() >= 1 && value.charAt(0) == '#')
			return getTableValue(value, template);
		
		return value;
	}
	
	String getTableValue(String value, Object template)
	{
		throw new IllegalStateException();
	}
	
	private final ConditionParser _conditionParser = new ConditionParser() {
		@Override
		protected String getNodeValue(String nodeValue, Object template)
		{
			return getValue(nodeValue, template);
		}
	};
	
	final Condition parseConditionWithMessage(Node n, Object template)
	{
		return _conditionParser.parseConditionWithMessage(n, template);
	}
	
	final Condition parseConditionIfExists(Node n, Object template)
	{
		return _conditionParser.parseConditionIfExists(n, template);
	}
}
