package com.pcbe;

import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.locks.Condition;

// A counter (service window) of an office. It has its own queue and is served by its own thread.
// Its fields are protected by the lock of its office.
public class Counter extends Thread {

    final Office office;
    final LinkedList<Request> queue = new LinkedList<>(); // customers waiting at this window
    final Condition hasWork;                              // signalled when someone joins this queue
    Request serving;                                      // customer currently at the window, or null
    boolean open = true;                                  // false while on a coffee break

    Counter(Office office, String name, Condition hasWork) {
        super(name);
        this.office = office;
        this.hasWork = hasWork;
        setDaemon(true); // stops automatically when the program ends
    }

    // People in front of a newcomer: everyone waiting plus the one being served.
    int load() {
        return queue.size() + (serving != null ? 1 : 0);
    }

    @Override
    public void run() {
        Random random = new Random();
        try {
            while (true) {
                Request request = office.takeNext(this);    // waits until someone is in MY queue

                // RACE CONDITION (check-then-act): the counter CHECKS now that another counter is open,
                // but ACTS (closes) only after serving the customer, 100-300 ms later. Two counters of the
                // same office can both check while the other one is still open, and then BOTH close.
                // The office then has no open counter, and customers who must move find no counter (null).
                // The original code used breakPermits.tryAcquire(), which checks and takes in ONE atomic step.
                boolean takeBreak = random.nextInt(100) < 20 && office.anotherCounterOpen(this);

                Thread.sleep(100 + random.nextInt(200));    // serving the customer (no lock held)
                office.finish(this, request);               // gives the document to the customer

                if (takeBreak) {
                    office.closeCounter(this);               // its customers move to other queues
                    Thread.sleep(500);
                    office.reopenCounter(this);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
