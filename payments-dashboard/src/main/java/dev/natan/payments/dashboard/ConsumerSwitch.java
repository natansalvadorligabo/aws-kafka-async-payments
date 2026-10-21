package dev.natan.payments.dashboard;

/**
 * Takes the consumer of the payments queue down and back up, to watch messages wait instead of being lost.
 *
 * <pre>{@code
 * consumerSwitch.turnOff();
 * }</pre>
 */
public interface ConsumerSwitch {

    /** "lambda" or "spring". */
    String consumer();

    boolean isOn();

    void turnOff();

    void turnOn();
}
