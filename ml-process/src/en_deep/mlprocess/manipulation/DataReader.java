/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Task;
import en_deep.mlprocess.manipulation.posfeat.POSFeatures;
import java.io.IOException;

/**
 *
 * @author odusek
 */
public abstract class DataReader {

    /* CONSTANTS */

    /** Topological direction */
    public enum Direction {
        LEFT, RIGHT
    }

    /** Word information */
    public enum WordInfo {
        POS, LEMMA, FORM, DEPREL, PRED
    }

    /** Attribute definition start in ARFF files */
    public static final String ATTRIBUTE = "@ATTRIBUTE";
    /** Specification of an attribute as CLASS in ARFF files @todo move to StReader */
    public static final String CLASS = "";
    /** Specification of an attribute as INTEGER in ARFF files @todo move to StReader */
    public static final String INTEGER = "INTEGER";
    /** Specification of an attribute as STRING in ARFF files @todo move to StReader */
    public static final String STRING = "STRING";

    /** Index of the FEAT attribute in the output ARFF file */
    private static final int IDXO_FEAT = 7;

    /* DATA */

    /** The id of the current sentence */
    protected int sentenceId;

    /** The POS features handling class for this language, or null if not necessary. */
    public POSFeatures posFeatHandler;

    /**
     * Name of the {@link en_deep.mlprocess.manipulation.posfeat.POSFeatures} subclass that should handle the
     * POS features of this language, or null.
     */
    public String posFeatName;

    /** The task this reader works for. */
    protected final Task task;

    /* METHODS */

    public abstract int[] getChildren(int wordNo);

    public abstract int getHead(int wordNo);

    public abstract int getSibling(int wordNo, Direction direction);


    public abstract String getWordInfo(int word, WordInfo info);

    public abstract String getWordInfo(int word, int attributeNumber);

    public abstract String[] getWordsInfo(int[] words, WordInfo info);

    
    /**
     * Reads the next sentence from the input file. Generates an ID for the sentence.
     * On EOF, closes the input file.
     *
     * @return true if successful, false on EOF
     */
    abstract boolean loadNextSentence() throws IOException;

    public abstract int getSentenceLength();

    abstract String getArffHeaders();
    
    abstract String getSemRolesHeader();


    /**
     * This returns all the input information about the given word (starting with a comma,
     * fields enclosed in quotes if necessary), i.e\. everything except generated features. It returns all
     * the POS features values if applicable; the missing values are returned as ARFF unquoted '?'.
     *
     * @param wordNo the number of the word in the current sentence
     * @return all the input information for the given word, in quotes
     */
    public abstract String getInputFields(int wordNo);


    protected DataReader(Task task){
        this.task = task;
    }

    /**
     * If there is a name of the POS handling class in the configuration file, this will try to initialize
     * it. If the class is not found in the {@link en_deep.mlprocess.manipulation.genfeat} package, the process
     * will fail.
     */
    protected void initPOSFeats() throws IOException {
        if (this.posFeatName != null) {
            this.posFeatHandler = POSFeatures.createHandler(this.posFeatName);
            if (this.posFeatHandler == null) {
                throw new IOException("POS feature handling " + "class `" + this.posFeatName + "' creation failed.");
            }
        }
    }

    /**
     * Sets a new input file and opens it.
     *
     * @param inputFile the path to the new input ST file
     */
    abstract void setInputFile(String fileName) throws IOException;

    /**
     * Returns the id of the current task.
     * @return the id of the current task
     */
    public String getTaskId() {
        return this.task.getId();
    }

    /**
     * Returns the value of the given task parameter.
     * @param paramName the desired parameter name
     * @return the value of the given task parameter
     */
    public String getTaskParameter(String paramName) {
        return this.task.getParameterVal(paramName);
    }

    /**
     * This is for debugging purposes (exceptions etc.), it returns the full text of the currently
     * loaded sentence.
     *
     * @return the text of the currently loaded sentence
     */
    public String getSentenceText(){

        if (this.getSentenceLength() == -1){
            return "[NULL-No sentence currently loaded]";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.getSentenceLength(); ++i){
            sb.append(this.getWordInfo(i, WordInfo.FORM));
            if (i < this.getSentenceLength() -1){
                sb.append(" ");
            }
        }
        return sb.toString();
    }


    /**
     * Returns the ID of the current sentence.
     * @return the sentence ID
     */
    public int getSentenceId(){
        return this.sentenceId;
    }



}
