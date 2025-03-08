package sk.tuke.meta.example;

public class Author {
    private long id;
    private String name;

    private Person person;

    public Author(String name, Person person){
        this.name = name;
        this.person = person;
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
}
