package en_deep.mlprocess;

/**
 * Description of a Feature data Source
 */
class FeatureDescription extends DataSourceDescription {

    /** The global ID of the Feature */
    String id;

    public FeatureDescription(String id) {
        super(DataSourceDescription.DataSourceType.FEATURE);
        this.id = id;
    }
}
