package test.store.spike;
import androidx.room.*;
@Database(entities={Parent.class,Media.class},version=1,exportSchema=false)
public abstract class SpikeDb extends RoomDatabase { public abstract SpikeDao data(); }
