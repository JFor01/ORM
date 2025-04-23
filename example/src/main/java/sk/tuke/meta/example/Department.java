package sk.tuke.meta.example;

import sk.tuke.meta.persistence.annotations.Column;
import sk.tuke.meta.persistence.annotations.Table;

@Table()
public class Department {
    @Column
    private long id;
    private String name;
    @Column
    private String code;

    public Department() {
    }

    public Department(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String toString() {
        return String.format("Department %d: %s (%s)", id, name, code);
    }
    public void setId(long id){
        this.id = id;
    }
}
