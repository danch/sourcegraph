package com.nvisia.sourcegraph.test.simple;

public class Main {

    static class InnerClass {
        String test;
        public InnerClass(String value) {
            test = value;
        }
        public void doNothing() {
            System.out.println("Hallo "+test);
        }
    }
    public InnerClass innerClass;

    public Main(InnerClass innerClass) {
        this.innerClass = innerClass;
    }

    public static void main(String[] args) {
        Main main = new Main(new InnerClass("World"));
        main.innerClass.doNothing();
    }
}
