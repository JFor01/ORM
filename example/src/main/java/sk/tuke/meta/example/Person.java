package sk.tuke.meta.example;

import sk.tuke.meta.annotations.Id;
import sk.tuke.meta.annotations.Table;

@Table(name = "Človek")
public class Person {

    private String surname;
    @Id
    private long Id;
    private String name;
    private int age;

    private Department department;

    public Person() {
    }

    public Person(String surname, String name, int age) {
        this.surname = surname;
        this.name = name;
        this.age = age;
    }

    public long getId() {
        return Id;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    @Override
    public String toString() {
        return String.format("Person %d: %s %s (%d)", Id, surname, name, age);
    }
}
