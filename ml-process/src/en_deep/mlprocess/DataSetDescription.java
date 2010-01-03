package en_deep.mlprocess;

import java.util.Vector;

/**
 * Description of a Data Set data Source
 */
public class DataSetDescription extends DataSourceDescription {

    /** The global ID of the Data set */
    String id;

    public DataSetDescription(String id) {
        super(DataSourceDescription.DataSourceType.DATA_SET);
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {

        DataSetDescription otherDS;

        if (!other.getClass().equals(this.getClass())){
            return false;
        }
        otherDS = (DataSetDescription) other;
        return (this.id.equals(otherDS.id));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

    @Override
    public Vector<DataSourceDescription> split(int partsNo) {

        Vector<DataSourceDescription> out = new Vector<DataSourceDescription>(partsNo);

        for (int i = 0; i < partsNo; ++i){
            out.add(new DataSetDescription(this.id + DataSourceDescription.PART_DELIMITER + i));
        }
        return out;
    }

}
