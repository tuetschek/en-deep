package en_deep.mlprocess;

import en_deep.mlprocess.exception.DataException;
import java.util.Vector;

/**
 * Used to store all occurrences of a particular file / data set / feature
 * as input / output in order to resolve dependencies. This in fact means that all the
 * {@link TaskSection}s that are listed in the {@link Occurrences.asInput} member depend on
 * the {@link TaskSection} in the {@link Occurrences.asOutput} member.
 */
class Occurrences {

    /* CONSTANTS */

    /** Possible usage of files in terms of {@link Task} dependencies */
    enum Purpose {
        INPUT, OUTPUT
    }


    /* DATA */

    /** Count all {@link TaskSection}s where this data source shows up as the input */
    Vector<TaskDescription> asInput;
    /** The only one occurrence of {@link TaskSection} where this data source is produced as an output belongs here */
    TaskDescription asOutput;

    /* METHODS */

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
     * @param fileName the name of the input file that is being processed, just for error messages
     * @param line the current line in the input file, just for error messages
     * @throws DataException if the data source has more than one output occurrence
     */
    void add(TaskDescription task, Purpose purpose, String fileName, int line) throws DataException {
        if (purpose == Purpose.INPUT) {
            asInput.add(task);
        } else if (this.asOutput == null) {
            this.asOutput = task;
        } else {
            throw new DataException(DataException.ERR_DUPLICATE_OUTPUT, fileName, line);
        }
    }
}
