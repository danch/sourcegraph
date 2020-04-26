package com.nvisia.sourcegraph.test.simple;

public class Main {


    public Greeter greeter;

    public Main(Greeter greeter) {
        this.greeter = greeter;
    }

    public static void main(String[] args) {
        Main main = new Main(new Greeter("World"));
        main.greeter.greet();
    }
}
