package com.inkera.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AppLogger {

    public enum Level {

        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3);

        private final int priority;

        Level(int priority) {
            this.priority = priority;
        }
    }

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern(
                    "HH:mm:ss.SSS"
            );

    private static boolean enabled = true;

    private static Level threshold =
            Level.INFO;

    private AppLogger() {
    }

    public static synchronized void configure(
            boolean isEnabled,
            String levelName
    ) {

        enabled = isEnabled;

        if (levelName == null ||
            levelName.isBlank()) {

            threshold = Level.INFO;
            return;
        }

        try {

            threshold =
                    Level.valueOf(
                            levelName
                                    .trim()
                                    .toUpperCase()
                    );

        } catch (IllegalArgumentException e) {

            threshold = Level.INFO;
        }
    }

    public static void debug(String message) {
        log(Level.DEBUG, message, null);
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    public static void warn(String message) {
        log(Level.WARN, message, null);
    }

    public static void error(String message) {
        log(Level.ERROR, message, null);
    }

    public static void error(
            String message,
            Throwable throwable
    ) {

        log(
                Level.ERROR,
                message,
                throwable
        );
    }

    private static synchronized void log(
            Level level,
            String message,
            Throwable throwable
    ) {

        if (!enabled) {
            return;
        }

        if (level.priority < threshold.priority) {
            return;
        }

        String timestamp =
                LocalDateTime.now()
                        .format(FORMATTER);

        String line =
                "["
                + timestamp
                + "] ["
                + level.name()
                + "] "
                + message;

        if (level == Level.WARN ||
            level == Level.ERROR) {

            System.err.println(line);

        } else {

            System.out.println(line);
        }

        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
}
