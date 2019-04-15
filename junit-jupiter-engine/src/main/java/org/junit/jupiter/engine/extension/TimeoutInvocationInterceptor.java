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

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.util.Preconditions;

class TimeoutInvocationInterceptor implements BeforeAllCallback, BeforeEachCallback, InvocationInterceptor {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(Timeout.class);
	private static final String TIMEOUT_KEY = "timeout";

	@Override
	public void beforeAll(ExtensionContext context) {
		readAndStoreTimeout(context);
	}

	@Override
	public void beforeEach(ExtensionContext context) {
		readAndStoreTimeout(context);
	}

	@Override
	public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
			ExtensionContext extensionContext) throws Throwable {
		intercept(invocation, extensionContext);
	}

	private void readAndStoreTimeout(ExtensionContext context) {
		AnnotationSupport.findAnnotation(context.getElement(), Timeout.class) //
				.ifPresent(timeout -> context.getStore(NAMESPACE).put(TIMEOUT_KEY, timeout));
	}

	private <T> T intercept(Invocation<T> invocation, ExtensionContext extensionContext) throws Throwable {
		return decorate(invocation, extensionContext).proceed();
	}

	private <T> Invocation<T> decorate(Invocation<T> invocation, ExtensionContext extensionContext) {
		Timeout timeout = extensionContext.getStore(NAMESPACE).get(TIMEOUT_KEY, Timeout.class);
		if (timeout == null) {
			return invocation;
		}
		return new TimeoutInvocation<>(invocation, timeout, getExecutor(extensionContext));
	}

	private ScheduledExecutorService getExecutor(ExtensionContext extensionContext) {
		return extensionContext.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(ExecutorResource.class).get();
	}

	static class TimeoutInvocation<T> implements Invocation<T> {

		private final Invocation<T> delegate;
		private final Timeout timeout;
		private final ScheduledExecutorService executor;

		TimeoutInvocation(Invocation<T> delegate, Timeout timeout, ScheduledExecutorService executor) {
			Preconditions.condition(timeout.value() > 0, "timeout must be positive: " + timeout.value());
			this.delegate = delegate;
			this.timeout = timeout;
			this.executor = executor;
		}

		@Override
		public T proceed() throws Throwable {
			InterruptTask interruptTask = new InterruptTask(Thread.currentThread());
			ScheduledFuture<?> future = executor.schedule(interruptTask, timeout.value(), timeout.unit());
			Throwable failure = null;
			T result = null;
			try {
				result = delegate.proceed();
			}
			catch (Throwable t) {
				failure = t;
			}
			finally {
				future.cancel(true);
				if (interruptTask.executed) {
					failure = createTimeoutException(timeout, failure);
				}
			}
			if (failure != null) {
				throw failure;
			}
			return result;
		}

		private TimeoutException createTimeoutException(Timeout timeout, Throwable failure) {
			String timeUnit = timeout.unit().name().toLowerCase();
			String message = String.format("Test timed out after %d %s", timeout.value(), timeUnit);
			TimeoutException timeoutError = new TimeoutException(message);
			if (failure != null) {
				timeoutError.addSuppressed(failure);
			}
			return timeoutError;
		}

		static class InterruptTask implements Runnable {

			private final Thread thread;
			private volatile boolean executed;

			InterruptTask(Thread thread) {
				this.thread = thread;
			}

			@Override
			public void run() {
				executed = true;
				thread.interrupt();
			}

		}

	}

	static class ExecutorResource implements CloseableResource {

		private final ScheduledExecutorService executor;

		ExecutorResource() {
			AtomicInteger threadNumber = new AtomicInteger();
			executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "junit-jupiter-timeout-watcher-" + threadNumber.incrementAndGet());
				thread.setPriority(Thread.MAX_PRIORITY);
				return thread;
			});
		}

		public ScheduledExecutorService get() {
			return executor;
		}

		@Override
		public void close() throws Throwable {
			executor.shutdown();
			boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
			if (!terminated) {
				executor.shutdownNow();
				throw new JUnitException("Scheduled executor could not be stopped in an orderly manner");
			}
		}

	}

}
