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

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Process;
import en_deep.mlprocess.manipulation.posfeat.POSFeatures;
import en_deep.mlprocess.utils.MathUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
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
    /** Attribute definition start in ARFF files */
    public static final String ATTRIBUTE = "@ATTRIBUTE";
    /** Specification of an attribute as CLASS in ARFF files @todo move to StReader */
    public static final String CLASS = "";
    /** Specification of an attribute as INTEGER in ARFF files @todo move to StReader */
    public static final String INTEGER = "INTEGER";
    /** Specification of an attribute as STRING in ARFF files @todo move to StReader */
    public static final String STRING = "STRING";

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

    /** Index of the FEAT attribute in the output ARFF file */
    private static final int IDXO_FEAT = 7;

    /** Number of compulsory fields that are in each sentence in the ST file */
    final int COMPULSORY_FIELDS;

    /** The default value for ST file fields. */
    public static final String EMPTY_VALUE = "_";

    /** Output file suffix for noun predicates */
    private static final String NOUN = ".n";
    /** Output file suffix for verb predicates */
    private static final String VERB = ".v";
    /** Output file suffix for erroneously tagged predicates */
    private static final String TAG_ERR = ".e";

    /** Semantic relation (all/valency args) attribute name in ARFF files */
    private static final String SEM_REL = "semrel";
    /** Semantic relation (adveribials/references) name */
    private static final String SEM_REL_AMS = "semrel-ams";

    /** List of compulsory fields in the output ARFF file */
    private static final String [] HEADER = {
        "@ATTRIBUTE sent-id INTEGER",
        "@ATTRIBUTE word-id INTEGER",
        "@ATTRIBUTE form STRING",
        "@ATTRIBUTE lemma STRING",
        "@ATTRIBUTE p-lemma STRING",
        "@ATTRIBUTE pos STRING",
        "@ATTRIBUTE p-pos STRING",
        "", // dummy field for generated features -- they are handled by a special class
        "",
        "@ATTRIBUTE head INTEGER",
        "@ATTRIBUTE p-head INTEGER",
        "@ATTRIBUTE deprel STRING",
        "@ATTRIBUTE p-deprel STRING",
        "@ATTRIBUTE fillpred {Y,_}",
        "@ATTRIBUTE pred STRING"
    };

    private static final String LF = System.getProperty("line.separator");

    /* VARIABLES */

    /** Sentence ID generation -- last used value */
    private static int lastSentenceId = 0;

    /** 
     * Name of the {@link en_deep.mlprocess.manipulation.posfeat.POSFeatures} subclass that should handle the
     * POS features of this language, or null.
     */
    public String posFeatName;
    /**
     * The POS features handling class for this language, or null if not necessary.
     */
    public POSFeatures posFeatHandler;
    /** Possible semantic roles */
    public String[] semRoles;
    /** Tag pattern for verbs in the ST file */
    public String nounPat;
    /** Tag pattern for nouns in the ST file */
    public String verbPat;
    /** Semrel pattern for adverbials and references in the ST file */
    public String amsPat;
    /** The current input file */
    private Scanner inputFile;
    /** The name of the current input file */
    private String inputFileName;

    /** Use predicted POS and DEPREL values ? */
    public final boolean usePredicted;
    /** Divide AM-s and references ? */
    private boolean divideAMs;
    /**
     * Always -1, if usePredicted is true, 1 otherwise -- in order to cover the second
     * (predicted or non-predicted) member by IDXI_ .. + predictedNon.
     */
    public final int predictedNon;
    /** The {@link StManipulation} task this reader works for. */
    private final StManipulation task;
    
    /** The data for the current sentence */
    private Vector<String []> words;
    /** The id of the current sentence */
    private int sentenceId;

    /** List of additional columns (possibly) included with the ST data (before any APREDs) */
    private final String[] additionalColumns;

    /* METHODS */

    /**
     * This initializes an StReader -- reads the language configuration from a file,
     * i.e\. all SEMREL values, noun and verb POS tag pattern, handling of FEAT for the
     * current ST file language and a list of additional columns (for a detailed description,
     * see the {@link StToArff} constructor).
     *
     * @param task the StToArff task for this conversion
     */
    StReader(StManipulation task) throws IOException {

        // set main parameters
        this.task = task;
        this.usePredicted = this.task.getBooleanParameterVal(StManipulation.PREDICTED);
        this.divideAMs = this.task.getBooleanParameterVal(StToArff.DIVIDE_AMS);

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
        }
        else {
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
        Scanner config = new Scanner(new File(this.task.getParameterVal(StToArff.LANG_CONF)),
                Process.getInstance().getCharset());

        this.posFeatName = config.nextLine(); // name of the feature-handling class or empty line
        if (this.posFeatName.matches("\\s*")){ // no features handled
            this.posFeatName = null;
        }

        this.nounPat = config.nextLine();
        this.verbPat = config.nextLine();

        String semRolesStr = config.nextLine();
        this.amsPat = config.nextLine();
        String addColsSpecs = config.nextLine();

        config.close();
        config = null;
        if (semRolesStr == null || this.nounPat == null || this.verbPat == null || this.amsPat == null) {
            throw new IOException();
        }

        // initialize POS features handler, if applicable
        this.initPOSFeats();

        this.semRoles = semRolesStr.split("\\s+");

        // initialize additional ST file column names, if applicable
        if (addColsSpecs != null){
            this.additionalColumns = addColsSpecs.split("\\s+");
        }
        else {
            this.additionalColumns = new String[0];
        }
        this.COMPULSORY_FIELDS = 14 + this.additionalColumns.length;
    }

    /**
     * If there is a name of the POS handling class in the configuration file, this will try to initialize
     * it. If the class is not found in the {@link en_deep.mlprocess.manipulation.genfeat} package, the process
     * will fail.
     */
    private void initPOSFeats() throws IOException {

        if (this.posFeatName != null){
            this.posFeatHandler = POSFeatures.createHandler(this.posFeatName);

            if (this.posFeatHandler == null){
                throw new IOException("POS feature handling " + "class `" + this.posFeatName + "' creation failed.");
            }
        }
    }

    /**
     * Sets a new input file and opens it.
     *
     * @param inputFile the path to the new input ST file
     */
    void setInputFile(String fileName) throws IOException {

        this.inputFileName = fileName;
        this.inputFile = new Scanner(new File(fileName), Process.getInstance().getCharset());
    }

    /**
     * Return a comma-separated list of possible semantic roles
     * @return list of semantic roles
     */
    private String getSemRoles() {
        return this.listMembers(this.semRoles);
    }

    /**
     * Returns either a list of valency arguments, or a list of adverbials and references,
     * according to the {@link #amsPat} setting.
     *
     * @param adverbials if true, return adverbials and references
     * @return list of valency arguments semantic roles or a list of adverbial and reference roles
     */
    private String getSemRoles(boolean adverbials){

        String [] matchingRoles = StringUtils.getMatching(this.semRoles, this.amsPat, !adverbials);
        return this.listMembers(matchingRoles);
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
        word = this.inputFile.hasNextLine() ? this.inputFile.nextLine() : null;

        while (word != null && !word.matches("^\\s*$")){

            words.add(word.split("\\t"));
            word = this.inputFile.hasNextLine() ? this.inputFile.nextLine() : null;
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
    int[] getPredicates() {

        int [] ret = new int [this.length()]; // upper bound
        int pos = 0;

        for (int i = 0; i < this.length(); ++i){ // collect all predicate positions
            if (this.getWordInfo(i, IDXI_FILLPRED).equals("Y")){
                ret[pos] = i;
                pos++;
            }
        }
        return Arrays.copyOf(ret, pos);
    }

    /**
     * Returns the number of words in the current sentence.
     * @return the length of the current sentence
     */
    public int length() {
        return this.words.size();
    }

    /**
     * Returns the number of columns for each word in the current sentence
     * @return the number of columns for each word in the current sentence
     */
    public int width() {
        if (this.words.isEmpty()){
            return 0;
        }
        return this.words.get(0).length;
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

        if (wordNo < 0 || wordNo >= this.words.size() || fieldNo < 0 || fieldNo >= this.words.get(wordNo).length){
            return "";
        }
        if (this.posFeatHandler != null && fieldNo == this.IDXI_POS || fieldNo == this.IDXI_POS + this.predictedNon){
            int move = (fieldNo == this.IDXI_POS) ? 0 : this.predictedNon;
            return this.posFeatHandler.getFullPOS(this.words.get(wordNo)[this.IDXI_POS + move],
                    this.words.get(wordNo)[this.IDXI_FEAT + move]);
        }
        return this.words.get(wordNo)[fieldNo];
    }

    /**
     * This returns all the compulsory information about the given word (starting with a comma,
     * fields enclosed in quotes if necessary). It returns all the features values if applicable;
     * the missing values are returned as ARFF unquoted '?'.
     * @param wordNo the number of the word in the current sentence
     * @param fieldNo the number of the desired information field
     * @return the given information field for the given word, in quotes
     */
    public String getCompulsoryFields(int wordNo){

        int goldFeatPos = this.usePredicted ? this.IDXI_FEAT + this.predictedNon : this.IDXI_FEAT;
        int predFeatPos = this.usePredicted ? this.IDXI_FEAT : this.IDXI_FEAT + this.predictedNon;
        StringBuilder sb = new StringBuilder();
        String [] word = this.words.get(wordNo);

        for (int fieldNo = 0; fieldNo < this.COMPULSORY_FIELDS; ++fieldNo){

            if (fieldNo >= word.length){ // treat ST missing values as ARFF missing values (evaluation file)
                sb.append(",?");
            }
            else if(fieldNo == goldFeatPos && this.posFeatHandler != null){ // print POS features
                sb.append(",").append(this.posFeatHandler.listFeats(word[goldFeatPos], word[predFeatPos]));
            }
            else if (fieldNo == goldFeatPos || fieldNo == predFeatPos){ // no feature handler/already printed
                continue;
            }
            else if (fieldNo == this.IDXI_WORDID || fieldNo == this.IDXI_HEAD
                    || fieldNo == this.IDXI_HEAD + this.predictedNon){
                sb.append(",").append(this.getWordInfo(wordNo, fieldNo));
            }
            else {
                sb.append(",\"").append(StringUtils.escape(this.getWordInfo(wordNo, fieldNo))).append("\"");
            }
        }
        return sb.toString();
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
            ret[i] = this.getWordInfo(wordNos[i], fieldNo);
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
     * Returns the ID of the current sentence.
     * @return the sentence ID
     */
    public int getSentenceId(){
        return this.sentenceId;
    }

   /**
     * Generates a unique sentence ID.
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
     * This is for debugging purposes (exceptions etc.), it returns the full text of the currently
     * loaded sentence.
     * 
     * @return the text of the currently loaded sentence
     */
    public String getSentenceText(){

        if (this.words == null){
            return "[NULL-No sentence currently loaded]";
        }

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
                Arrays.fill(tmp, origColumns, field+1, EMPTY_VALUE);
                this.words.set(i, tmp);
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

    /**
     * This returns true if the argument candidate is in the syntactical neighborhood of the predicate.
     * I.e. this means that it's a dependent of one of the predicates ancestors (including the
     * predicate itself).
     * 
     * @param pred the position of the predicate
     * @param argCand the position of the argument candidate
     * @return true if the argument candidate is in the syntactical neighborhood of the predicate
     */
    boolean isInNeighborhood(int pred, int argCand) {

        int curNode = pred;
        while (curNode >= 0){
            int [] kids = this.getChildrenPos(curNode);
            if (curNode == argCand || MathUtils.find(kids, argCand) != -1){
                return true;
            }
            curNode = this.getHeadPos(curNode);
        }
        return false;
    }

    /**
     * This returns the semantic role for the given word as should be output in the ARFF file (heeds the
     * {@link #divideAMs} setting.
     * @param wordNo the number of the desired word
     * @return the semantic role information, as should be output in the ARFF file
     */
    String getSemRole(int wordNo, int predNo) {

        if (this.width() < this.IDXI_SEMROLE + 1){ // evaluation file -> missing value(s)
            return (this.divideAMs ? "?,?" : "?");
        }
        else if (this.divideAMs){
            if (this.getWordInfo(wordNo, this.IDXI_SEMROLE + predNo).matches(this.amsPat)){
                return EMPTY_VALUE + ",\"" + this.getWordInfo(wordNo, this.IDXI_SEMROLE + predNo) + "\"";
            }
            else {
                return "\"" + this.getWordInfo(wordNo, this.IDXI_SEMROLE + predNo) + "\"," + EMPTY_VALUE;
            }
        }
        else {
            return "\"" + this.getWordInfo(wordNo, this.IDXI_SEMROLE + predNo) + "\"";
        }
    }

    /**
     * Returns the headers for the compulsory ST file fields that are always written to the ARFF output,
     * including the (possible) extended ST file fields, which are always assumed to be strings.
     *
     * @return the ARFF header compulsory fields
     */
    String getArffHeaders(){

        StringBuilder sb = new StringBuilder();

        for (int fieldNo = 0; fieldNo < HEADER.length; ++fieldNo) {

            if (this.posFeatHandler != null && (fieldNo == IDXO_FEAT)) {
                // prints the header for POS features (both predicted and golden!)
                sb.append(this.posFeatHandler.getHeaders());
            }
            else if (fieldNo == IDXO_FEAT || fieldNo == IDXO_FEAT + 1) {
                // do not print FEAT headers if we're not using them (or we already printed both headers)
                continue;
            }
            else {
                sb.append(HEADER[fieldNo]);
            }
            if (fieldNo < HEADER.length - 1){
                sb.append(LF);
            }
        }
        // additional ST file fields, if applicable
        if (this.additionalColumns != null){
            for (int i = 0; i < this.additionalColumns.length; ++i){
                sb.append(LF).append(ATTRIBUTE + " ").append(this.additionalColumns[i]).append(" " + STRING);
            }
        }
        return sb.toString();
    }


    /**
     * Returns the headers for semantic roles, according to the {@link #divideAMs} settings.
     * @return the semantic class headers
     */
    String getSemRolesHeader(){

        StringBuilder sb = new StringBuilder();

        if (this.divideAMs) {
            sb.append(ATTRIBUTE).append(" " + SEM_REL + " ").append(CLASS).append(" {_,");
            sb.append(this.getSemRoles(false));
            sb.append("}").append(LF);
            sb.append(ATTRIBUTE + " " + SEM_REL_AMS + " " + CLASS + " {_,");
            sb.append(this.getSemRoles(true));
            sb.append("}");
        }
        else {
            sb.append(ATTRIBUTE + " " + SEM_REL + " " + CLASS + " {_,");
            sb.append(this.getSemRoles());
            sb.append("}");
        }
        return sb.toString();
    }

}
