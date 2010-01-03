package en_deep.mlprocess;

import java.util.Vector;

/**
 * Description of a Feature data Source
 */
public class FeatureDescription extends DataSourceDescription {

    /** The global ID of the Feature */
    String id;

    /**
     * The id of the feature that contains the data set (may be null, if the data set is not specified
     * in the input file - for Computation and Evaluation tasks, if the feature applies to all data sets)
     */
    String dataSetId;

    public FeatureDescription(String id, String dataSetId) {
        super(DataSourceDescription.DataSourceType.FEATURE);
        this.id = id;
        this.dataSetId = dataSetId;
    }

    @Override
    public boolean equals(Object other) {

        FeatureDescription otherFeat;

        if (!other.getClass().equals(this.getClass())){
            return false;
        }
        otherFeat = (FeatureDescription) other;
        return (this.id.equals(otherFeat.id) && ((this.dataSetId == null && otherFeat.dataSetId == null) ||
                (this.dataSetId != null && otherFeat.dataSetId != null && this.dataSetId.equals(otherFeat.dataSetId))));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + (this.id != null ? this.id.hashCode() : 0);
        hash = 79 * hash + (this.dataSetId != null ? this.dataSetId.hashCode() : 0);
        return hash;
    }

    /**
     * This copies the original feature n-times into a vector, because there's no point
     * in splitting features.
     *
     * @param partsNo the desired number of times the feature will be copied
     * @return vector of the feature's copies
     */
    @Override
    public Vector<DataSourceDescription> split(int partsNo) {

        Vector<DataSourceDescription> out = new Vector<DataSourceDescription>(partsNo);

        for (int i = 0; i < partsNo; ++i){
            out.add(this);
        }
        return out;
    }

}
