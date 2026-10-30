package net.onelitefeather.quelea.velocity.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Records every report instead of sending it.
 */
public final class FakeErrorReporter implements ErrorReporter {

    /** One report: the error (or message) and its tags. */
    public record Report(Throwable error, String message, Map<String, String> tags) {
    }

    private final List<Report> reports = new ArrayList<>();

    @Override
    public void report(Throwable error, Map<String, String> tags) {
        reports.add(new Report(error, null, tags));
    }

    @Override
    public void report(String message, Map<String, String> tags) {
        reports.add(new Report(null, message, tags));
    }

    public List<Report> reports() {
        return List.copyOf(reports);
    }
}
