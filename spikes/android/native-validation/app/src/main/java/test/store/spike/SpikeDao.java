package test.store.spike;
import androidx.room.*;
import java.util.List;
@Dao public interface SpikeDao {
    @Insert void parent(Parent value);
    @Insert void media(Media value);
    @Query("SELECT * FROM parents ORDER BY id") List<Parent> parents();
    @Query("SELECT * FROM media ORDER BY id") List<Media> media();
}
