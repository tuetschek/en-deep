/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.IOException;
import java.util.BitSet;
import weka.core.Instance;
import weka.core.Instances;

/**
 * A helper class for accessing tree data in an ARFF file.
 * @author Ondrej Dusek
 */
public class ArffReader extends DataReader {

    /* CONSTANTS */

    /** The feature handler class parameter name */
    private static final String FEAT_HANDLER = "feat_handler";
    /** The rename_std_headers parameter name */
    private static final String RENAME_STD_HEADERS = "rename_std_headers";

    /** The sentence ID task parameter name */
    private static final String SENT_ID = "sent_id";
    /** The default sentence ID ARFF attribute name */
    private static final String DEFAULT_SENT_ID = "sent-id";
    /** The word ID task parameter name */
    private static final String WORD_ID = "word_id";
    /** The default word ID ARFF attribute name */
    private static final String DEFAULT_WORD_ID = "word-id";

    /** The POS task parameter name */
    private static final String POS = "pos";
    /** The default POS ARFF attribute name */
    private static final String DEFAULT_POS = "pos";
    /** The lemma task parameter name */
    private static final String LEMMA = "lemma";
    /** The default lemma ARFF attribute name */
    private static final String DEFAULT_LEMMA = "lemma";
    /** The form task parameter name */
    private static final String FORM = "form";
    /** The default word form ARFF attribute name */
    private static final String DEFAULT_FORM = "form";
    /** The synt_rel task parameter name */
    private static final String SYNT_REL = "synt_rel";
    /** The default syntactic relation ARFF attribute name */
    private static final String DEFAULT_SYNT_REL = "deprel";
    /** The head task parameter name */
    private static final String HEAD = "head";
    /** The default syntactic head ARFF attribute name */
    private static final String DEFAULT_HEAD = "head";
    /** The pos_feat task parameter name */
    private static final String POS_FEAT = "pos_feat";
    /** The default POS features ARFF attribute name */
    private static final String DEFAULT_POS_FEAT = "pfeat";


    /* DATA */

    /** The loaded input data file */
    private Instances input;

    /** Beginning of the current sentence in the input file */
    private int curSentStart = -1;
    /** End of the current sentence in the input file */
    private int curSentEnd = -1;

    /** The sentence ID ARFF attribute name */
    private String sentIdName;
    /** The word ID ARFF attribute name */
    private String wordIdName;

    /** The POS ARFF attribute name */
    private String posName;
    /** The POS features ARFF attribute name */
    private String [] posFeatNames;
    /** The word form ARFF attribute name */
    private String formName;
    /** The lemma ARFF attribute name */
    private String lemmaName;
    /** The syntactic relation ARFF attribute name */
    private String syntRelName;
    /** The syntactic head ARFF attribute name */
    private String headName;

    /** The sentence ID attribute index in the current ARFF file */
    private int sentIdAttr;
    /** The word ID attribute index in the current ARFF file */
    private int wordIdAttr;

    /** The POS attribute index in the current ARFF file */
    private int posAttr;
    /** The POS features ARFF attribute name(s) */
    private int [] posFeatAttrs;
    /** The word form attribute index in the current ARFF file */
    private int formAttr;
    /** The lemma attribute index in the current ARFF file */
    private int lemmaAttr;
    /** The syntactic relation attribute index in the current ARFF file */
    private int syntRelAttr;
    /** The syntactic head attribute index in the current ARFF file */
    private int headAttr;

    /** Rename the standard headers (form, lemma etc.) to the default names ? */
    private boolean renameStdHeaders;


    /* METHODS */

