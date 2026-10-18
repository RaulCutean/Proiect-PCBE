package com.pcbe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// An office with several counters, each with its OWN queue.
// - customers join the shortest queue of an open counter;
// - a waiting customer switches to another open counter if fewer people are in front there;
// - when a counter goes on a coffee break, its customers move to the other counters;
// - at least one counter always stays open (Semaphore with counters - 1 break permits).
public class Office {

    private final String name;
    private final ReentrantLock lock = new ReentrantLock(true);      // protects all queues of this office
    private final Condition queuesChanged = lock.newCondition();     // waiting customers re-check their position
    final Semaphore breakPermits;                                    // how many counters may be on break at once
    private final List<Counter> counters = new ArrayList<>();

    public Office(String name, int counterCount) {
        this.name = name;
        for (int i = 1; i <= counterCount; i++) {
            counters.add(new Counter(this, name + "-counter" + i, lock.newCondition()));
        }
        this.breakPermits = new Semaphore(counterCount - 1);
    }

    // Starts the thread of every counter (each runs Counter.run()).
    public void open() {
        counters.forEach(Thread::start);
    }
    // ---------------------------------------------------------------- customer side


    // Called by a customer thread: choose a queue, then wait (switching queues if useful) until served.
    public void request(String customer, Document document) throws InterruptedException {
        lock.lock();
        try {
            Request request = new Request(customer, document);
            Counter chosen = shortestOpenCounter(null);
            System.out.println(customer + " joins " + chosen.getName() + " for " + document.name() + " " + queues());
            moveTo(request, chosen);

            while (!request.done) {
                considerSwitching(request);
                queuesChanged.await(); // releases the lock while waiting
            }
        } finally {
            lock.unlock();
        }
    }

    private void considerSwitching(Request request) {
    // Decides whether a waiting customer should move to another queue. Called with the lock held.
        Counter current = request.counter;
        if (current.serving == request) {
            return; // already at the window, nothing to decide
        }
        if (!current.open) {
            Counter target = shortestOpenCounter(null);
            System.out.println(request.customer + " LEAVES closed " + current.getName() + " -> " + target.getName() + " " + queues());
            moveTo(request, target);
            return;
        }
        int ahead = current.queue.indexOf(request) + (current.serving != null ? 1 : 0);
        Counter target = shortestOpenCounter(current);
        if (target != null && target.load() < ahead) {
            System.out.println(request.customer + " SWITCHES " + current.getName() + " -> " + target.getName()
                    + " (" + ahead + " ahead -> " + target.load() + " ahead) " + queues());
            moveTo(request, target);
        }
    }

    // Puts the request at the end of the target counter's queue. Called with the lock held.
    private void moveTo(Request request, Counter target) {
        if (request.counter != null) {
            request.counter.queue.remove(request);
        }
        target.queue.addLast(request);
        request.counter = target;
        target.hasWork.signal();     // wake only the counter that now has a new customer
        queuesChanged.signalAll();   // the queues changed: other customers may want to switch
    }

    // Open counter with the fewest people in front (ties: lowest number), or null if none besides 'exclude'.
    private Counter shortestOpenCounter(Counter exclude) {
        Counter best = null;
        for (Counter counter : counters) {
            if (counter.open && counter != exclude && (best == null || counter.load() < best.load())) {
                best = counter;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- counter side

    // Called by a counter thread: wait until someone is in this counter's queue and call them to the window.
    Request takeNext(Counter counter) throws InterruptedException {
        lock.lock();
        try {
            while (counter.queue.isEmpty()) {
                counter.hasWork.await();
            }
            Request request = counter.queue.removeFirst();
            counter.serving = request;
            queuesChanged.signalAll(); // everyone behind moved up one place
            return request;
        } finally {
            lock.unlock();
        }
    }

    // Called by a counter thread: the document is issued, wake the customer.
    void finish(Counter counter, Request request) {
        lock.lock();
        try {
            System.out.println(counter.getName() + " issued " + request.document.name() + " to " + request.customer);
            request.done = true;
            counter.serving = null;
            queuesChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    // Is any OTHER counter of this office open right now? (The answer may be outdated as soon as we return.)
    boolean anotherCounterOpen(Counter counter) {
        lock.lock();
        try {
            for (Counter other : counters) {
                if (other != counter && other.open) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    void closeCounter(Counter counter) {
        lock.lock();
        try {
            counter.open = false;
            System.out.println(counter.getName() + " CLOSED for a coffee break " + queues());
            queuesChanged.signalAll(); // its customers must move to open counters
        } finally {
            lock.unlock();
        }
    }

    void reopenCounter(Counter counter) {
        lock.lock();
        try {
            counter.open = true;
            System.out.println(counter.getName() + " is OPEN again " + queues());
            queuesChanged.signalAll(); // customers from long queues may switch here
        } finally {
            lock.unlock();
        }
    }

    // Snapshot of the queues for the log, e.g. "TownHall[2, 0*, 1]" (* = on coffee break).
    private String queues() {
        StringBuilder text = new StringBuilder(name).append('[');
        for (int i = 0; i < counters.size(); i++) {
            Counter counter = counters.get(i);
            text.append(i > 0 ? ", " : "").append(counter.load()).append(counter.open ? "" : "*");
        }
        return text.append(']').toString();
    }
}
