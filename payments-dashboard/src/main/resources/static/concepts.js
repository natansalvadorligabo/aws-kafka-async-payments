// What each box in the diagram teaches. Keep in sync with the code and tests named in "In the code".
const CONCEPTS = {
    overview: {
        title: 'How to read this',
        body: `
            <p>A payment goes <b>Client → API → SQS → consumer</b>. The API answers <code>202</code> as soon as SQS has
            the message, and a consumer settles it later. Settling means two things: it records the payment in
            <b>DynamoDB</b> and announces it on <b>SNS</b>.</p>
            <p>Everything on this page comes from the running stack. The squares in the queue are SQS's own counters,
            and the moving dots appear only when an event was actually observed.</p>
            <h3>Try it</h3>
            <ol>
                <li><b>Stop consumer</b>, then <b>Send 10 payments</b>: they wait in the queue.</li>
                <li><b>Start consumer</b>: the queue drains and nothing is lost.</li>
                <li><b>Send duplicate</b>: two copies, one settlement.</li>
                <li><b>Send poison message</b>: it ends up in the DLQ after 30s or more.</li>
            </ol>`
    },
    client: {
        title: 'Client',
        body: `
            <p>This dashboard plays the client. <b>Send payment</b> calls the real API exactly as <code>curl</code>
            would.</p>
            <p><b>Send duplicate</b> and <b>Send poison message</b> bypass the API and write straight to SQS. That is
            how you reproduce what the network or a bad producer can do.</p>
            <h3>In the code</h3>
            <p><code>StackPaymentInjector</code> (payments-dashboard)</p>`
    },
    api: {
        title: 'payments-api: accept fast, settle later',
        body: `
            <p>The API validates the request (<code>400</code> if it is invalid), puts a <code>PaymentRequested</code>
            on SQS and answers <code>202 Accepted</code> with the <code>paymentId</code>.</p>
            <p><b>Why 202, not 200?</b> The payment is not settled yet, only safely queued. The API's latency does not
            depend on how slow or how down the consumer is.</p>
            <h3>In the code</h3>
            <p><code>PaymentController</code>, <code>SqsPaymentQueue</code>. Proven by
            <code>PaymentQueueingTest</code>.</p>`
    },
    queue: {
        title: 'SQS: waiting vs in flight',
        body: `
            <p><b>Waiting</b> (visible): no consumer has received the message yet.</p>
            <p><b>In flight</b> (not visible): a consumer received it but has not deleted it. A message is deleted
            only after settlement succeeds.</p>
            <p><b>Visibility timeout = 10s.</b> If the consumer crashes or throws, it never deletes the message. After
            10s the message becomes visible again and is redelivered. This is how SQS retries without losing anything,
            and also why the same message can arrive more than once (at-least-once delivery).</p>
            <p>Standard queues do not keep order.</p>
            <h3>In the code</h3>
            <p><code>infra/localstack/init-aws.sh</code>. Proven by <code>NoPaymentLostTest</code> and
            <code>VisibilityTimeoutRedeliveryTest</code>.</p>`
    },
    consumer: {
        title: 'Consumer: Lambda or Spring',
        body: `
            <p>Only one consumer runs at a time (<code>make up</code> or <code>make up CONSUMER=spring</code>). Both
            call the same <code>PaymentSettlement</code>.</p>
            <p><b>Lambda:</b> an <i>event source mapping</i> polls SQS and invokes the function with batches of up to
            10. A record that fails is reported back (<code>ReportBatchItemFailures</code>), so only that one returns
            to the queue. <b>Stop consumer</b> disables the mapping.</p>
            <p><b>Spring:</b> <code>@SqsListener</code> deletes a message only when the method returns normally.
            <b>Stop consumer</b> stops the listener container through <code>/actuator/listener</code>.</p>
            <h3>In the code</h3>
            <p><code>SettlePaymentsHandler</code>, <code>PaymentListener</code>, <code>PaymentSettlement</code></p>`
    },
    dynamo: {
        title: 'DynamoDB: idempotency',
        body: `
            <p>Because of at-least-once delivery, a consumer can see the same payment twice. Before settling, it
            <b>claims</b> the <code>paymentId</code>: a <code>PENDING</code> item with a lease of 8s.</p>
            <pre>PutItem status=PENDING, leaseUntil=now+8s
ConditionExpression:
  attribute_not_exists(paymentId)
  OR (status = PENDING AND leaseUntil &lt; now)</pre>
            <p>After announcing the settlement, the item becomes <code>DONE</code>. A later delivery then finds
            <b>DONE</b>: a duplicate, ignored. If it finds <b>PENDING with a live lease</b>, another delivery is busy, so it
            fails and the message comes back later. It must not be ignored.</p>
            <p><b>Why a lease?</b> A consumer that dies between claim and publish (OOM, kill, Lambda timeout) would
            otherwise leave a claim behind forever. The redelivery would take it for a duplicate, and the payment would
            never settle. The lease (8s) is shorter than the visibility timeout (10s), so by the time SQS redelivers
            an abandoned message, its claim can be taken over.</p>
            <p><b>The price:</b> a consumer that dies <i>after</i> publishing but before <code>DONE</code> gets its
            notification published again. <code>PaymentSettled</code> is at-least-once, so subscribers deduplicate by
            <code>paymentId</code>.</p>
            <p>The lease is checked in the condition, not with DynamoDB TTL: TTL deletes items up to ~48h late.</p>
            <h3>In the code</h3>
            <p><code>DynamoDbProcessedPayments</code>, <code>PaymentSettlement</code>. Proven by
            <code>IdempotentSettlementTest</code>, <code>CrashMidSettlementTest</code> and
            <code>DynamoDbProcessedPaymentsTest</code>.</p>`
    },
    sns: {
        title: 'SNS: announcing the settlement',
        body: `
            <p>After the claim, the consumer publishes <code>PaymentSettled</code> on the SNS topic
            <code>payment-settled</code>. SNS fans the message out to every subscriber. Here the only subscriber is
            the queue <code>payment-settled-audit</code>, which this dashboard reads to draw the green dots.</p>
            <p>The latency in the log is the time from the API's <code>202</code> to <code>settledAt</code>.</p>
            <h3>In the code</h3>
            <p><code>SnsSettlementNotifier</code></p>`
    },
    dlq: {
        title: 'Dead-letter queue',
        body: `
            <p>A message that fails every time (a "poison message") would otherwise be retried forever. The
            <b>redrive policy</b> moves it to <code>payments-dlq</code> once it has been received
            <code>maxReceiveCount = 3</code> times. With a 10s visibility timeout that takes at least 30s, plus however long
            the consumer waits between polls (about 50s in total with the Lambda trigger on LocalStack).</p>
            <p>Nothing is lost: the message waits in the DLQ for a person to inspect it.</p>
            <h3>In the code</h3>
            <p><code>RedrivePolicy</code> in <code>init-aws.sh</code>. Proven by <code>DeadLetterQueueTest</code>.</p>`
    }
};

