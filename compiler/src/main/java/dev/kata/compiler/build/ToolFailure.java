package dev.kata.compiler.build;

/** An environment or external tool problem, reported as an E09xx diagnostic. */
public final class ToolFailure extends Exception {
    private final String code;

    public ToolFailure(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
