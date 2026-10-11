package com.pcbe;

import java.util.LinkedList;
import java.util.Random;

// An office with one FIFO queue shared by several counters. The Office object is the monitor.
public class Office {

    private static class Request {
        final String customer;
        final Document document;
        boolean done;

        Request(String customer, Document document) {
            this.customer = customer;
            this.document = document;
        }
    }

    private final String name;
    private final int counters;
    private final LinkedList<Request> queue = new LinkedList<>();

    public Office(String name, int counters) {
        this.name = name;
        this.counters = counters;
    }

    // Starts one thread per counter. Daemon threads stop automatically when the program ends.
    public void open() {
        for (int i = 1; i <= counters; i++) {
            Thread counter = new Thread(this::work, name + "-counter" + i);
            counter.setDaemon(true);
            counter.start();
        }
    }

    // Called by a customer thread: join the queue and wait until a counter issues the document.
    public synchronized void request(String customer, Document document) throws InterruptedException {
        Request request = new Request(customer, document);
        queue.addLast(request);
        System.out.println(customer + " waits at " + name + " for " + document.name() + " (queue size " + queue.size() + ")");
        notifyAll();
        while (!request.done) {
            wait();
        }

    }
    // Called by a counter thread: wait until a customer is in the queue and take the first one.
    private synchronized Request next() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        return queue.removeFirst();
    }

    // Called by a counter thread: mark the request as done and wake up the waiting customer.
    private synchronized void finish(Request request) {
        request.done = true;
        notifyAll();
    }

    // The loop run by each counter thread.
    private void work() {
        Random random = new Random();
        String counter = Thread.currentThread().getName();
        try {
            while (true) {
                Request request = next();
                Thread.sleep(100 + random.nextInt(200)); // serving the customer (no lock held)
                System.out.println(counter + " issued " + request.document.name() + " to " + request.customer);
                finish(request);

                if (random.nextInt(100) < 20) { // 20% chance of a coffee break after a customer
                    System.out.println(counter + " CLOSED for a coffee break");
                    Thread.sleep(500);
                    System.out.println(counter + " is OPEN again");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
