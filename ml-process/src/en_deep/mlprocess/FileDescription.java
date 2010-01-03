package en_deep.mlprocess;

import java.util.Vector;

/**
 * Description of a file data source
 */
public class FileDescription extends DataSourceDescription {

    /** The name of the input/output file */
    String fileName;

    public FileDescription(String fileName) {
        super(DataSourceDescription.DataSourceType.FILE);
        this.fileName = fileName;
        // TODO normalize the file name somehow ?
    }

    @Override
    public boolean equals(Object other) {

        FileDescription otherFile;

        if (!other.getClass().equals(this.getClass())){
            return false;
        }
        otherFile = (FileDescription) other;
        return (this.fileName.equals(otherFile.fileName));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + (this.fileName != null ? this.fileName.hashCode() : 0);
        return hash;
    }

    @Override
    public Vector<DataSourceDescription> split(int partsNo) {

        Vector<DataSourceDescription> out = new Vector<DataSourceDescription>(partsNo);

        for (int i = 0; i < partsNo; ++i){
            out.add(new FileDescription(this.fileName + PART_DELIMITER + i));
        }
        return out;
    }

}
