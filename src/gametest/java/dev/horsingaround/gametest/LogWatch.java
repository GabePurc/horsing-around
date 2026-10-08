package dev.horsingaround.gametest;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

/**
 * Collects warnings and errors logged while a test rides, from the client and from the server (singleplayer's or an
 * in-process dedicated one): above all the server rejecting the rider's moves ("moved wrongly", which a player sees as
 * rubber-banding).
 */
final class LogWatch extends AbstractAppender implements AutoCloseable {
	private final List<String> warnings = new ArrayList<>();

	private LogWatch() {
		super("horsingaround-log-watch", null, null, true, Property.EMPTY_ARRAY);
	}

	static LogWatch begin() {
		final LogWatch watch = new LogWatch();
		watch.start();
		final LoggerContext context = (LoggerContext) LogManager.getContext(false);
		final Configuration config = context.getConfiguration();
		config.getRootLogger().addAppender(watch, Level.WARN, null);
		context.updateLoggers();
		return watch;
	}

	@Override
	public void append(final LogEvent event) {
		if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
			final String line = event.getLevel() + " [" + event.getLoggerName() + "] " + event.getMessage().getFormattedMessage();
			synchronized (this.warnings) {
				this.warnings.add(line);
			}
		}
	}

	/** Warnings and errors so far containing any of {@code needles} (all of them with none given). */
	List<String> matching(final String... needles) {
		synchronized (this.warnings) {
			if (needles.length == 0) {
				return List.copyOf(this.warnings);
			}
			final List<String> found = new ArrayList<>();
			for (final String line : this.warnings) {
				for (final String needle : needles) {
					if (line.contains(needle)) {
						found.add(line);
						break;
					}
				}
			}
			return found;
		}
	}

	/** Server corrections of the rider's moves: what a player sees as rubber-banding. */
	List<String> corrections() {
		return this.matching("moved wrongly", "moved too quickly", "was expected to be controlling");
	}

	void clear() {
		synchronized (this.warnings) {
			this.warnings.clear();
		}
	}

	@Override
	public void close() {
		final LoggerContext context = (LoggerContext) LogManager.getContext(false);
		context.getConfiguration().getRootLogger().removeAppender(this.getName());
		context.updateLoggers();
		this.stop();
	}
}
