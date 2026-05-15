/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.support;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test appender for capturing log events during assertions.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public final class TestLogAppender extends AbstractAppender implements AutoCloseable {
    private final Logger logger;

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    private TestLogAppender(String name, Logger logger, Layout<? extends Serializable> layout) {
        super(name, null, layout, false, Property.EMPTY_ARRAY);
        this.logger = logger;
    }

    /**
     * Executes the attach to operation.
     *
     * @param type the type parameter
     * @return the result
     */
    public static TestLogAppender attachTo(Class<?> type) {
        Logger logger = LoggerContext.getContext(false).getLogger(type.getName());
        TestLogAppender appender =
            new TestLogAppender("test-appender-" + type.getSimpleName() + "-" + System.nanoTime(), logger,
                PatternLayout.createDefaultLayout());
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    /**
     * Executes the append operation.
     *
     * @param event the event parameter
     */
    @Override
    public void append(LogEvent event) {
        events.add(event.toImmutable());
    }

    /**
     * Executes the formatted messages operation.
     *
     * @return the result
     */
    public List<String> formattedMessages() {
        return events.stream().map(event -> event.getMessage().getFormattedMessage()).toList();
    }

    /**
     * Executes the close operation.
     */
    @Override
    public void close() {
        logger.removeAppender((Appender) this);
        stop();
    }
}
