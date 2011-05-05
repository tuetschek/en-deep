/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.genfeat.Feature;
import en_deep.mlprocess.utils.StringUtils;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.util.Hashtable;
import java.util.Vector;

/**
 * A convertor to ARFF from a general table format that contains all the required information (currently supported:
 * CoNLL-like column format, ARFF).
 * @author Ondrej Dusek
 */
public class TreeDataToArff extends StLikeConvertor {
    
    /* CONSTANTS */
    
    /** The 'reader' class parameter name */
    private static final String READER = "reader";


    /* METHODS */

    /**
     * This creates a new {@link TreeDataToArff} task.
     * <p>
     * The output specification must have a "**" pattern, in order to produce more output files. If there
     * are more input files, the exactly same number of outputs (with "**") must be given.
     * </p><p>
     * <strong>Parameters:</strong>
     * </p>
     * <ul>
     * <li><tt>reader</tt> -- the Java class that handles the reading of input, must be derived from {@link DataReader}.
     * Currently, {@link ArffReader} and {@link StReader} are supported.</li>
     * <li><tt>predicted</tt> -- if set to non-false, work with predicted lemma, POS and only (only works with {@link StReader})
     * </li>
     * <li><tt>divide_ams</tt> -- if set to non-false, there will be two semantic relation attributes -- separate for
     * valency arguments and for adverbials &amp; references (only works with {@link StReader}).</li>
     * </ul>
     * <p>
     * Please see the documentation of the input reader class for their required task parameters.
     * </p>
     * <p>
     * Additional parameters may be required by the individual generated
     * {@link en_deep.mlprocess.manipulation.genfeat.Feature Feature}s, or by the super-classes.
     * </p>
     *
     * @todo no need for possible list of POS, FEAT and DEPREL in the lang_conf file, exclude it
     * @param id the task id
     * @param parameters the task parameters
     * @param input the input data sets or files
     * @param output the output data sets or files
     */
    public TreeDataToArff(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check outputs
        if (input.size() != output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
        // we don't want any patterns
        else {          
            this.eliminatePatterns(this.output);
        }
    }

    @Override
    public void perform() throws TaskException {

        try {

            for (int i = 0; i < this.input.size(); ++i){
                // convert the files
                this.convert(this.input.get(i), this.output.get(i));
            }
        }
        catch (TaskException e){
            Logger.getInstance().message("Sentence: " + this.reader.getSentenceId() + " -- "
                    + this.reader.getSentenceText(), Logger.V_DEBUG);
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw e;
        }
        catch (Exception e){
            Logger.getInstance().message("Sentence: " + this.reader.getSentenceId() + " -- "
                    + this.reader.getSentenceText(), Logger.V_DEBUG);
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }


    /**
     * Converts one file from the ST-like extended format to ARFF.
     * @param st the input file name
     * @param arff the output file name
     * @throws TaskException
     */
    private void convert(String st, String arff) throws TaskException, IOException {

        FileOutputStream os = null;
        PrintStream out = null;

        this.reader.setInputFile(st);
        this.initGenFeats();

        os = new FileOutputStream(arff);
        out = new PrintStream(os);
        this.writeHeader(out, StringUtils.truncateFileName(arff), false, false);

        while (this.reader.loadNextSentence()){

            for (int j = 0; j < this.reader.getSentenceLength(); ++j){

                out.print(this.reader.getSentenceId());

                out.print(this.reader.getInputFields(j));

                // add generated features
                for (Feature f : this.genFeats){
                    out.print("," + f.generate(j, j)); // let each word be its own predicate
                }

                out.println();
            }

            if (this.reader.getSentenceId() % 1000 == 0){
                Logger.getInstance().message(this.id + ": Input: " + st + ", sentence: " + this.reader.getSentenceId(),
                        Logger.V_DEBUG);
            }
        }

        out.close();
        out = null;
    }

    @Override
    protected void initReader() throws TaskException {

        if (!this.hasParameter(READER)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameter: " + READER);
        }
      
        String className = this.getParameterVal(READER);

        Class readerClass = null;
        Constructor readerConstructor = null;

        // retrieve the task class
        try {
            readerClass = Class.forName(className);
        }
        catch (ClassNotFoundException ex) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Reader class not found.");
        }

        // try to call a constructor
        try {
            readerConstructor = readerClass.getConstructor(Task.class);
            this.reader = (DataReader) readerConstructor.newInstance(this);
        }
        catch (Exception ex){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Could not initialize reader class.");
        }
    }

}
