/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.support.hierarchical;

import static org.junit.gen5.commons.meta.API.Usage.Experimental;
import static org.junit.gen5.engine.TestExecutionResult.aborted;
import static org.junit.gen5.engine.TestExecutionResult.failed;
import static org.junit.gen5.engine.TestExecutionResult.successful;
import static org.junit.gen5.engine.support.hierarchical.BlacklistedExceptions.rethrowIfBlacklisted;

import org.junit.gen5.commons.meta.API;
import org.junit.gen5.engine.TestExecutionResult;
import org.opentest4j.TestAbortedException;

/**
 * {@code SingleTestExecutor} encapsulates the execution of a single test
 * wrapped in an {@link Executable}.
 *
 * @since 5.0
 * @see #executeSafely(Executable)
 */
@API(Experimental)
public class SingleTestExecutor {

	/**
	 * Functional interface for a single test to be executed by
	 * {@link SingleTestExecutor}.
	 */
	public interface Executable {

		/**
		 * Execute the test.
		 *
		 * @throws TestAbortedException to signal abortion
		 * @throws Throwable to signal failure
		 */
		void execute() throws TestAbortedException, Throwable;

	}

	/**
	 * Execute the supplied {@link Executable} and return a
	 * {@link TestExecutionResult} based on the outcome.
	 *
	 * <p>If the {@code Executable} throws a <em>blacklisted</em> exception
	 * &mdash; for example, an {@link OutOfMemoryError} &mdash; this method will
	 * rethrow it.
	 *
	 * @param executable the test to be executed
	 * @return {@linkplain TestExecutionResult#aborted aborted} if the
	 * {@code Executable} throws a {@link TestAbortedException};
	 * {@linkplain TestExecutionResult#failed failed} if any other
	 * {@link Throwable} is thrown; and {@linkplain TestExecutionResult#successful
	 * successful} otherwise
	 */
	public TestExecutionResult executeSafely(Executable executable) {
		try {
			executable.execute();
			return successful();
		}
		catch (TestAbortedException e) {
			return aborted(e);
		}
		catch (Throwable t) {
			rethrowIfBlacklisted(t);
			return failed(t);
		}
	}

}
