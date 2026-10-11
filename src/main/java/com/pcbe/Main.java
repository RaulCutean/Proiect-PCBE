package com.pcbe;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Main {

    static void main() throws Exception {
        Properties config = new Properties();
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream("application.properties")) {
            config.load(in);
        }

        Map<String, Office> offices = new HashMap<>();
        Map<String, Document> documents = new HashMap<>();
        Map<String, List<String>> customers = new HashMap<>();

        for (String key : config.stringPropertyNames()) {
            String value = config.getProperty(key).trim();
            String name = key.substring(key.indexOf('.') + 1);
            if (key.startsWith("office.")) {
                offices.put(name, new Office(name, Integer.parseInt(value)));
            } else if (key.startsWith("document.")) {
                String[] parts = value.split("\\|", -1); // "Office|Doc1,Doc2"
                documents.put(name, new Document(name, parts[0].trim(), split(parts[1])));
            } else if (key.startsWith("customer.")) {
                customers.put(name, split(value));
            }
        }

        // A document a customer asks for must need other documents first (none can be obtained directly).
        for (List<String> wanted : customers.values()) {
            for (String doc : wanted) {
                if (documents.get(doc).requires().isEmpty()) {
                    throw new IllegalArgumentException(doc + " cannot be obtained directly");
                }
            }
        }

        offices.values().forEach(Office::open);

        List<Customer> threads = new ArrayList<>();
        customers.forEach((name, wanted) -> threads.add(new Customer(name, wanted, documents, offices)));
        for (Customer customer : threads) {
            customer.start();
        }
        for (Customer customer : threads) {
            customer.join();
        }
        System.out.println("All customers obtained their documents.");
    }

    private static List<String> split(String list) {
        List<String> result = new ArrayList<>();
        for (String item : list.split(",")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }
}
