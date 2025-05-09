package sk.tuke.meta.persistence;

import java.net.ConnectException;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface EntityDAO<T> {
    void createTable();

    Optional<T> get(long id);

    List<T> getAll();

    void save(Object entity);

    void delete(Object entity);
    Connection getConnection();

}
