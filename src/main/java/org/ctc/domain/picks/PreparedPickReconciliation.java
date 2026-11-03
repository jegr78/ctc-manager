package org.ctc.domain.picks;

import java.io.IOException;
import java.util.function.Function;

public record PreparedPickReconciliation<T>(Verification verification, Function<String, T> persistence) {
    @FunctionalInterface public interface Verification { String verify() throws IOException; }
    @Override public String toString() { return "PreparedPickReconciliation[internal]"; }
}
