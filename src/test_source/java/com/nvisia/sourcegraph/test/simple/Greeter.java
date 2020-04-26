package com.nvisia.sourcegraph.test.simple;

public class Greeter {
    String test;
    public Greeter(String value) {
        test = value;
    }
    public void greet() {
        String greeting = "Hallo ";
        System.out.println(greeting+test);
    }
}
