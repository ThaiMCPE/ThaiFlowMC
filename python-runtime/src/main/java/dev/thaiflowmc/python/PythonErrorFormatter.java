package dev.thaiflowmc.python;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;

/**
 * Turns a {@link PolyglotException} raised by a mod's Python code into the
 * short, readable block ThaiFlowMC shows mod authors, e.g.:
 *
 * <pre>
 * main.py line 8:
 *
 *     player.sya("Hello")
 *
 * AttributeError: 'Player' object has no attribute 'sya'
 * </pre>
 */
final class PythonErrorFormatter {

    private PythonErrorFormatter() {
    }

    static String format(String scriptName, PolyglotException e) {
        if (e.isHostException()) {
            Throwable host = e.asHostException();
            return host.getMessage() != null ? host.getMessage() : host.toString();
        }

        StringBuilder detail = new StringBuilder();
        SourceSection location = e.getSourceLocation();
        if (location != null && location.isAvailable()) {
            detail.append(scriptName).append(" line ").append(location.getStartLine()).append(":\n\n");
            String line = location.getCharacters().toString().strip();
            if (!line.isEmpty()) {
                detail.append("    ").append(line).append("\n\n");
            }
        }
        detail.append(e.getMessage() != null ? e.getMessage() : e.toString());
        return detail.toString();
    }
}
