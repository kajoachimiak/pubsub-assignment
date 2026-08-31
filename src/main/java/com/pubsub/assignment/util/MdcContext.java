package com.pubsub.assignment.util;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Propagates a captured SLF4J MDC context (e.g. {@code traceId}) across asynchronous execution
 * boundaries such as {@link java.util.concurrent.CompletableFuture} continuations that may run on
 * worker threads (virtual-thread executors, Pub/Sub client callback threads, {@code ForkJoinPool})
 * that never inherit the caller's thread-local MDC.
 *
 * <p>Usage: capture the context on the originating thread via {@link #capture()}, then wrap any
 * callback that may later execute on a different thread with one of the {@code wrap} methods. The
 * wrapped callback restores the captured context for the duration of its execution and reverts to
 * whatever context the executing thread had beforehand once it completes, so the captured context
 * is never cleared prematurely and never leaks into unrelated work.
 */
public final class MdcContext {

    private MdcContext() {
    }

    public static Map<String, String> capture() {
        return MDC.getCopyOfContextMap();
    }

    public static <T> Supplier<T> wrap(Map<String, String> context, Supplier<T> supplier) {
        return () -> runWithContext(context, supplier::get);
    }

    public static <T> Consumer<T> wrap(Map<String, String> context, Consumer<T> consumer) {
        return t -> runWithContext(context, () -> {
            consumer.accept(t);
            return null;
        });
    }

    public static <T, R> Function<T, R> wrap(Map<String, String> context, Function<T, R> function) {
        return t -> runWithContext(context, () -> function.apply(t));
    }

    public static <T, U> BiConsumer<T, U> wrap(Map<String, String> context, BiConsumer<T, U> consumer) {
        return (t, u) -> runWithContext(context, () -> {
            consumer.accept(t, u);
            return null;
        });
    }

    private static <T> T runWithContext(Map<String, String> context, Supplier<T> action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        apply(context);
        try {
            return action.get();
        } finally {
            apply(previous);
        }
    }

    private static void apply(Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        } else {
            MDC.clear();
        }
    }
}
