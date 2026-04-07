package br.com.brforgers.armelin.dps;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class LoggerConfig {
    private static final Logger rootLogger = Logger.getLogger("");

    public static void setup(String logFilePath) {
        try {
            for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            File logFile = new File(logFilePath);
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            Formatter customFormatter = new Formatter() {
                private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                @Override
                public String format(LogRecord record) {
                    return String.format("[%s] [%s] %s%n",
                            dateFormat.format(new Date(record.getMillis())),
                            record.getLevel(),
                            record.getMessage());
                }
            };

            FileHandler fileHandler = new FileHandler(logFilePath, true);
            fileHandler.setFormatter(customFormatter);
            rootLogger.addHandler(fileHandler);

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(customFormatter);
            rootLogger.addHandler(consoleHandler);

            rootLogger.setLevel(Level.INFO);
            Logger.getLogger("DPS").info("Logger system initialized. Outputting to: " + logFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Could not initialize file logger: " + e.getMessage());
        }
    }
}
