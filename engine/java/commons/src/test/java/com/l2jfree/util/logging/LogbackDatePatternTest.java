/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.util.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;

class LogbackDatePatternTest
{
	private static final Pattern DATE_CONVERTER = Pattern.compile("%date\\{([^}]*)\\}");

	@Test
	void allDatePatternsInPackagedConfigurationCanBeInitialized()
			throws Exception
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);

		Document configuration;
		try (InputStream input = getClass().getResourceAsStream("/logback.xml"))
		{
			assertThat(input).as("packaged Logback configuration").isNotNull();
			configuration = factory.newDocumentBuilder().parse(input);
		}

		NodeList patterns = configuration.getElementsByTagName("pattern");
		List<String> datePatterns = new ArrayList<>();
		for (int i = 0; i < patterns.getLength(); i++)
		{
			Matcher matcher = DATE_CONVERTER.matcher(patterns.item(i).getTextContent());
			while (matcher.find())
				datePatterns.add(matcher.group());
		}

		assertThat(datePatterns).isNotEmpty();

		LoggerContext context = new LoggerContext();
		try
		{
			for (String datePattern : datePatterns)
			{
				PatternLayout layout = new PatternLayout();
				layout.setContext(context);
				layout.setPattern(datePattern);
				layout.start();
				assertThat(layout.isStarted()).as("date conversion %s", datePattern).isTrue();
				layout.stop();
			}
		}
		finally
		{
			context.stop();
		}
	}
}
