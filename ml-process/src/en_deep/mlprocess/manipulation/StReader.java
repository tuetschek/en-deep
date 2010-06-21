/*
 *  Copyright (c) 2009 Ondrej Dusek
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification,
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this
 *  list of conditions and the following disclaimer in the documentation and/or other
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific
 *  prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.StringUtils;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

/**
 * This stores all the configuration needed for the conversion an the generated
 * features as well.
 */
public class StReader {

    /* CONSTANTS */

    /** Topological direction */
    public enum Direction {
        LEFT, RIGHT
    }

    /** Name of the ARFF lemma attribute */
    public final String LEMMA;
    /** Name of the ARFF POS attribute */
    public final String POS;
    /** Name of the ARFF pred attribute */
    public final String PRED = "pred";

    /** Index of the word id in the ST file */
    public final int IDXI_WORDID = 0;
    /** Index of the FORM attribute in the ST file */
    public final int IDXI_FORM = 1;
    /** Index of the FILLPRED attribute in the ST file */
    public final int IDXI_FILLPRED = 12;
    /** Starting index of the semantic roles (APRED) in the ST file */
    public final int IDXI_SEMROLE = 14;
    /** Index of the POS attribute in the ST file */
    public final int IDXI_POS;
    /** Index of the lemma attribute in the ST file */
    public final int IDXI_LEMMA;
    /** Index of the HEAD attribute in the ST file */
    public final int IDXI_HEAD;
    /** Index of the DEPREL attribute in the ST file */
    public final int IDXI_DEPREL;
    /** Index of the DEPREL attribute in the ST file */
    public final int IDXI_FEAT;
    /** Index of the PRED attribute in the ST file */
    public final int IDXI_PRED = 13;

    /** Number of compulsory fields that are in each sentence in the ST file */
    final int COMPULSORY_FIELDS = 14;

    /** The default value for ST file fields. */
    private String DEFAULT_VALUE = "_";

    /** Output file suffix for noun predicates */
    private static final String NOUN = ".n";
    /** Output file suffix for verb predicates */
    private static final String VERB = ".v";
    /** Output file suffix for erroneously tagged predicates */
    private static final String TAG_ERR = ".e";

    /* VARIABLES */

    /** Sentence ID generation -- last used value */
    private static int lastSentenceId = 0;

    /** Will be FEAT values used for this language? */
    public boolean posFeat;
    /** Possible semantic roles */
    public String[] semRoles;
    /** Tag pattern for verbs in the ST file */
    public String nounPat;
    /** Tag pattern for nouns in the ST file */
    public String verbPat;
    /** The current input file */
    private RandomAccessFile inputFile;
    /** The name of the current input file */
    private String inputFileName;
    /** Use predicted POS and DEPREL values ? */
    private final boolean usePredicted;
    /**
     * Always -1, if usePredicted is true, 1 otherwise -- in order to cover the second
     * (predicted or non-predicted) member by IDXI_ .. + predictedNon.
     */
    final int predictedNon;
    /** The {@link StManipulation} task this reader works for. */
    private final StManipulation task;
    
    /** The data for the current sentence */
    private Vector<String []> words;
    /** The id of the current sentence */
    private int sentenceId;


    /* METHODS */

    /**
     * This initializes an StReader -- reads the language configuration from a file,
     * i.e\. all SEMREL values, noun and verb POS tag pattern and usage of FEAT for the
     * current ST file language.
     *
     * @param task the StToArff task for this conversion
     */
    StReader(StManipulation task) throws IOException {

        // set main parameters
        this.task = task;
        this.usePredicted = this.task.getBooleanParameterVal(StToArff.PREDICTED);

        // set the constant values
        if (this.usePredicted) {
            IDXI_POS = 5;
            IDXI_LEMMA = 3;
            IDXI_HEAD = 9;
            IDXI_DEPREL = 11;
            IDXI_FEAT = 7;
            this.predictedNon = -1;
            LEMMA = "p-lemma";
            POS = "p-pos";
        } else {
            IDXI_POS = 4;
            IDXI_LEMMA = 2;
            IDXI_HEAD = 8;
            IDXI_DEPREL = 10;
            IDXI_FEAT = 6;
            this.predictedNon = 1;
            LEMMA = "lemma";
            POS = "pos";
        }

        // read the config file
        RandomAccessFile config = new RandomAccessFile(this.task.getParameterVal(StToArff.LANG_CONF), "r");

        this.posFeat = config.readLine().matches("\\s*") ? false : true;
        this.nounPat = config.readLine();
        this.verbPat = config.readLine();

        String semRolesStr = config.readLine();

        config.close();
        config = null;
        if (semRolesStr == null || this.nounPat == null || this.verbPat == null) {
            throw new IOException();
        }

        this.semRoles = semRolesStr.split("\\s+");
    }

