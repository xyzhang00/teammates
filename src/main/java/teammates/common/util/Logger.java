package teammates.common.util;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Allows any component of the application to log messages at appropriate levels.
 */
@SuppressWarnings("PMD.MoreThanOneLogger") // class is designed as a facade for two different loggers
public final class Logger {

    private final java.util.logging.Logger standardLog;
    private final java.util.logging.Logger errorLog;

    private Logger() {
        StackTraceElement logRequester = getLoggerSource();
        String loggerName = logRequester == null ? "null" : logRequester.getClassName();
        this.standardLog = java.util.logging.Logger.getLogger(loggerName + "-out");
        this.standardLog.setUseParentHandlers(false);
        this.standardLog.addHandler(new StdOutConsoleHandler());

        this.errorLog = java.util.logging.Logger.getLogger(loggerName + "-err");
    }

    public static Logger getLogger() {
        return new Logger();
    }

    /**
     * Logs a message at FINE level.
     */
    public void fine(String message) {
        standardLog.fine(formatLogMessage(message, "DEBUG"));
    }

    /**
     * Logs a message at INFO level.
     */
    public void info(String message) {
        standardLog.info(formatLogMessage(message, "INFO"));
    }

    /**
     * Logs an HTTP request.
     */
    public void request(HttpServletRequest request, int statusCode, String message,
                        Map<String, String> userInfo, Map<String, Object> extraInfo) {
        long timeElapsed = RequestTracer.getTimeElapsedMillis();
        String method = request.getMethod();
        String requestUrl = request.getRequestURI();
        Map<String, Object> requestDetails = new HashMap<>();
        requestDetails.put("responseStatus", statusCode);
        requestDetails.put("responseTime", timeElapsed);
        requestDetails.put("requestMethod", method);
        requestDetails.put("requestUrl", requestUrl);
        requestDetails.put("userAgent", request.getHeader("User-Agent"));
        requestDetails.put("requestParams", HttpRequestHelper.getRequestParameters(request));
        requestDetails.put("requestHeaders", HttpRequestHelper.getRequestHeaders(request));

        if (request.getParameter(Const.ParamsNames.REGKEY) != null) {
            userInfo.putIfAbsent("regkey", request.getParameter(Const.ParamsNames.REGKEY));
        }
        requestDetails.put("userInfo", userInfo);
        requestDetails.putAll(extraInfo);

        String logMessage = String.format("[%s] [%sms] [%s %s] %s",
                statusCode, timeElapsed, method, requestUrl, message);

        event(LogEvent.REQUEST_LOG, logMessage, requestDetails);
    }

    /**
     * Logs a particular event at INFO level.
     */
    public void event(LogEvent event, String message, Map<String, Object> details) {
        String logMessage;
        if (Config.isDevServer()) {
            logMessage = formatLogMessageForHumanDisplay(message) + " extra_info: "
                    + JsonUtils.toCompactJson(details);
        } else {
            Map<String, Object> payload = getBaseCloudLoggingPayload(message, "INFO");
            payload.putAll(details);
            payload.put("event", event);

            logMessage = JsonUtils.toCompactJson(payload);
        }
        standardLog.info(logMessage);
    }

    /**
     * Logs a message at WARNING level.
     */
    public void warning(String message) {
        standardLog.warning(formatLogMessage(message, "WARNING"));
    }

    /**
     * Logs a message at SEVERE level.
     */
    public void severe(String message) {
        errorLog.severe(formatLogMessage(message, "ERROR"));
    }

    private String formatLogMessage(String message, String severity) {
        if (Config.isDevServer()) {
            return formatLogMessageForHumanDisplay(message);
        }
        return formatLogMessageForCloudLogging(message, severity);
    }

    private String formatLogMessageForHumanDisplay(String message) {
        StringBuilder prefix = new StringBuilder();

        StackTraceElement source = getLoggerSource();
        if (source != null) {
            prefix.append(source.getClassName()).append(':')
                    .append(source.getMethodName()).append(':')
                    .append(source.getLineNumber()).append(':');
        }
        prefix.append(' ');

        if (RequestTracer.getTraceId() == null) {
            return prefix.toString() + message;
        }
        return prefix.toString() + "[" + RequestTracer.getTraceId() + "] " + message;
    }

    private String formatLogMessageForCloudLogging(String message, String severity) {
        return JsonUtils.toCompactJson(getBaseCloudLoggingPayload(message, severity));
    }

    private Map<String, Object> getBaseCloudLoggingPayload(String message, String severity) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        payload.put("severity", severity);

        StackTraceElement source = getLoggerSource();
        if (source != null) {
            Map<String, Object> sourceLocation = new HashMap<>();
            sourceLocation.put("file", source.getClassName());
            sourceLocation.put("line", source.getLineNumber());
            sourceLocation.put("function", source.getMethodName());

            payload.put("logging.googleapis.com/sourceLocation", sourceLocation);
        }

        if (RequestTracer.getTraceId() != null) {
            payload.put("logging.googleapis.com/trace",
                    "projects/" + Config.APP_ID + "/traces/" + RequestTracer.getTraceId());
        }

        if (RequestTracer.getSpanId() != null) {
            payload.put("logging.googleapis.com/spanId", RequestTracer.getSpanId());
        }

        return payload;
    }

    private StackTraceElement getLoggerSource() {
        StackTraceElement[] stes = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stes.length; i++) {
            StackTraceElement ste = stes[i];
            if (ste.getClassName().equals(Logger.class.getName()) && i + 1 < stes.length) {
                StackTraceElement nextSte = stes[i + 1];
                if (!nextSte.getClassName().equals(Logger.class.getName())) {
                    return nextSte;
                }
            }
        }
        return null;
    }

}
