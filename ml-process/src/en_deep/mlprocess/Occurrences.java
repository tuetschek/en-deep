package en_deep.mlprocess;

import en_deep.mlprocess.TaskSection.DataSourcePurpose;
import en_deep.mlprocess.exception.DataException;
import java.util.Vector;

/**
 * Used to store all occurrences of a particular file / data set / feature
 * as input / output in order to resolve dependencies. This in fact means that all the
 * {@link TaskSection}s that are listed in the {@link Occurrences.asInput} member depend on
 * the {@link TaskSection} in the {@link Occurrences.asOutput} member.
 */
class Occurrences {

    /* DATA */
    /** Count all {@link TaskSection}s where this data source shows up as the input */
    Vector<TaskDescription> asInput;
    /** The only one occurrence of {@link TaskSection} where this data source is produced as an output belongs here */
    TaskDescription asOutput;

    Occurrences() {
        super();
        asInput = new Vector<TaskDescription>();
    }

    /**
     * Adds the given {@link TaskSection} into the given list, according to the task purpose.
     * Throws an exception if the data source has more than one output occurrence.
     *
     * @param task the task to be added to the occurences list
     * @param purpose the occurrences list specificiation
     * @throws DataException if the data source has more than one output occurrence
     */
    void add(TaskDescription task, DataSourcePurpose purpose) throws DataException {
        if (purpose == DataSourcePurpose.INPUT) {
            asInput.add(task);
        } else if (this.asOutput == null) {
            this.asOutput = task;
        } else {
            throw new DataException(DataException.ERR_DUPLICATE_OUTPUT);
        }
    }
}