    /**
     * This creates a new {@link ArffReader} object, setting all the needed parameters:
     * <ul>
     * <li><tt>sent_id</tt> -- name of the attribute with sentence IDs in the input ARFF file (such an
     * attribute must be present, if this is not set, the name defaults to <tt>sent-id</tt>).</li>
     * <li><tt>word_id</tt> -- name of the attribute with sentence IDs in the input ARFF file (such an
     * attribute must be present, if this is not set, the name defaults to <tt>word-id</tt>).</li>
     * <li><tt>pos</tt> -- name of the attribute with POS tags in the input ARFF file
     * (defaults to <tt>pos</tt>).</li>
     * <li><tt>lemma</tt> -- name of the attribute with lemmas in the input ARFF file
     * (defaults to <tt>lemma</tt>).</li>
     * <li><tt>form</tt> -- name of the attribute with word forms in the input ARFF file
     * (defaults to <tt>form</tt>).</li>
     * <li><tt>synt_rel</tt> -- name of the attribute with syntactic functions in the input ARFF file
     * (defaults to <tt>deprel</tt>).</li>
     * <li><tt>head</tt> -- name of the attribute with syntactic head IDs in the input ARFF file
     * (defaults to <tt>head</tt>).</li>
     * <li><tt>pos_feat</tt> -- (space-separated) name(s) of the attribute(s) with POS features in the input ARFF file
     * (defaults to <tt>pfeat</tt>), which will be handled by the POS feature handler class (if set).</li>
     * <li><tt>feat_handler</tt> -- Java class name for the POS feature handler.</li>
     * <li><tt>rename_std_headers</tt> -- if set to non-zero, the attributes containing standard information will be
     * renamed to the abovementioned default names.</li>
     * </ul>
     * @param task
     * @throws IOException
     */
    public ArffReader(Task task) throws IOException {
        
        super(task);

        this.posFeatHandlerName = this.task.getParameterVal(FEAT_HANDLER);
        this.renameStdHeaders = this.task.getBooleanParameterVal(RENAME_STD_HEADERS);

        this.sentIdName = this.task.hasParameter(SENT_ID) ? this.task.getParameterVal(SENT_ID) : DEFAULT_SENT_ID;
        this.wordIdName = this.task.hasParameter(WORD_ID) ? this.task.getParameterVal(WORD_ID) : DEFAULT_WORD_ID;

        this.posName = this.task.hasParameter(POS) ? this.task.getParameterVal(POS) : DEFAULT_POS;
        this.lemmaName = this.task.hasParameter(LEMMA) ? this.task.getParameterVal(LEMMA) : DEFAULT_LEMMA;
        this.formName = this.task.hasParameter(FORM) ? this.task.getParameterVal(FORM) : DEFAULT_FORM;
        this.syntRelName = this.task.hasParameter(SYNT_REL) ? this.task.getParameterVal(SYNT_REL) : DEFAULT_SYNT_REL;
        this.headName = this.task.hasParameter(HEAD) ? this.task.getParameterVal(HEAD) : DEFAULT_HEAD;

        if (this.task.hasParameter(POS_FEAT)){
            this.posFeatNames = this.task.getParameterVal(POS_FEAT).split("\\s+");
        }
        else {
            this.posFeatNames = new String [1];
            this.posFeatNames[0] = DEFAULT_POS_FEAT;
        }

        // initialize POS features handler, if applicable
        this.initPOSFeats();
    }

    
    @Override
    public String getWordInfo(int word, int attributeNumber) {

        if (attributeNumber < 0 || attributeNumber >= this.input.numAttributes()
                || word < 0 || word >= this.getSentenceLength()){
            return "";
        }
        if (this.input.attribute(attributeNumber).isNumeric()){
            return Double.toString(this.input.instance(this.curSentStart + word).value(attributeNumber))
                    .replaceFirst("\\.0$", "");
        }
        return this.input.instance(this.curSentStart + word).stringValue(attributeNumber);
    }

    
    @Override
    boolean loadNextSentence() throws IOException {
        
        this.curSentStart = this.curSentEnd + 1;

        if (this.curSentStart >= this.input.numInstances()){
            return false;
        }
        this.sentenceId = (int) this.input.instance(this.curSentStart).value(this.sentIdAttr);

        int word = this.curSentStart + 1;
        while (word < this.input.numInstances()
                && (int) this.input.instance(word).value(this.sentIdAttr) == this.sentenceId){
           word++;
        }
        this.curSentEnd = word - 1;
        return true;
    }

    @Override
    public int getSentenceLength() {
        if (this.curSentStart >= 0 && this.curSentStart < this.input.numInstances()){
            return this.curSentEnd - this.curSentStart + 1;
        }
        return -1;
    }

