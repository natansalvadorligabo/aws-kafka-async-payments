// Draws the live stack from the dashboard's Server-Sent Events: "state" (counters) and "flow" (observed events).

const ANCHORS = {
    client: [95, 200],
    api: [265, 200],
    queue: [465, 200],
    consumer: [685, 200],
    dynamo: [875, 90],
    sns: [875, 300],
    dlq: [465, 350]
};

const ROUTES = {
    ACCEPTED: {path: ['client', 'api', 'queue'], kind: 'accepted'},
    DUPLICATE_SENT: {path: ['client', 'queue'], kind: 'duplicate', copies: 2},
    POISON_SENT: {path: ['client', 'queue'], kind: 'poison'},
    SETTLED: {path: ['queue', 'consumer', 'dynamo', 'sns'], kind: 'settled'},
    DUPLICATE_IGNORED: {path: ['queue', 'consumer'], kind: 'duplicate', fade: true},
    DEAD_LETTERED: {path: ['queue', 'dlq'], kind: 'poison'}
};

const LABELS = {
    ACCEPTED: 'accepted',
    DUPLICATE_SENT: 'duplicate sent',
    POISON_SENT: 'poison sent',
    SETTLED: 'settled',
    DUPLICATE_IGNORED: 'duplicate ignored',
    DEAD_LETTERED: 'dead-lettered',
    CONSUMER_STOPPED: 'consumer down',
    CONSUMER_STARTED: 'consumer up'
};

// Replayed history (events older than this) goes to the log without animation.
const ANIMATE_IF_NEWER_THAN_MS = 3000;
const SEGMENT_MS = 450;
const MAX_SQUARES = 24;
const MAX_LOG_ROWS = 200;

const svg = document.getElementById('flow');
const tokens = document.getElementById('tokens');
const eventsTable = document.getElementById('events');
let settledThisSession = 0;
let followedPayment = null;

connect();
showConcept('overview');
bindControls();
bindConcepts();

function connect() {
    const source = new EventSource('/api/stream');
    const connection = document.getElementById('connection');
    source.onopen = () => setBadge(connection, 'live', 'ok');
    source.onerror = () => setBadge(connection, 'reconnecting…', 'warn');
    source.addEventListener('state', message => renderState(JSON.parse(message.data)));
    source.addEventListener('flow', message => onFlow(JSON.parse(message.data)));
}

function renderState(state) {
    text('waiting', state.waiting);
    text('in-flight', state.inFlight);
    text('dead-letters', state.deadLetters);
    text('settled', state.settled);
    text('pending-claims', state.pendingClaims);
    renderQueue(state.waiting, state.inFlight);

    const consumerNode = document.getElementById('consumer-node');
    consumerNode.classList.toggle('down', !state.consumerUp);
    text('consumer-name', state.consumer === 'lambda' ? 'Lambda' : 'Spring consumer');
    text('consumer-state', state.consumerUp ? 'polling the queue' : 'down');
    setBadge(document.getElementById('consumer-badge'),
        `consumer: ${state.consumer} · ${state.consumerUp ? 'up' : 'down'}`, state.consumerUp ? 'ok' : 'warn');

    const toggle = document.getElementById('consumer-toggle');
    toggle.textContent = state.consumerUp ? 'Stop consumer' : 'Start consumer';
    toggle.dataset.action = state.consumerUp ? 'consumer/off' : 'consumer/on';
}

function renderQueue(waiting, inFlight) {
    const group = document.getElementById('queue-messages');
    group.replaceChildren();
    const squares = [
        ...Array(Math.min(waiting, MAX_SQUARES)).fill('waiting'),
        ...Array(Math.min(inFlight, MAX_SQUARES)).fill('in-flight')
    ].slice(0, MAX_SQUARES);
    squares.forEach((kind, i) => {
        const square = element('rect', {
            x: (i % 8) * 18, y: Math.floor(i / 8) * 18, width: 14, height: 14, rx: 3, class: `msg ${kind}`
        });
        group.appendChild(square);
    });
    const hidden = waiting + inFlight - squares.length;
    if (hidden > 0) {
        group.appendChild(element('text', {x: 140, y: 66, class: 'overflow'}, `+${hidden}`));
    }
}

function onFlow(event) {
    if (event.type === 'SETTLED') {
        text('notified', ++settledThisSession);
    }
    prependRow(event);
    const route = ROUTES[event.type];
    const isLive = Date.now() - Date.parse(event.at) < ANIMATE_IF_NEWER_THAN_MS;
    if (route && isLive) {
        for (let copy = 0; copy < (route.copies ?? 1); copy++) {
            setTimeout(() => travel(route), copy * 180);
        }
    }
}

