package dev.natan.payments.dashboard;

/** Fake {@link ConsumerSwitch}: an in-memory on/off flag. */
class FakeConsumerSwitch implements ConsumerSwitch {

    private volatile boolean on = true;

    @Override
    public String consumer() {
        return "fake";
    }

    @Override
    public boolean isOn() {
        return on;
    }

    @Override
    public void turnOff() {
        on = false;
    }

    @Override
    public void turnOn() {
        on = true;
    }
}