    @Override
    String getArffHeaders() {

        StringBuilder sb = new StringBuilder();
        BitSet used = new BitSet();
        
        // add all usual fields, if they are present
        sb.append(this.getAttributeHeader(this.sentIdAttr, DEFAULT_SENT_ID));
        used.set(this.sentIdAttr);
        sb.append(LF).append(this.getAttributeHeader(this.wordIdAttr, DEFAULT_WORD_ID));
        used.set(wordIdAttr);

        if (this.formAttr >= 0){
            sb.append(LF).append(this.getAttributeHeader(this.formAttr, DEFAULT_FORM));
            used.set(this.formAttr);
        }
        if (this.lemmaAttr >= 0){
            sb.append(LF).append(this.getAttributeHeader(this.lemmaAttr, DEFAULT_LEMMA));
            used.set(this.lemmaAttr);
        }
        if (this.posAttr >= 0){
            sb.append(LF).append(this.getAttributeHeader(this.posAttr, DEFAULT_POS));
            used.set(this.posAttr);
        }
        if (this.headAttr >= 0){
            sb.append(LF).append(this.getAttributeHeader(this.headAttr, DEFAULT_HEAD));
            used.set(this.headAttr);
        }
        if (this.syntRelAttr >= 0){
            sb.append(LF).append(this.getAttributeHeader(this.syntRelAttr, DEFAULT_SYNT_REL));
            used.set(this.syntRelAttr);
        }

        // handle all POS feats (if there is a POS features handler), skip and include with others otherwise
        for (int i = 0; i < this.posFeatAttrs.length; ++i){
            if (this.posFeatHandler != null && this.posFeatAttrs[i] >= 0){
                sb.append(LF).append(this.posFeatHandler.getHeader(this.posFeatNames[i] + "_"));
                used.set(this.posFeatAttrs[i]);
            }
        }

        // add other fields in the data (mask already used)
        for (int i = 0; i < this.input.numAttributes(); ++i){
            if (!used.get(i)){
                sb.append(LF).append(this.input.attribute(i).toString());
            }
        }
       
        return sb.toString();
    }

    /**
     * Return the output attribute name for the given attribute (given its position and a default
     * name, if it should be renamed according to the {@link #renameStdHeaders} attribute.
     *
     * @param attributeNo the attribute number
     * @param defaultName the default attribute name, in case it should be renamed to default
     * @return the attribute header
     */
    private String getAttributeHeader(int attributeNo, String defaultName){

        String attrHeader = this.input.attribute(attributeNo).toString();

        if (this.renameStdHeaders){
            attrHeader = attrHeader.replaceFirst("\\s+\\S+", " " + defaultName);
        }
        return attrHeader;
    }

    /**
     * Returns an empty string, since there is no designated target class in the ARFF files input.
     * @todo solve this better in the base class !
     * @return an empty string
     */
    @Override
    String getTargetClassHeader() {
        return "";
    }

    @Override
    public String getInputFields(int wordNo) {

        StringBuilder sb = new StringBuilder();
        Instance word = this.input.get(this.curSentStart + wordNo);
        BitSet used = new BitSet();

        // add all usual fields, if they are present
        sb.append(",").append(Integer.toString((int) word.value(this.wordIdAttr)));
        used.set(this.sentIdAttr); // skip this, since it's printed somewhere else
        used.set(this.wordIdAttr);

        if (this.formAttr >= 0){
            sb.append(",").append(StringUtils.protect(word.stringValue(this.formAttr)));
            used.set(this.formAttr);
        }
        if (this.lemmaAttr >= 0){
            sb.append(",").append(StringUtils.protect(word.stringValue(this.lemmaAttr)));
            used.set(this.lemmaAttr);
        }
        if (this.posAttr >= 0){
            sb.append(",").append(StringUtils.protect(word.stringValue(this.posAttr)));
            used.set(this.posAttr);
        }
        if (this.headAttr >= 0){
            sb.append(",").append(Integer.toString((int) word.value(this.headAttr)));
            used.set(this.headAttr);
        }
        if (this.syntRelAttr >= 0){
            sb.append(",").append(StringUtils.protect(word.stringValue(this.syntRelAttr)));
            used.set(this.syntRelAttr);
        }

        // handle all POS feats, if there is a POS features handler, skip and include with the rest otherwise
        for (int i = 0; i < this.posFeatAttrs.length; ++i){
            if (this.posFeatAttrs[i] >= 0 && this.posFeatHandler != null){
                sb.append(",").append(StringUtils.protect(
                        this.posFeatHandler.listFeats(word.stringValue(this.posFeatAttrs[i]))));
                used.set(this.posFeatAttrs[i]);
            }
        }
        
        // add other fields in the data (mask already used)
        for (int i = 0; i < this.input.numAttributes(); ++i){
            if (!used.get(i)){
                sb.append(",").append(StringUtils.protect(this.getWordInfo(wordNo, i)));
            }
        }
        
        return sb.toString();
    }

