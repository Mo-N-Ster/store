package test.store.spike;
import androidx.room.*;
import androidx.annotation.NonNull;
@Entity(tableName="media", foreignKeys=@ForeignKey(entity=Parent.class,parentColumns="id",childColumns="parentId"), indices=@Index("parentId"))
public class Media {
    @PrimaryKey public long id;
    public long parentId;
    @NonNull public String filename = "";
    @NonNull public String hash = "";
}
