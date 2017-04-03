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

import org.asciidoctor.extension.IncludeProcessor;


/**
 * This {@link IncludeProcessor} evaluates all include macros in the document
 * whose targets start with <code>{api}</code> and transforms them to inline
 * macros, which are later parsed by the {@link ResolveApiInlinePreprocessor}.
 */
public class ResolveApiIncludeProcessor extends IncludeToInlineProcessor {
	private static final String INCLUDE_API = "api";

	public ResolveApiIncludeProcessor() {
		super(INCLUDE_API);
	}

}
