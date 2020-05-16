package com.nvisia.sourcegraph.graph;

public enum EdgeType {
    Contains, //the from element contains the other lexically
    DependsOn, //the from element depends on to type (think UML 'dependency'
    References, //the from element holds 1 or more references to instances of the to
    Executes, Calls
}
