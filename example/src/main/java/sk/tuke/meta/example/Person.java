package sk.tuke.meta.example;

import sk.tuke.meta.persistence.annotations.Column;
import sk.tuke.meta.persistence.annotations.Id;
import sk.tuke.meta.persistence.annotations.Table;

@Table(name = "Človek")
public class Person {

    private String surname;
    @Id
    @Column( name = "identification",
            unique = true
            )
    private long notId;
    @Column(
            nullable = false
    )
    private String name;
    @Column(
            name = "vek"
    )
    private int age;
    @Column


    private Department department;
    @Column
    private double height;

    public Person() {
    }

    public Person(String surname, String name, int age, double height) {
        this.surname = surname;
        this.name = name;
        this.age = age;
        this.height = height;
    }

    public long getNotId() {
        return notId;
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
        return String.format("Person %d: %s %s (%d)", notId, surname, name, age);
    }
    public void setNotId(long id){
        this.notId = id;
    }
    public void setHeight(double height){
        this.height = height;
    }
    public double getHeight(){
        return this.height;
    }
}
