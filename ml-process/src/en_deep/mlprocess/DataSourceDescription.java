package en_deep.mlprocess;

/**
 * A data-only class (with derivees) to describe the various data sources
 * that may be used within the {@link Process}. Only the derived classes
 * may be used.
 *
 * @author Ondrej Dusek
 */
public abstract class DataSourceDescription {

    /* CONSTANTS */

    /** The possible types of data sources */
    public enum DataSourceType {
        FILE, DATA_SET, FEATURE
    }

    /* DATA */

    /** The current data source type */
    DataSourceType type;

    
    /* METHODS */
    
    /** 
     * This creates a {@link DataSourceDescription} given its type, used
     * only for the derived classes.
     * @param type the type of this {@link DataSourceDescription}
     */
    protected DataSourceDescription(DataSourceType type) {
        this.type = type;
    }
}
