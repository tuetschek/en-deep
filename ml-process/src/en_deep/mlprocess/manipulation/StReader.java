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
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.featmodif.FeatureModifier;
import en_deep.mlprocess.utils.MathUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Vector;

/**
 * This stores all the configuration needed for the conversion an the generated
 * features as well.
 */
public class StReader extends DataReader {

    /* CONSTANTS */

    /** The lang_conf parameter name */
    static final String LANG_CONF = "lang_conf";

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
    /** Index of the SYNT_REL attribute in the ST file */
    public final int IDXI_DEPREL;
    /** Index of the SYNT_REL attribute in the ST file */
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
        "", // dummy field for POS features -- they are handled by a special class
        "",
        "@ATTRIBUTE head INTEGER",
        "@ATTRIBUTE p-head INTEGER",
        "@ATTRIBUTE deprel STRING",
        "@ATTRIBUTE p-deprel STRING",
        "@ATTRIBUTE fillpred {Y,_}",
        "@ATTRIBUTE pred STRING"
    };

    /* VARIABLES */

    /**
     * Name of the {@link en_deep.mlprocess.manipulation.posfeat.FeatureModifier} subclass that should handle the
     * POS features of this language, or null.
     */
    public String posFeatHandlerName;

    /** Sentence ID generation -- last used value */
    private static int lastSentenceId = 0;
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

    /** Use predicted POS and SYNT_REL values ? */
    public final boolean usePredicted;
    /** Divide AM-s and references ? */
    private boolean divideAMs;
    /**
     * Always -1, if usePredicted is true, 1 otherwise -- in order to cover the second
     * (predicted or non-predicted) member by IDXI_ .. + predictedNon.
     */
    public final int predictedNon;
    
    /** The data for the current sentence */
    private Vector<String []> words;

    /** List of additional columns (possibly) included with the ST data (before any APREDs) */
    private final String[] additionalColumns;

    /** The POS features handling class for this language, or null if not necessary. */
    private FeatureModifier posFeatHandler;

    /* METHODS */

    /**
     * This initializes an StReader -- reads the language configuration from a file,
     * i.e\. all SEMREL values, noun and verb POS tag pattern, handling of FEAT for the
     * current ST file language and a list of additional columns (for a detailed description,
     * see the {@link StToArff} constructor).
     * <p>
     * Task parameters required by this class:
     * </p>
     * <li><tt>lang_conf</tt> -- path to the language reader file, that contains:
     * <ul>
     *   <li>a FEAT usage indication (name of the handling class derived from 
     *       {@link en_deep.mlprocess.manipulation.featmodif.FeatureModifer}, or empty line)</li>
     *   <li>noun and verb tag regexp patterns (each on separate line)</li>
     *   <li>list of all possible semantic roles (one line, space-separated)</li>
     *   <li>a regexp that catches all adverbial modifier semantic roles</li>
     *   <li>a space-separated list of additional columns in the ST file, if any</li>
     * </ul></li>
     *
     * @param task the StToArff task for this conversion
     */
    StReader(Task task) throws IOException {

        super(task);

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

        // check the lang_conf parameter
        if (!this.task.hasParameter(LANG_CONF)){
            throw new IOException(new TaskException(TaskException.ERR_INVALID_PARAMS,
                    this.getTaskId(), "Parameter lang_conf is missing."));
        }

        // read the configuration file
        String configFilePath = StringUtils.getPath(this.task.getParameterVal(LANG_CONF));
        Scanner config = new Scanner(new File(configFilePath), Process.getInstance().getCharset());

        this.posFeatHandlerName = config.nextLine(); // name of the feature-handling class or empty line
        if (this.posFeatHandlerName.matches("\\s*")){ // no features handled
            this.posFeatHandlerName = null;
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
     * Sets a new input file and opens it.
     *
     * @param inputFile the path to the new input ST file
     */
    @Override
    void setInputFile(String fileName) throws IOException {

        this.inputFileName = fileName;
        Logger.getInstance().message("Reading file :" + fileName, Logger.V_DEBUG);
        this.inputFile = new Scanner(new File(fileName), Process.getInstance().getCharset());
    }

    /**
     * Return a comma-separated list of possible semantic roles
     * @return list of semantic roles
     */
    private String getSemRoles() {
        return StringUtils.join(this.semRoles, ",", true);
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
        return StringUtils.join(matchingRoles, ",", true);
    }

  

    @Override
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

        int [] ret = new int [this.getSentenceLength()]; // upper bound
        int pos = 0;

        for (int i = 0; i < this.getSentenceLength(); ++i){ // collect all predicate positions
            if (this.getWordInfo(i, IDXI_FILLPRED).equals("Y")){
                ret[pos] = i;
                pos++;
            }
        }
        return Arrays.copyOf(ret, pos);
    }

    /**
     * Returns the number of words in the current sentence, -1 if no sentence loaded.
     * @return the length of the current sentence
     */
    @Override
    public int getSentenceLength() {
        if (this.words == null){
            return -1;
        }
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
    @Override
    public String getWordInfo(int wordNo, int fieldNo){

        if (wordNo < 0 || wordNo >= this.words.size() || fieldNo < 0 || fieldNo >= this.words.get(wordNo).length){
            return "";
        }
        return this.words.get(wordNo)[fieldNo];
    }


    @Override
    public String getInputFields(int wordNo){

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
     * Generates a unique sentence ID.
     * @return the generated ID
     */
    private static synchronized int generateSentenceId(){

        lastSentenceId++;
        return lastSentenceId;
    }


    /**
     * This returns the current sentence in the original ST format.
     * @return the current sentence, in the original ST format (without empty line at the end)
     */
    public String getSentenceST(){

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.getSentenceLength(); ++i){
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
            int [] kids = this.getChildren(curNode);
            if (curNode == argCand || MathUtils.find(kids, argCand) != -1){
                return true;
            }
            curNode = this.getHead(curNode);
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

    @Override
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
    @Override
    String getTargetClassHeader(){

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


    /**
     * Returns the position of the given information in the ST input file.
     * @param info the needed information
     * @return the position of the needed information in the ST file
     */
    @Override
    protected int getInfoPos(WordInfo info){

        switch (info){
            case SYNT_REL:
                return IDXI_DEPREL;
            case FORM:
                return IDXI_FORM;
            case LEMMA:
                return IDXI_LEMMA;
            case POS:
                return IDXI_POS;
            case PRED:
                return IDXI_PRED;
            case HEAD:
                return IDXI_HEAD;
            case PFEAT:
                return IDXI_FEAT;
            default:
                return -1; // cause errors
        }
    }

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

    @Override
    public String getAttributeName(int attributeNumber) {

        if (attributeNumber < HEADER.length){

            String header = HEADER[attributeNumber].replaceFirst("[^ ]+ ([^ ]+) .*", "\\1");

            if (header.equals("")){
                return "A" + attributeNumber;
            }
            return header;
        }
        return this.additionalColumns[attributeNumber - HEADER.length];
    }

}
