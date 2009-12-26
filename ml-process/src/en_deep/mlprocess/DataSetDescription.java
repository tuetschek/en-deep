package en_deep.mlprocess;

/**
 * Description of a Data Set data Source
 */
class DataSetDescription extends DataSourceDescription {

    /** The global ID of the Data set */
    String id;

    public DataSetDescription(String id) {
        super(DataSourceDescription.DataSourceType.DATA_SET);
        this.id = id;
    }
}
