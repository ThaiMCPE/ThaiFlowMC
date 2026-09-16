package dev.thaiflowmc.loader.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A dependency version requirement such as {@code ">=0.1.0"} or an exact
 * {@code "1.2.0"}. Supports the comparators {@code >=}, {@code <=},
 * {@code >}, {@code <} and exact equality (no operator).
 */
public final class VersionRequirement {

    private static final Pattern PATTERN = Pattern.compile("^(>=|<=|>|<|=)?\\s*(.+)$");

    public enum Operator { GTE, LTE, GT, LT, EQ }

    private final Operator operator;
    private final Version version;
    private final String raw;

    private VersionRequirement(Operator operator, Version version, String raw) {
        this.operator = operator;
        this.version = version;
        this.raw = raw;
    }

    public static VersionRequirement parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("not a valid version requirement: null");
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("not a valid version requirement: \"" + raw + "\"");
        }
        String operatorText = matcher.group(1);
        Operator operator = switch (operatorText == null ? "=" : operatorText) {
            case ">=" -> Operator.GTE;
            case "<=" -> Operator.LTE;
            case ">" -> Operator.GT;
            case "<" -> Operator.LT;
            default -> Operator.EQ;
        };
        Version version = Version.parse(matcher.group(2));
        return new VersionRequirement(operator, version, raw.trim());
    }

    public boolean matches(Version candidate) {
        int cmp = candidate.compareTo(version);
        return switch (operator) {
            case GTE -> cmp >= 0;
            case LTE -> cmp <= 0;
            case GT -> cmp > 0;
            case LT -> cmp < 0;
            case EQ -> cmp == 0;
        };
    }

    public Version version() {
        return version;
    }

    @Override
    public String toString() {
        return raw;
    }
}
