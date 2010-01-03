package en_deep.mlprocess;

import java.util.Vector;

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

    /** This is used to delimit ids of data source parts */
    protected static final String PART_DELIMITER = "_##PART##";

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

    @Override
    public abstract boolean equals(Object other);

    @Override
    public abstract int hashCode();

    /**
     * Split the data source into given number of parts, giving it new identification.
     *
     * @param partsNo the desired number of parts
     * @return the data source parts
     */
    public abstract Vector<DataSourceDescription> split(int partsNo);

}
