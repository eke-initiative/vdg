package it.cnr.istc.stlab.lgu;

import java.text.NumberFormat;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

public class ProgressCounter {

	private AtomicLong progress = new AtomicLong(0L);
	private long steps = 10;
	private long check;
	private boolean absolute = false;
	private String prefix;
	private NumberFormat format;
	private Logger logger;
	private long t0;
	private org.slf4j.Logger slf4jLogger;
	private boolean printRate;

	public ProgressCounter() {
		check = 10000;
		absolute = true;
		init();
	}

	public ProgressCounter(long until) {
		if (until > 0) {
			check = until / steps;
			if (check == 0) {
				check = 1;
				steps = 100 / until;
			}
		} else {
			check = 10000;
			absolute = true;
		}
		init();
	}

	public ProgressCounter setPrefix(String p) {
		this.prefix = p;
		return this;
	}

	public ProgressCounter setCheckpoint(long check) {
		if (check > 0) {
			this.check = check;
		}
		return this;
	}

	private void init() {
		format = NumberFormat.getInstance();
		format.setGroupingUsed(true);
		t0 = System.currentTimeMillis();
	}

	public ProgressCounter setLogger(Logger logger) {
		this.logger = logger;
		return this;
	}

	public ProgressCounter setSLF4jLogger(org.slf4j.Logger logger) {
		this.slf4jLogger = logger;
		return this;
	}

	public long currentValue() {
		return progress.get();
	}

	public void increase() {
		if (progress.incrementAndGet() % check == 0) {
			if (!absolute) {
				logMessage(((progress.longValue() / check) * steps) + "%");
			} else {
				logMessage(format.format(progress) + " ");
			}
		}

	}

	private String formatMessage(String m) {
		return String.format("%s %s %s", prefix, m, attachRate());
	}

	private void logMessage(String m) {
		if (logger != null) {
			logger.info(formatMessage(m));
		} else if (slf4jLogger != null) {
			slf4jLogger.info(formatMessage(m));
		} else {
			System.out.println(formatMessage(m));
		}
	}

	public long value() {
		return progress.longValue();
	}

	public ProgressCounter setPrintRate(boolean printRate) {
		this.printRate = printRate;
		return this;
	}

	private String attachRate() {
		if (printRate) {
			long t1 = System.currentTimeMillis();
			long elapsed = t1 - t0;
			double timePerItem = ((double) elapsed) / ((double) progress.longValue());
			return timePerItem + "ms per item";
		}
		return "";
	}

}