CONCEPTS.kafka = {
    title: 'Kafka: a log split into partitions',
    body: `
        <p>A topic is an append-only log, split into <b>partitions</b> (6 here). Each record gets the next
        <b>offset</b> of its partition. Offsets only grow and are never reused.</p>
        <p><b>The key picks the partition</b>: <code>hash(key) % partitions</code>. The API uses the
        <code>accountId</code> as the key, so all of an account's payments land in the same lane, in the order they
        were accepted. Kafka keeps order <b>within a partition only</b>. There is no global order across lanes.</p>
        <p><b>Reading does not delete.</b> Unlike SQS, a consumed record stays in the log until retention removes it.
        Each consumer group just remembers how far it has read (its offset per partition). That is what makes replay
        possible.</p>
        <p><b>Parallelism has a ceiling:</b> inside one consumer group, a partition is read by at most one consumer.
        6 partitions means at most 6 consumers working in parallel per group.</p>
        <p>This dashboard reads with <code>assign()</code>, without a consumer group, so watching never moves anyone's
        offsets.</p>
        <h3>In the code</h3>
        <p><code>KafkaPaymentQueue</code> (the key), <code>infra/kafka/create-topics.sh</code> (the partitions),
        <code>KafkaPaymentsTopic</code>. Proven by <code>KafkaPaymentQueueingTest</code>.</p>`
};

const LIMITS = `
    <h3>What this view cannot see</h3>
    <p>SQS reports counts, not individual deliveries. Retries of a single message are not observable from the
    outside. "Duplicate ignored" is <b>inferred</b>: two copies were delivered, one settlement happened, and the queue
    is now empty.</p>`;
