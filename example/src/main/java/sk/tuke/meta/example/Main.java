package sk.tuke.meta.example;

import sk.tuke.meta.persistence.PersistenceManager;
import sk.tuke.meta.persistence.ReflectivePersistenceManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;

public class Main {
    public static final String DB_PATH = "test.db";

    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);

        PersistenceManager manager = new ReflectivePersistenceManager(conn);

        manager.createTables(Person.class, Department.class, Author.class);

        exampleOperations(manager);

        conn.close();
    }

    private static void exampleOperations(PersistenceManager manager) {

        Department it = new Department("it","IT");

        Person hrasko = new Person("Janko", "Hrasko", 30);
        hrasko.setDepartment(it);

        Author juraj = new Author("'??'*';'",hrasko,it);

        manager.save(it);
        manager.save(hrasko);
        manager.save(juraj);

        juraj.setName("not Juraj");
        manager.save(juraj);



        List<Person> persons = manager.getAll(Person.class);
        for (Person person : persons) {
            System.out.println(person);
            System.out.println("  " + person.getDepartment());
        }

        System.out.println("Printing author");
        Optional<Author> authorOptional = manager.get(Author.class,1);
        Author author = authorOptional.get();
        System.out.println(author.getId());
        System.out.println(author.getName());
        System.out.println(author.getPerson());
        System.out.println(author.getDepartment());
        System.out.println(author.getPerson().getId());
        System.out.println(author.getDepartment().getCode());
    }
}
