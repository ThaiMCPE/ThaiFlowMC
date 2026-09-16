package dev.thaiflowmc.python;

import dev.thaiflowmc.api.ThaiFlowException;

/**
 * A mod's Python code raised an exception, or ThaiFlowMC failed to invoke a
 * Python callback. {@link #friendlyDetail()} is the short, readable block
 * (script name, line, and message) shown to the mod author.
 */
public final class PythonScriptException extends ThaiFlowException {

    private final String friendlyDetail;

    public PythonScriptException(String modId, String friendlyDetail, Throwable cause) {
        super("Python error in mod \"" + modId + "\": " + friendlyDetail, cause);
        this.friendlyDetail = friendlyDetail;
    }

    @Override
    public String friendlyDetail() {
        return friendlyDetail;
    }
}
