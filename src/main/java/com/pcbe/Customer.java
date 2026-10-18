package com.pcbe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

// A customer is a thread that wants some documents and must first obtain all their prerequisites.
// To maximize parallelism, every needed document gets its own helper thread, which queues for the
// document as soon as all of ITS prerequisites are held. Independent documents are fetched at the same time.
public class Customer extends Thread {

    private final List<String> wanted;
    private final Map<String, Document> documents;
    private final Map<String, Office> offices;

    public Customer(String name, List<String> wanted, Map<String, Document> documents, Map<String, Office> offices) {
        super(name);
        this.wanted = wanted;
        this.documents = documents;
        this.offices = offices;
    }

    @Override
    public void run() {
        try {
            // One latch per needed document; it opens (count 1 -> 0) when the document has been obtained.
            Map<String, CountDownLatch> obtained = new LinkedHashMap<>();
            for (String name : wanted) {
                collectNeeded(documents.get(name), obtained);
            }
            System.out.println(getName() + " needs " + obtained.keySet());

            List<Thread> helpers = new ArrayList<>();
            for (String name : obtained.keySet()) {
                Document document = documents.get(name);
                Thread helper = new Thread(() -> fetch(document, obtained), getName() + "-" + name);
                helpers.add(helper);
                helper.start();
            }
            for (Thread helper : helpers) {
                helper.join(); // wait until every document has been obtained
            }
            System.out.println(">>> " + getName() + " FINISHED and has " + wanted);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Adds the document and, recursively, everything it requires (each document only once).
    private void collectNeeded(Document document, Map<String, CountDownLatch> obtained) {
        if (obtained.containsKey(document.name())) {
            return;
        }
        for (String required : document.requires()) {
            collectNeeded(documents.get(required), obtained);
        }
        obtained.put(document.name(), new CountDownLatch(1));
    }

    // Runs on a helper thread: wait until every prerequisite is held, then queue for the document.
    private void fetch(Document document, Map<String, CountDownLatch> obtained) {
        try {
            for (String required : document.requires()) {
                obtained.get(required).await();
            }
            offices.get(document.office()).request(getName(), document);
            obtained.get(document.name()).countDown(); // dependent documents may now be requested
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