    @Override
    void setInputFile(String fileName) throws IOException {

        Logger.getInstance().message("Reading file :" + fileName, Logger.V_DEBUG);

        try {
            this.input = FileUtils.readArff(fileName);
        }
        catch (Exception ex) {
            throw new IOException(ex);
        }
        this.curSentStart = -1;
        this.curSentEnd = -1;

        // find out the indexes of the ID attributes
        this.sentIdAttr = this.input.attribute(this.sentIdName).index();
        this.wordIdAttr = this.input.attribute(this.wordIdName).index();

        if (this.sentIdAttr == -1 || this.wordIdAttr == -1){
            throw new IOException("Sentence ID and/or word ID attribute(s) not found!");
        }

        // find out the indexes of all other usual attributes
        this.formAttr = this.input.attribute(this.formName) != null ? this.input.attribute(this.formName).index() : -1;
        this.posAttr = this.input.attribute(this.posName) != null ? this.input.attribute(this.posName).index() : -1;
        this.lemmaAttr = this.input.attribute(this.lemmaName) != null ? this.input.attribute(this.lemmaName).index() : -1;
        this.syntRelAttr = this.input.attribute(this.syntRelName) != null ? this.input.attribute(this.syntRelName).index() : -1;
        this.headAttr = this.input.attribute(this.headName) != null ? this.input.attribute(this.headName).index() : -1;

        this.posFeatAttrs = new int [this.posFeatNames.length];
        for (int i = 0; i < this.posFeatNames.length; ++i){
            this.posFeatAttrs[i] = this.input.attribute(this.posFeatNames[i]) != null 
                    ? this.input.attribute(this.posFeatNames[i]).index() : -1;
        }

        // check the attribute types
        this.checkAttributeType(this.sentIdName, this.sentIdAttr, true);
        this.checkAttributeType(this.wordIdName, this.wordIdAttr, true);

        this.checkAttributeType(this.formName, this.formAttr, false);
        this.checkAttributeType(this.lemmaName, this.lemmaAttr, false);
        this.checkAttributeType(this.posName, this.posAttr, false);
        this.checkAttributeType(this.syntRelName, this.syntRelAttr, false);
        this.checkAttributeType(this.headName, this.headAttr, true);
        
        for (int i = 0; i < this.posFeatAttrs.length; ++i){
            this.checkAttributeType(this.posFeatNames[i], this.posFeatAttrs[i], false);
        }

    }

    /**
     * Returns the position of the given information in the ARFF file.
     * @param info the needed information
     * @return the number of the ARFF file attribute that contains the needed information
     */
    @Override
    protected int getInfoPos(WordInfo info){

        switch (info){
            case SYNT_REL:
                return this.syntRelAttr;
            case FORM:
                return this.formAttr;
            case LEMMA:
                return this.lemmaAttr;
            case POS:
                return this.posAttr;
            case HEAD:
                return this.headAttr;
            case PFEAT:
                return this.posFeatAttrs[0];
            default:
                return -1; // cause errors (PRED is not supported)
        }
    }

    private void checkAttributeType(String name, int pos, boolean numericity) throws IOException {

        if (pos < 0){
            return;
        }
        if (this.input.attribute(pos).isNumeric() != numericity){
            throw new IOException("The attribute " + name + " must " + (numericity ? "" : "not ") + "be numeric!");
        }
    }
}