    /**
     * Sets a new input file and opens it.
     *
     * @param inputFile the path to the new input ST file
     */
    void setInputFile(String fileName) throws IOException {

        this.inputFileName = fileName;
        this.inputFile = new RandomAccessFile(fileName, "r");
    }

    /**
     * Return a comma-separated list of possible semantic roles
     * @return list of semantic roles
     */
    String getSemRoles() {
        return this.listMembers(this.semRoles);
    }

    /**
     * Return a comma-separated list of items from an array
     * @param arr the array to be transformed to a string
     * @return a comma-separated list in string form
     */
    private String listMembers(String[] arr) {
        StringBuilder sb = new StringBuilder();
        if (arr == null || arr.length == 0) {
            return "";
        }
        sb.append("\"").append(StringUtils.escape(arr[0])).append("\"");
        for (int j = 1; j < arr.length; ++j) {
            sb.append(",\"").append(StringUtils.escape(arr[j])).append("\"");
        }
        return sb.toString();
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
     * Returns the id of the current task.
     * @return the id of the current task
     */
    public String getTaskId() {
        return this.task.getId();
    }

    /**
     * Reads the sentence from the input ST file. Generates an ID for the sentence.
     * On EOF, closes the input file.
     *
     * @return true if successful, false on EOF
     */
    boolean loadNextSentence() throws IOException {

        String word;

        this.words = new Vector<String []>();
        word = this.inputFile.readLine();

        while (word != null && !word.matches("^\\s*$")){

            words.add(word.split("\\t"));
            word = this.inputFile.readLine();
        }

        if (this.words.isEmpty()){ // close the file if at the end
            this.inputFile.close();
            this.inputFile = null;
        }
        else { // create an ID for the new valid sentence
            this.sentenceId = generateSentenceId();
        }
        return (!this.words.isEmpty());
    }

    /**
     * Returns a list of positions where the predicates in this sentence are.
     *
     * @param sentence the sentence to be processed
     * @param sentenceId the current sentence id
     * @param fileName the file name
     * @return the list of predicate positions in the sentence
     */
    int[] getPredicates() throws TaskException {

        int [] ret = new int [this.words.get(0).length - COMPULSORY_FIELDS];
        int pos = 0;

        for (int i = 0; i < this.length(); ++i){ // collect all predicate positions
            if (this.getWordInfo(i, IDXI_FILLPRED).equals("Y")){
                ret[pos] = i;
                pos++;
            }
        }
        if (pos != ret.length){ // check their number
            throw new TaskException(TaskException.ERR_IO_ERROR, this.task.getId(),
                    "Predicate and fillpred column numbers mismatch in " + this.inputFileName + " at sentence "
                    + sentenceId + ".");
        }
        return ret;
    }

    /**
     * Returns the number of words in the sentence.
     * @return the length of the sentence
     */
    public int length() {
        return this.words.size();
    }

    /**
     * Returns all the information about the given word.
     * @param wordNo the number of the word in the sentence
     * @return the whole information about the word
     */
    String[] getWord(int wordNo) {
        return this.words.get(wordNo);
    }

    /**
     * Returns the given piece of information about the given word, as it appears in the ST file.
     * This is protected against out-of-range errors -- empty string is returned for out-of-range
     * requests.
     * 
     * @param wordNo the number of the word in the sentence
     * @param fieldNo the number of the desired field from the ST file
     * @return the given field of the given word form the ST file format
     */
    public String getWordInfo(int wordNo, int fieldNo){

        if (wordNo < 0 || wordNo >= this.words.size()){
            return "";
        }
        return this.words.get(wordNo)[fieldNo];
    }

    /**
     * Returns the given piece of information about for several words in a sentence.
     *
     * @param wordNos the numbers of the desired words in the sentence
     * @param fieldNo the number of the desired field from the ST file
     * @return the given field of the given word form the ST file format
     */
    public String [] getWordsInfo(int [] wordNos, int fieldNo){

        String [] ret = new String [wordNos.length];

        for (int i = 0; i < wordNos.length; ++i){
            ret[i] = this.words.get(wordNos[i])[fieldNo];
        }
        return ret;
    }

    /**
     * Returns the numbers of the syntactical children for the given word in the
     * currently loaded sentence.
     * 
     * @param wordNo the word to get the children for
     * @return the children of the given word
     */
    public int [] getChildrenPos(int wordNo){

        ArrayList<Integer> children = new ArrayList<Integer>();
        // find all children
        for (int i = 0; i < this.length(); ++i){
            if (this.getWordInfo(i, IDXI_HEAD).equals(Integer.toString(wordNo + 1))){
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
     * Returns the ID of the sentence that it was given in the constructor.
     * @return the sentence ID
     */
    public int getSentenceId(){
        return this.sentenceId;
    }

   /**
     * Returns a unique sentence ID.
     *
     * @return the generated ID
     */
    private static synchronized int generateSentenceId(){

        lastSentenceId++;
        return lastSentenceId;
    }

    /**
     * Returns the word position of the syntactic head of the given word (not its ID!).
     * @param wordNo the word to look up the head for
     * @return the position of the syntactic head of the given word, or -1 for the root node
     */
    public int getHeadPos(int wordNo) {

        if (wordNo < 0 || wordNo >= this.length()){
            return -1;
        }

        int pos = Integer.parseInt(this.getWordInfo(wordNo, IDXI_HEAD));

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
    public int getSiblingPos(int wordNo, Direction whichOne){

        // find the mother node number
        String motherNo = this.getWordInfo(wordNo, IDXI_HEAD);

        // left sibling
        if (whichOne == Direction.LEFT){
            int ret = -1;

            for (int i = 0; i < wordNo; ++i){
                if (this.getWordInfo(i, IDXI_HEAD).equals(motherNo)){
                    ret = i;
                }
            }
            return ret;
        }
        // right sibling
        else {
            for (int i = wordNo + 1; i < this.length(); ++i){
                if (this.getWordInfo(i, IDXI_HEAD).equals(motherNo)){
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * This is for debugging purposes, it returns the full text of the currently loaded sentence.
     * @return the text of the currently loaded sentence
     */
    public String getSentenceText(){

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.length(); ++i){
            sb.append(this.getWordInfo(i, this.IDXI_FORM));
            if (i < this.length() -1){
                sb.append(" ");
            }
        }
        return sb.toString();
    }


    /**
     * This returns the current sentence in the original ST format.
     * @return the current sentence, in the original ST format (without empty line at the end)
     */
    public String getSentenceST(){

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.length(); ++i){
            sb.append(StringUtils.join(this.words.get(i), "\t"));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * This rewrites the contents of the given field (or adds a necessary number of columns) and fills
     * in the given value. Missing columns will be initialized with a '_'.
     *
     * @param field the number of the field to be filled in
     * @param word the number of the word
     * @param value the string value
     */
    public void setField(int field, int word, String value){

        int origColumns = this.words.get(0).length;

        if (field >= origColumns){
            for (int i = 0; i < this.words.size(); ++i){
                String [] tmp = new String [field + 1];
                System.arraycopy(this.words.get(i), 0, tmp, 0, origColumns);
                Arrays.fill(tmp, origColumns+1, field+1, DEFAULT_VALUE);
            }
        }
        this.words.get(word)[field] = value;
    }

    /**
     * This returns the predicate type for the given POS, according to the POS pattern specifications.
     * @param pos the POS candidate
     * @return the predicate type (n-oun, v-erb, or e-rror)
     */
    String getPredicateType(String pos) {

        String predType = TAG_ERR;
        if (pos.matches(this.nounPat)){
            predType = NOUN;
        }
        else if (pos.matches(this.verbPat)){
            predType = VERB;
        }
        return predType;
    }
}
