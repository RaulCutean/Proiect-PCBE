package com.pcbe;

import java.util.List;

// A document, the office that issues it and the documents needed to get it.
public record Document(String name, String office, List<String> requires) {
}
