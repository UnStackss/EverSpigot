package dev.unstackss;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class EverCraftLogger {

    private static final Logger logger = Logger.getLogger("EverSpigot");

    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new CustomFormatter());
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
    }

    private static String colorize(Level level, String message) {
        String color = switch (level.getName()) {
            case "SEVERE" -> "\u001B[31m";
            case "WARNING" -> "\u001B[33m";
            case "INFO" -> "\u001B[1;34m";
            case "CONFIG", "FINE", "FINER", "FINEST" -> "\u001B[32m";
            default -> "\u001B[0m";
        };
        return String.format("%s[EverSpigot] %s\u001B[0m", color, message);
    }

    public static void log(Level level, String message) {
        logger.log(level, message);
    }

    private static class CustomFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[%1$tH:%1$tM:%1$tS %1$tp] ", record.getMillis()));
            sb.append(String.format("%-5s: ", record.getLevel().getName()));
            sb.append(colorize(record.getLevel(), record.getMessage()));
            sb.append("\n");
            return sb.toString();
        }
    }
}
