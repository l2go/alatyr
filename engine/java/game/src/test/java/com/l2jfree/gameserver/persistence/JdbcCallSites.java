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
package com.l2jfree.gameserver.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;

/**
 * Finds JDBC call sites in Java sources: a {@code prepareStatement} with SQL known at compile time, the
 * {@code setXxx(index, ...)} calls on that statement and the {@code getXxx("column")} calls on its result set.
 * <p>
 * The analysis is local to one method, constructor or initializer and follows the plain style of the server code
 * ({@code statement = con.prepareStatement("..."); statement.setInt(1, x); rs = statement.executeQuery();
 * rs.getInt("id")}). SQL built at runtime is skipped; the statement gate covers what it can.
 */
final class JdbcCallSites
{
	/** One prepared statement and the typed accesses to it. */
	record Site(String location, String sql, Map<Integer, String> setters, Map<String, String> namedGetters,
			Map<Integer, String> indexedGetters)
	{
	}
	
	private JdbcCallSites()
	{
	}
	
	static List<Site> scan(Path... roots) throws IOException
	{
		JavaParser parser = new JavaParser(
				new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
		List<Site> sites = new ArrayList<>();
		for (Path root : roots)
		{
			List<Path> files;
			try (Stream<Path> walk = Files.walk(root))
			{
				files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
			}
			for (Path file : files)
			{
				ParseResult<CompilationUnit> result = parser.parse(file);
				Optional<CompilationUnit> unit = result.getResult();
				if (unit.isEmpty())
				{
					throw new IOException("Cannot parse " + file + ": " + result.getProblems());
				}
				scan(root.relativize(file).toString(), unit.get(), sites);
			}
		}
		return sites;
	}
	
	private static void scan(String file, CompilationUnit unit, List<Site> sites)
	{
		Map<String, String> constants = constants(unit);
		List<Node> bodies = new ArrayList<>();
		unit.findAll(CallableDeclaration.class).forEach(bodies::add);
		unit.findAll(InitializerDeclaration.class).forEach(bodies::add);
		for (Node body : bodies)
		{
			scanBody(file, body, constants, sites);
		}
	}
	
	private static void scanBody(String file, Node body, Map<String, String> constants, List<Site> sites)
	{
		List<MethodCallExpr> calls = new ArrayList<>(body.findAll(MethodCallExpr.class,
				call -> enclosingBody(call) == body));
		calls.sort(Comparator.comparing(call -> call.getBegin().orElseThrow()));
		Map<String, Site> statements = new HashMap<>();
		Map<String, Site> resultSets = new HashMap<>();
		for (MethodCallExpr call : calls)
		{
			String name = call.getNameAsString();
			Optional<String> scope = call.getScope().filter(Expression::isNameExpr).map(s -> s.asNameExpr().getNameAsString());
			if (name.equals("prepareStatement") && call.getArguments().size() >= 1)
			{
				String sql = resolve(call.getArgument(0), constants);
				Optional<String> target = assignedVariable(call);
				if (target.isEmpty())
				{
					continue;
				}
				if (sql == null)
				{
					statements.remove(target.get());
					continue;
				}
				Site site = new Site(file + ":" + call.getBegin().orElseThrow().line, sql, new LinkedHashMap<>(),
						new LinkedHashMap<>(), new LinkedHashMap<>());
				sites.add(site);
				statements.put(target.get(), site);
			}
			else if (scope.isPresent() && statements.containsKey(scope.get()))
			{
				Site site = statements.get(scope.get());
				if (name.startsWith("set") && call.getArguments().size() >= 2
						&& call.getArgument(0) instanceof IntegerLiteralExpr index)
				{
					site.setters().put(index.asNumber().intValue(), name);
				}
				else if (name.equals("executeQuery"))
				{
					assignedVariable(call).ifPresent(rs -> resultSets.put(rs, site));
				}
			}
			else if (scope.isPresent() && resultSets.containsKey(scope.get()) && name.startsWith("get")
					&& call.getArguments().size() == 1)
			{
				Site site = resultSets.get(scope.get());
				Expression argument = call.getArgument(0);
				if (argument instanceof StringLiteralExpr column)
				{
					site.namedGetters().put(column.getValue(), name);
				}
				else if (argument instanceof IntegerLiteralExpr index)
				{
					site.indexedGetters().put(index.asNumber().intValue(), name);
				}
			}
		}
	}
	
	private static Node enclosingBody(Node node)
	{
		Node current = node.getParentNode().orElse(null);
		while (current != null && !(current instanceof CallableDeclaration) && !(current instanceof InitializerDeclaration))
		{
			current = current.getParentNode().orElse(null);
		}
		return current;
	}
	
	/** The variable that receives the value of {@code call}, if the call is directly assigned. */
	private static Optional<String> assignedVariable(MethodCallExpr call)
	{
		Node parent = call.getParentNode().orElse(null);
		if (parent instanceof VariableDeclarator declarator)
		{
			return Optional.of(declarator.getNameAsString());
		}
		if (parent instanceof AssignExpr assign && assign.getTarget().isNameExpr())
		{
			return Optional.of(assign.getTarget().asNameExpr().getNameAsString());
		}
		return Optional.empty();
	}
	
	/** String constants declared in the file ({@code static final String X = "..."}). */
	private static Map<String, String> constants(CompilationUnit unit)
	{
		Map<String, String> constants = new HashMap<>();
		for (int pass = 0; pass < 3; pass++) // constants may refer to earlier constants
		{
			for (FieldDeclaration field : unit.findAll(FieldDeclaration.class))
			{
				if (!field.isFinal())
				{
					continue;
				}
				for (VariableDeclarator variable : field.getVariables())
				{
					variable.getInitializer().map(init -> resolve(init, constants))
							.ifPresent(value -> constants.put(variable.getNameAsString(), value));
				}
			}
		}
		return constants;
	}
	
	/** The compile-time string value of an expression, or {@code null} when it is built at runtime. */
	static String resolve(Expression expression, Map<String, String> constants)
	{
		if (expression instanceof StringLiteralExpr literal)
		{
			return literal.asString();
		}
		if (expression instanceof TextBlockLiteralExpr block)
		{
			return block.asString();
		}
		if (expression instanceof EnclosedExpr enclosed)
		{
			return resolve(enclosed.getInner(), constants);
		}
		if (expression instanceof NameExpr name)
		{
			return constants.get(name.getNameAsString());
		}
		if (expression instanceof FieldAccessExpr access)
		{
			return constants.get(access.getNameAsString());
		}
		if (expression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS)
		{
			String left = resolve(binary.getLeft(), constants);
			String right = resolve(binary.getRight(), constants);
			return left == null || right == null ? null : left + right;
		}
		return null;
	}
}
