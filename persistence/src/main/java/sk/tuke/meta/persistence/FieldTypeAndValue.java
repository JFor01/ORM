package sk.tuke.meta.persistence;

public class FieldTypeAndValue {

    private String type;
    private String value;

    public FieldTypeAndValue(String type,String value){
        this.type = type;
        this.value = value;
    }

    public FieldTypeAndValue(){

    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
