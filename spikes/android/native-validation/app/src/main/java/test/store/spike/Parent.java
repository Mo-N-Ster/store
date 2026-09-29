package test.store.spike;
import androidx.room.*;
import androidx.annotation.NonNull;
@Entity(tableName="parents")
public class Parent {
    @PrimaryKey public long id;
    @NonNull public String generation = "";
    public long cents;
}
