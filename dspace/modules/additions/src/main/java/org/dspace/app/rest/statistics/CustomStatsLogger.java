package org.dspace.app.rest.statistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class CustomStatsLogger {

    private static final Logger log = LogManager.getLogger(CustomStatsLogger.class);

    @PostConstruct
    public void init() {
        log.info("=== OVERLAY CUSTOM STATS LOGGER INITIALIZED ===");
    }
}
