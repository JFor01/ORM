package sk.tuke.meta.example;

import sk.tuke.meta.persistence.GeneratedPersistenceManager;
import sk.tuke.meta.persistence.PersistenceManager;
import sk.tuke.meta.persistence.ReflectivePersistenceManager;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;

public class Main {
    public static final String DB_PATH = "test.db";

    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        URL url = ClassLoader.getSystemResource("build/generated/sources/annotationProcessor/java/main/resource.txt");
        if (url != null){
            url.getContent();
        }

        PersistenceManager manager = new GeneratedPersistenceManager(conn);

        manager.createTables(Person.class, Department.class, Author.class);

        exampleOperations(manager);



        conn.close();
    }

    private static void exampleOperations(PersistenceManager manager) {

        Department it = new Department("it","IT");

        Person hrasko = new Person("Janko", "Hrasko", 30);
        hrasko.setDepartment(it);
        manager.save(it);
        manager.save(hrasko);
        hrasko.setName("Juraj");
        manager.save(hrasko);

       // List<Person> people = manager.getAll(Person.class);
       // Optional<Person> person = manager.get(Person.class,10);


    }
}
