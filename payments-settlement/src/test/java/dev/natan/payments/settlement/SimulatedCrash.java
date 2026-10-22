package dev.natan.payments.settlement;

/**
 * Stands in for a process dying mid-settlement (OOM, kill, Lambda timeout): an {@link Error}, so no
 * {@code catch (RuntimeException)} cleans up after it.
 */
public class SimulatedCrash extends Error {

    public SimulatedCrash(String message) {
        super(message);
    }
}
