/*
 * Copyright 2015-2019 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.engine.extension;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.engine.AbstractJupiterTestEngineTests;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.Execution;

@DisplayName("@Timeout")
class TimeoutInvocationInterceptorTests extends AbstractJupiterTestEngineTests {

	@Test
	@DisplayName("is applied on annotated @Test methods")
	void appliesTimeoutOnAnnotatedTestMethods() {
		EngineExecutionResults results = executeTestsForClass(TimeoutAnnotatedMethodTestCase.class);

		Execution execution = findTestExecution(results, "testMethod()");
		assertThat(execution.getDuration()) //
				.isGreaterThanOrEqualTo(Duration.ofMillis(100)) //
				.isLessThan(Duration.ofSeconds(1));
		assertThat(execution.getTerminationInfo().getExecutionResult().getThrowable().orElseThrow()) //
				.isInstanceOf(TimeoutException.class) //
				.hasMessage("Test timed out after 100 milliseconds");
	}

	@Test
	@DisplayName("is applied on @Test methods in annotated classes")
	void appliesTimeoutOnTestMethodsInAnnotatedClasses() {
		EngineExecutionResults results = executeTestsForClass(TimeoutAnnotatedClassTestCase.class);

		Execution execution = findTestExecution(results, "testMethod()");
		assertThat(execution.getDuration()) //
				.isGreaterThanOrEqualTo(Duration.ofMillis(100)) //
				.isLessThan(Duration.ofSeconds(1));
		assertThat(execution.getTerminationInfo().getExecutionResult().getThrowable().orElseThrow()) //
				.isInstanceOf(TimeoutException.class) //
				.hasMessage("Test timed out after 100000000 nanoseconds");
	}

	@Test
	@DisplayName("fails uninterruptible methods")
	void failsUninterruptibleMethods() {
		EngineExecutionResults results = executeTestsForClass(UninterruptibleMethodTestCase.class);

		Execution execution = findTestExecution(results, "uninterruptibleMethod()");
		assertThat(execution.getDuration()) //
				.isGreaterThanOrEqualTo(Duration.ofMillis(100)) //
				.isLessThan(Duration.ofSeconds(1));
		assertThat(execution.getTerminationInfo().getExecutionResult().getThrowable().orElseThrow()) //
				.isInstanceOf(TimeoutException.class) //
				.hasMessage("Test timed out after 50 milliseconds");
	}

	private Execution findTestExecution(EngineExecutionResults results, String displayName) {
		return results //
				.tests() //
				.executions() //
				.filter(it -> displayName.equals(it.getTestDescriptor().getDisplayName())) //
				.findFirst() //
				.orElseThrow();
	}

	static class TimeoutAnnotatedMethodTestCase {
		@Test
		@Timeout(value = 100, unit = MILLISECONDS)
		void testMethod() throws Exception {
			Thread.sleep(1000);
		}
	}

	@Timeout(value = 100_000_000, unit = NANOSECONDS)
	static class TimeoutAnnotatedClassTestCase {
		@Nested
		class NestedClass {
			@Test
			void testMethod() throws Exception {
				Thread.sleep(1000);
			}
		}
	}

	static class UninterruptibleMethodTestCase {
		@Test
		@Timeout(value = 50, unit = MILLISECONDS)
		void uninterruptibleMethod() {
			long startTime = System.nanoTime();
			while (true) {
				assertThat(IntStream.range(1, 1_000_000).sum()).isGreaterThan(0);
				long elapsedTime = System.nanoTime() - startTime;
				if (elapsedTime > NANOSECONDS.convert(100, MILLISECONDS)) {
					return;
				}
			}
		}
	}

}
