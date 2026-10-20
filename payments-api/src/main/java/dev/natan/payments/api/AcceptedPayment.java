package dev.natan.payments.api;

import java.util.UUID;

/** Body of the 202 answer: the id the client can use to follow the payment. */
record AcceptedPayment(UUID paymentId, String status) {

    static AcceptedPayment of(UUID paymentId) {
        return new AcceptedPayment(paymentId, "ACCEPTED");
    }
}
