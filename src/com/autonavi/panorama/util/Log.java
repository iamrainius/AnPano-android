package com.autonavi.panorama.util;

public class Log {
	private static Logger sLogger;
	
	public static void setLogger(Logger logger) {
		sLogger = logger;
	}
	
	public static void log(String msg) {
		if (sLogger != null) {
			sLogger.log(msg);
		}
	}
}
