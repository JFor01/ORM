package sk.tuke.meta.example;

import sk.tuke.meta.persistence.annotations.Table;

@Table
public class Author {
    private long id;
    private String name;

    private Person person;
    private Department department;

    public Author(){

    }
    public Author(String name, Person person, Department department){
        this.name = name;
        this.person = person;
        this.department = department;
    }
    public long getId(){
        return this.id;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public Department getDepartment(){return department;}
    void setDepartment(Department department){this.department = department;}
}
