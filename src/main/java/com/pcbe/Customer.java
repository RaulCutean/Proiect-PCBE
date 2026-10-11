package com.pcbe;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// A customer is a thread that wants some documents and must first obtain all their prerequisites.
public class Customer extends Thread {

    private final List<String> wanted;
    private final Map<String, Document> documents;
    private final Map<String, Office> offices;
    private final Set<String> held = new HashSet<>();

    public Customer(String name, List<String> wanted, Map<String, Document> documents, Map<String, Office> offices) {
        super(name);
        this.wanted = wanted;
        this.documents = documents;
        this.offices = offices;
    }

    @Override
    public void run() {
        try {
            for (String name : wanted) {
                obtain(documents.get(name));
            }
            System.out.println(">>> " + getName() + " FINISHED and has " + wanted);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Recursively obtains the prerequisites first, then queues for the document itself.
    private void obtain(Document document) throws InterruptedException {
        if (held.contains(document.name())) {
            return;
        }
        for (String required : document.requires()) {
            obtain(documents.get(required));
        }
        offices.get(document.office()).request(getName(), document);
        held.add(document.name());
    }
}
