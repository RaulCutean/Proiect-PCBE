package com.pcbe;

// One customer's request for one document. All fields are protected by the office lock.
public class Request {

    final String customer;
    final Document document;
    Counter counter;   // the counter whose queue (or window) the customer is currently at
    boolean done;      // set by the counter when the document has been issued

    Request(String customer, Document document) {
        this.customer = customer;
        this.document = document;
    }
}
