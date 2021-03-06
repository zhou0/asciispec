/**
 * Copyright (c) 2016 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package eu.numberfour.asciispec.processors;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.asciidoctor.ast.Document;

import eu.numberfour.asciispec.AttributeParser;
import eu.numberfour.asciispec.ParseException;

/**
 * This processor evaluates all inline {api} macros, which were previously
 * created by the {@link ResolveApiInlinePreprocessor}. The PQN of the include
 * is parsed and the corresponding API is extracted from the generated API
 * documentation. The attributes <code>lines</code> and <code>leveloffset</code>
 * are supported.
 */
public class ResolveApiInlinePreprocessor extends MacroPreprocessor<String> implements SourceIndexMixin {
	private static final String API_INCLUDE = "apiInclude";
	private static final String GEN_ADOC_DIR_VAR = "gen_adoc_dir";

	/** source link pattern. */
	public static final Pattern API_INCLUDE_PATTERN = Pattern
			.compile("include:\\s*\\{\\s*api\\s*\\}\\s*\\+*(?<PQN>.*?)\\+*\\s*\\[(?<ATTRS>.*)\\]");
	private static final Pattern GEN_ADOC_VAR_PATTERN = Pattern.compile(":" + GEN_ADOC_DIR_VAR + ":\\s*(?<GENADOC>.*)");

	private final SourceIndexMixinState state = new SourceIndexMixinState();

	@Override
	public SourceIndexMixinState getState() {
		return state;
	}

	@Override
	public void init(Document document) {
		super.registerPattern(GEN_ADOC_DIR_VAR, GEN_ADOC_VAR_PATTERN);
		super.registerPattern(API_INCLUDE, API_INCLUDE_PATTERN);
	}

	@Override
	protected String processMatch(Document document, String key, Matcher matcher) {
		String fullMatch = matcher.group();
		String newline = fullMatch;

		switch (key) {
		case GEN_ADOC_DIR_VAR:
			newline = setGenDir(document, matcher, newline);
			break;
		case API_INCLUDE:
			newline = processApiIncl(document, matcher, newline);
			break;
		}
		return newline;
	}

	private String setGenDir(Document document, Matcher matcher, String newline) {
		try {
			String genadocDirname = matcher.group("GENADOC");
			Path genadocPath = Paths.get(genadocDirname);
			File genadocFile = getAbsoluteFileFromBaseDirectory(genadocPath);
			setIndexFile(genadocFile);
		} catch (Exception e) {
			String message = e.getMessage() + ". Check variable '" + GEN_ADOC_DIR_VAR + "'";
			newline += "\n\n" + error(document, message) + "\n\n";
		}
		return newline;
	}

	private String processApiIncl(Document document, Matcher matcher, String newline) {
		try {
			checkConfig();
			ensureDatabase();
			newline = processApiInclude(document, matcher);
		} catch (Exception e) {
			newline += "\n\n" + error(document, e.getMessage());
		}
		return newline;
	}

	private void checkConfig() {
		if (getIndexFile() == null)
			throw new IllegalArgumentException("Missing config variable '" + GEN_ADOC_DIR_VAR + "'.");
	}

	private String processApiInclude(Document document, Matcher matcher) {
		String apiInclude = matcher.group();
		String pqn = matcher.group("PQN");
		String attrs = matcher.group("ATTRS");

		IndexEntryInfoResult ieir = getIndexEntryInfo(document, apiInclude, pqn);
		String errMsg = ieir.errorMsg;
		StringBuilder strb = new StringBuilder();
		if (ieir.iei != null) {
			String url = ieir.iei.adocPath;
			Path modulePath = getGendirModules().resolve(url);
			int startLine = ieir.iei.offsetStart;
			int endLine = ieir.iei.offsetEnd;

			try {

				Map<String, Object> attributes = AttributeParser.parse(attrs);
				int leveloffset = getLeveloffset(attributes);
				appendLeveloffset(strb, leveloffset, false);

				List<String> lines = Files.readAllLines(modulePath);
				for (int i = startLine; i < endLine && i < lines.size(); i++) {
					int relLineNumber = i - startLine + 1;
					if (isInSelectedLineRange(attributes, relLineNumber)) {
						strb.append(lines.get(i)).append("\n");
					}
				}

				appendLeveloffset(strb, leveloffset, true);
			} catch (ParseException e) {
				errMsg = error(document, "Could not parse attributes: " + attrs);
			} catch (IOException e) {
				File moduleFile = getBaseRelative(modulePath.toFile());
				errMsg = error(document, "Could not read module file: " + moduleFile.toString());
			}
		}

		String result;
		if (errMsg != null) {
			result = apiInclude + "\n" + errMsg;
		} else {
			result = strb.toString();
		}

		return result;
	}

	private int getLeveloffset(Map<String, Object> attributes) {
		int leveloffset = 0;
		try {
			if (attributes.containsKey("leveloffset")) {
				leveloffset = Integer.parseInt(String.valueOf(attributes.get("leveloffset")));
			}
		} catch (Throwable t) {
		}
		return leveloffset;
	}

	private void appendLeveloffset(StringBuilder strb, int leveloffset, boolean undo) {
		if (leveloffset != 0) {
			if (undo) {
				leveloffset = -leveloffset;
			}
			strb.append(":leveloffset: ");
			if (leveloffset > 0) {
				strb.append("+");
			}
			strb.append(String.valueOf(leveloffset)).append("\n");
		}
	}

	private boolean isInSelectedLineRange(Map<String, Object> attributes, int relLine) {
		if (!attributes.containsKey("lines") || attributes.get("lines") == null)
			return true;

		String selectionStr = String.valueOf(attributes.get("lines"));
		String[] selectionsStr = selectionStr.split("[,;]");
		for (String sel : selectionsStr) {
			if (sel.contains("..")) {
				// it should be a range, e.g.: 1..7
				String[] range = sel.split("\\.\\.");
				int start = Integer.valueOf(range[0]);
				int end = Integer.valueOf(range[1]);
				if (end == -1)
					end = Integer.MAX_VALUE;

				if (relLine >= start && relLine <= end)
					return true;
			} else {
				// it should be a simple number
				int number = Integer.valueOf(sel);
				if (relLine == number)
					return true;
			}
		}

		return false;
	}

}