function travel(route) {
    const points = route.path.map(name => ANCHORS[name]);
    const token = element('circle', {r: 7, class: `token ${route.kind}`});
    tokens.appendChild(token);
    const started = performance.now();
    const total = SEGMENT_MS * (points.length - 1);

    function step(now) {
        const progress = Math.min((now - started) / total, 1);
        const segment = Math.min(Math.floor(progress * (points.length - 1)), points.length - 2);
        const local = progress * (points.length - 1) - segment;
        const [x1, y1] = points[segment];
        const [x2, y2] = points[segment + 1];
        token.setAttribute('cx', x1 + (x2 - x1) * local);
        token.setAttribute('cy', y1 + (y2 - y1) * local);
        if (route.fade) {
            token.setAttribute('opacity', 1 - progress * 0.8);
        }
        if (progress < 1) {
            requestAnimationFrame(step);
        } else {
            token.remove();
        }
    }

    requestAnimationFrame(step);
}

function prependRow(event) {
    const row = document.createElement('tr');
    row.dataset.payment = event.paymentId ?? '';
    row.classList.toggle('followed', followedPayment !== null && event.paymentId === followedPayment);

    const time = new Date(event.at).toLocaleTimeString([], {hour12: false}) +
        '.' + String(new Date(event.at).getMilliseconds()).padStart(3, '0');
    row.append(cell(time), badgeCell(event.type), paymentCell(event.paymentId), cell(event.detail));
    eventsTable.prepend(row);
    while (eventsTable.rows.length > MAX_LOG_ROWS) {
        eventsTable.deleteRow(-1);
    }
}

function paymentCell(paymentId) {
    const td = document.createElement('td');
    if (!paymentId) {
        td.textContent = '—';
        return td;
    }
    const button = document.createElement('button');
    button.className = 'payment-id';
    button.textContent = paymentId.slice(0, 8);
    button.title = paymentId;
    button.addEventListener('click', () => follow(paymentId));
    td.appendChild(button);
    return td;
}

function follow(paymentId) {
    followedPayment = followedPayment === paymentId ? null : paymentId;
    for (const row of eventsTable.rows) {
        row.classList.toggle('followed', followedPayment !== null && row.dataset.payment === followedPayment);
    }
}

function badgeCell(type) {
    const td = document.createElement('td');
    const span = document.createElement('span');
    span.className = `event ${type.toLowerCase()}`;
    span.textContent = LABELS[type] ?? type;
    td.appendChild(span);
    return td;
}

function bindControls() {
    document.querySelectorAll('.controls button').forEach(button => {
        button.addEventListener('click', async () => {
            button.disabled = true;
            try {
                const response = await fetch(`/api/${button.dataset.action}`, {method: 'POST'});
                if (!response.ok) {
                    const problem = await response.json().catch(() => ({}));
                    notice(problem.detail ?? `request failed: HTTP ${response.status}`);
                }
            } catch (error) {
                notice(`dashboard unreachable: ${error.message}`);
            } finally {
                button.disabled = false;
            }
        });
    });
}

function bindConcepts() {
    svg.querySelectorAll('.node').forEach(node => {
        node.addEventListener('click', () => showConcept(node.dataset.concept));
    });
}

function showConcept(name) {
    const concept = CONCEPTS[name];
    document.getElementById('concept-title').textContent = concept.title;
    document.getElementById('concept-body').innerHTML =
        concept.body + LIMITS + (name === 'overview' ? '' : '<p><button class="link" id="back">← How to read this</button></p>');
    document.getElementById('back')?.addEventListener('click', () => showConcept('overview'));
    svg.querySelectorAll('.node').forEach(node => node.classList.toggle('selected', node.dataset.concept === name));
}

function notice(message) {
    const target = document.getElementById('notice');
    target.textContent = message;
    setTimeout(() => target.textContent = '', 6000);
}

function setBadge(badge, label, tone) {
    badge.textContent = label;
    badge.className = `badge ${tone}`;
}

function text(id, value) {
    document.getElementById(id).textContent = value;
}

function cell(value) {
    const td = document.createElement('td');
    td.textContent = value;
    return td;
}

function element(name, attributes, content) {
    const node = document.createElementNS('http://www.w3.org/2000/svg', name);
    Object.entries(attributes).forEach(([key, value]) => node.setAttribute(key, value));
    if (content !== undefined) {
        node.textContent = content;
    }
    return node;
}
