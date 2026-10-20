package com.bayport.security;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Applies {@link LogScrubber} to every formatted log line.
 */
public class ScrubbingPatternLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        return LogScrubber.scrub(super.doLayout(event));
    }
}
