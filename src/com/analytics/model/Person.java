package com.analytics.model;

/** Abstract base class for anyone stored in the system (abstraction + inheritance). */
public abstract class Person {
    private final String id;
    private String name;

    protected Person(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Each subclass describes its own role (runtime polymorphism). */
    public abstract String getRole();

    @Override
    public String toString() {
        return getRole() + " [" + id + "] " + name;
    }
}
