package en_deep.mlprocess;

/**
 * Description of a file data source
 */
public class FileDescription extends DataSourceDescription {

    /** The name of the input/output file */
    String fileName;

    public FileDescription(String fileName) {
        super(DataSourceDescription.DataSourceType.FILE);
        this.fileName = fileName;
    }
}
