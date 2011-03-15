/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.genfeat.Feature;
import en_deep.mlprocess.utils.StringUtils;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Vector;

/**
 * A convertor from an extended CoNLL-like format to ARFF, which disregards predicate-argument relations.
 * @author Ondrej Dusek
 */
public class StLikeToArff extends StLikeConvertor {

    /**
     * This creates a new {@link StToArff} task.
     * <p>
     * The output specification must have a "**" pattern, in order to produce more output files. If there
     * are more input files, the exactly same number of outputs (with "**") must be given.
     * </p><p>
     * <strong>Parameters:</strong>
     * </p>
     * <ul>
     * <li><tt>lang_conf</tt> -- path to the language reader file, that contains:
     * <ul>
     *   <li>a FEAT usage indication (name of the handling class derived from {@link Feature}, or empty line)</li>
     *   <li>noun and verb tag regexp patterns (each on separate line)</li>
     *   <li>list of all possible semantic roles (one line, space-separated)</li>
     *   <li>a regexp that catches all adverbial modifier semantic roles</li>
     *   <li>a space-separated list of additional columns in the ST file, if any</li>
     * </ul></li>
     * <li><tt>predicted</tt> -- if set to non-false, work with predicted lemma, POS and only </li>
     * <li><tt>divide_ams</tt> -- if set to non-false, there will be two semantic relation attributes -- separate for
     * valency arguments and for adverbials &amp; references.</li>
     * <li><tt>pred_only</tt> -- if set to non-false, only predicates are output, omitting all other words in the sentence</li>
     * <li><tt>divide_senses</tt> -- if set to non-false, the data are divided according to the sense of predicates, too</li>
     * <li><tt>prune</tt> -- if set, the argument candidates are pruned (syntactical neighborhood of the predicate only)</li>
     * <li><tt>filt_pos</tt> -- (optional) provide a space-separated list of POS which should be filtered at the output,
     * e.g. meaningful for English are: "'' ( ) , . : `` EX HYPH LS NIL POS"</li>
     * <li><tt>one_file</tt> -- this turns the one-file-mode on. If set, the headers won't be set to nominal and the output
     * will go into one file only</li>
     * </ul>
     * <p>
     * Additional parameters may be required by the individual generated {@link en_deep.mlprocess.manipulation.genfeat.Feature Feature}s,
     * or by the super-classes.
     * </p>
     *
     * @todo no need for possible list of POS, FEAT and DEPREL in the lang_conf file, exclude it
     * @param id the task id
     * @param parameters the task parameters
     * @param input the input data sets or files
     * @param output the output data sets or files
     */
    public StLikeToArff(String id, Hashtable<String, String> parameters,
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

        os = new FileOutputStream(arff);
        out = new PrintStream(os);
        this.writeHeader(out, StringUtils.truncateFileName(arff), false, true);

        while (this.reader.loadNextSentence()){

            for (int j = 0; j < this.reader.length(); ++j){

                out.print(this.reader.getSentenceId());

                out.print(this.reader.getCompulsoryFields(j));

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

}
