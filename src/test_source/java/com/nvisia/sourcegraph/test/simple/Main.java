package com.nvisia.sourcegraph.test.simple;

public class Main {


    public Greeter greeter;
    private static final String[] NAMES_TO_GREET = {
            "World", "Fred", "George", "Frank"
    };

    public Main(Greeter greeter) {
        this.greeter = greeter;
    }

    public static void main(String[] args) {
        int arbitrary = 42;
        for (String name : NAMES_TO_GREET) {
            Main main = new Main(new Greeter(name));
            main.greeter.greet();
        }
    }
}
