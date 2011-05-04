/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Task;
import en_deep.mlprocess.manipulation.featmodif.FeatureModifier;
import java.io.IOException;
import java.util.ArrayList;

/**
 * This is an abstract class for classes providing input to data conversion and feature generation.
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
        POS, LEMMA, FORM, SYNT_REL, PRED, HEAD, PFEAT
    }

    /** Attribute definition start in ARFF files */
    public static final String ATTRIBUTE = "@ATTRIBUTE";
    /** Specification of an attribute as CLASS in ARFF files @todo move to StReader */
    public static final String CLASS = "";
    /** Specification of an attribute as INTEGER in ARFF files @todo move to StReader */
    public static final String INTEGER = "INTEGER";
    /** Specification of an attribute as STRING in ARFF files @todo move to StReader */
    public static final String STRING = "STRING";

    /* DATA */

    /** The id of the current sentence */
    protected int sentenceId;

    /** The POS features handling class for this language, or null if not necessary. */
    public FeatureModifier posFeatHandler;

    /**
     * Name of the {@link en_deep.mlprocess.manipulation.posfeat.FeatureModifier} subclass that should handle the
     * POS features of this language, or null.
     */
    public String posFeatHandlerName;

    /** The task this reader works for. */
    protected final Task task;

    /** Line feed character in the current OS */
    protected static final String LF = System.getProperty("line.separator");

    /* METHODS */

    /**
     * Create a new {@link DataReader} object, possibly reading some configuration from the task parameters.
     * @param task the {@link Task} that will use this object
     */
    protected DataReader(Task task){
        this.task = task;
    }

    
    /**
     * Returns the word numbers of the syntactical children for the given word in the
     * currently loaded sentence.
     *
     * @param wordNo the word to get the children for
     * @return the children of the given word
     */
    public int [] getChildren(int wordNo){

        ArrayList<Integer> children = new ArrayList<Integer>();
        // find all children
        for (int i = 0; i < this.getSentenceLength(); ++i){
            if (this.getWordInfo(i, WordInfo.HEAD).equals(Integer.toString(wordNo + 1))){
                children.add(i);
            }
        }

        // save them to an array
        int [] ret = new int [children.size()];
        for (int i = 0; i < children.size(); ++i){
            ret[i] = children.get(i);
        }
        return ret;
    }


    /**
     * Returns the word position of the syntactic head of the given word (not its ID!).
     * @param wordNo the word to look up the head for
     * @return the position of the syntactic head of the given word, or -1 for the root node
     */
    public int getHead(int wordNo) {

        if (wordNo < 0 || wordNo >= this.getSentenceLength()){
            return -1;
        }

        int pos = Integer.parseInt(this.getWordInfo(wordNo, WordInfo.HEAD));

        // head ID is the same as (position + 1), root node has head ID "0", so "-1" will be returned
        return (pos - 1);
    }


    /**
     * Returns the position of the given syntactical sibling of the given word.
     *
     * @param wordNo the order of the desired word
     * @param whichOne which (left or right) sibling to get
     * @return the position of the desired sibling, or -1, if it does not exist
     */
    public int getSibling(int wordNo, Direction whichOne){

        // find the mother node number
        String motherNo = this.getWordInfo(wordNo, WordInfo.HEAD);

        // left sibling
        if (whichOne == Direction.LEFT){
            int ret = -1;

            for (int i = 0; i < wordNo; ++i){
                if (this.getWordInfo(i, WordInfo.HEAD).equals(motherNo)){
                    ret = i;
                }
            }
            return ret;
        }
        // right sibling
        else {
            for (int i = wordNo + 1; i < this.getSentenceLength(); ++i){
                if (this.getWordInfo(i, WordInfo.HEAD).equals(motherNo)){
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Returns the number of input data column for the given information type.
     * @param wordInfo the information type in question
     * @return the number of corresponding input data column
     */
    protected abstract int getInfoPos(WordInfo wordInfo);
    

    /**
     * Returns the desired {@link WordInfo} information for the given word.
     *
     * @param word the word in question
     * @param info the desired type of information
     * @return the requested information for the given word
     */
    public String getWordInfo(int word, WordInfo info){
        return this.getWordInfo(word, this.getInfoPos(info));
    }

    /**
     * Returns the desired column from the input data for the given word.
     *
     * @param word the word in question
     * @param attributeNumber the desired column number
     * @return the requested information column of the input data for the given word
     */
    public abstract String getWordInfo(int word, int attributeNumber);

    /**
     * Returns the desired {@link WordInfo} information for the given words.
     *
     * @param words the words in question
     * @param info the desired type of information
     * @return the requested information for the given words
     */
    public String[] getWordsInfo(int[] words, WordInfo info){

        String [] ret = new String [words.length];
        int field = this.getInfoPos(info);

        for (int i = 0; i < words.length; ++i){
            ret[i] = this.getWordInfo(words[i], field);
        }
        return ret;
    }

    
    /**
     * Reads the next sentence from the input file. Generates an ID for the sentence.
     * On EOF, closes the input file.
     *
     * @return true if successful, false on EOF
     */
    abstract boolean loadNextSentence() throws IOException;

    /**
     * Returns the number of words in the current sentence, -1 if no sentence loaded.
     * @return the length of the current sentence
     */
    public abstract int getSentenceLength();

    /**
     * Returns the headers for the input columns, which are always written to the ARFF output.
     * All input data columns except for sentence and word ID and syntactic head ID are always
     * assumed to be strings. There should be no LF character at the end of this string.
     *
     * @return the ARFF header for the input fields
     */
    abstract String getArffHeaders();
    

    /**
     * Returns the output ARFF header for the target class. There should be no newline at the end.
     * @return the semantic class headers
     */
    abstract String getTargetClassHeader();


    /**
     * This returns all the input information about the given word (starting with a comma,
     * fields enclosed in quotes if necessary), i.e\. everything except generated features. It returns all
     * the POS features values if applicable; the missing values are returned as ARFF unquoted '?'.
     *
     * @param wordNo the number of the word in the current sentence
     * @return all the input information for the given word, in quotes
     */
    public abstract String getInputFields(int wordNo);


    /**
     * If there is a name of the POS handling class in the configuration file, this will try to initialize
     * it. If the class is not found in the {@link en_deep.mlprocess.manipulation.genfeat} package, the process
     * will fail.
     */
    protected void initPOSFeats() throws IOException {
        if (this.posFeatHandlerName != null) {
            this.posFeatHandler = FeatureModifier.createHandler(this.posFeatHandlerName);
            if (this.posFeatHandler == null) {
                throw new IOException("POS feature handling " + "class `" + this.posFeatHandlerName + "' creation failed.");
            }
        }
    }

    /**
     * Sets a new input file and opens it.
     *
     * @param inputFile the path to the new input data file
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
