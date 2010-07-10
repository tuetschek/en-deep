/*
 *  Copyright (c) 2010 Ondrej Dusek
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

import en_deep.mlprocess.Process;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.io.StreamTokenizer;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This is a special attribute adder version for enormously large data sets. It collects data that are
 * scattered among smaller data sets and adds them into the large one. However, the large data set must contain
 * some information as to where to find them.
 * @author Ondrej Dusek
 */
public class CollectingAdder extends Task {
    
    /* CONSTANTS */

    /** The 'file_attr' parameter name */
    private static final String FILE_ATTR = "file_attr";
    /** The 'inst_id' parameter name */
    private static final String INST_ID = "inst_id";
    /** The 'attribs' parameter name */
    private static final String ATTRIBS = "attribs";
    /** The 'pattern' parameter name */
    private static final String PATTERN = "pattern";

    /* DATA */

    /** File attribute name */
    private String fileAttrName;
    /** The instance id attribute names */
    private String [] instIdNames;
    /** The added attribute names */
    private String [] attribNames;
    /** The small input data set filenames pattern */
    private String filePattern;
    /** The name of the big input data set */
    private final String mainFileName;
    /** The name of the added small data set */
    private final HashSet<String> smallFileNames;
    /** The indexes of the instance id attributes in the main input file */
    private int[] instIdIdxs;
    /** The index of the file attribute */
    private int fileAttrIdx;
    /** The output stream */
    private PrintStream out;
    /** Tokenizer for ARFF files */
    private StreamTokenizer arffTokenizer;
    /** Number of attributes in the main ARFF file */
    private int numAttribs;
    /** The last used small file name */
    private String smallName;
    /** The last used small file data */
    private Instances smallData;
    /** The last used instance in the small data */
    private int smallCurPos;
    /** Indexes of the newly added attributes in the output file */
    private int [] addedIdxs;

    /* METHODS */

    /**
     * This creates a new {@link CollectingAdder} task, checking the numbers of inputs and outputs
     * and the necessary parameters:
     * <ul>
     * <li><tt>file_attr</tt> -- the name of the attribute that contains information about the smaller data sets file names</li>
     * <li><tt>inst_id</tt> -- the attributes that identify the instances</li>
     * <li><tt>attribs</tt> -- the attributes that should be added into the large data set</li>
     * <li><tt>pattern</tt> -- pattern for creating file names of small data sets from the file attribute in
     * the large data sets</tt>
     * </ul>
     * There must be more than one input and just one output. All nominal attributes are considered string on
     * the output.
     */
    public CollectingAdder(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (!this.hasParameter(FILE_ATTR) || !this.hasParameter(INST_ID) || !this.hasParameter(ATTRIBS)
                || !this.hasParameter(PATTERN)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
        this.fileAttrName = this.getParameterVal(FILE_ATTR);
        this.instIdNames = this.getParameterVal(INST_ID).split("\\s+");
        this.attribNames = this.getParameterVal(ATTRIBS).split("\\s+");
        this.filePattern = this.getParameterVal(PATTERN);
        if (!this.filePattern.contains(File.separator)){
            this.filePattern = Process.getInstance().getWorkDir() + this.filePattern;
        }

        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Just one output needed.");
        }
        if (this.input.size() <= 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have more than 1 input.");
        }

        this.mainFileName = this.input.remove(0);
        this.smallFileNames = new HashSet<String>(this.input);
    }



    @Override
    public void perform() throws TaskException {
        
        try {
            this.out = new PrintStream(this.output.get(0));
            this.processHeader();
            
            this.init();
            this.collect();

            this.out.close();
        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e) {
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

    /**
     * This collects all the data in the small data sets and writes them into the great data set. The indexes of
     * the needed attributes must already be set.
     * @throws Exception
     */
    private void collect() throws Exception {

        Object [] inst;
        Object [] idFields = new Object [this.instIdIdxs.length];

        while ((inst = this.readInstance()) != null){

            for (int i = 0; i < idFields.length; ++i){
                idFields[i] = inst[this.instIdIdxs[i]];
            }

            this.loadSmallData((String) inst[this.fileAttrIdx]);
            this.findInstance(idFields);
            this.writeInstance(inst, this.smallData.get(this.smallCurPos));
        }
    }

    /**
     * This reads the original header and using a header from one smaller file, writes the output file header.
     * @throws Exception
     */
    private void processHeader() throws Exception {

        Instances mainHeader = FileUtils.readArffStructure(this.mainFileName);
        this.instIdIdxs = new int [this.instIdNames.length];
        this.numAttribs = mainHeader.numAttributes();
        
        try {
            for (int i = 0; i < this.instIdNames.length; ++i){
                this.instIdIdxs[i] = mainHeader.attribute(this.instIdNames[i]).index();
            }
            this.fileAttrIdx = mainHeader.attribute(this.fileAttrName).index();
        }
        catch (Exception e){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Some needed attributes not found"
                    + "in the main file.");
        }

        this.addedIdxs = new int [this.attribNames.length];
        
        try {
            Instances smallHeader = FileUtils.readArffStructure(this.smallFileNames.iterator().next());
            for (int i = 0; i < this.attribNames.length; ++i){

                Attribute newAttr = smallHeader.attribute(this.attribNames[i]);
                if (newAttr.type() == Attribute.NOMINAL){
                    newAttr = new Attribute(newAttr.name(), (List<String>) null);
                }

                if (mainHeader.attribute(this.attribNames[i]) != null){

                    int idx = mainHeader.attribute(this.attribNames[i]).index();

                    mainHeader.deleteAttributeAt(idx);
                    mainHeader.insertAttributeAt(newAttr, idx);
                    this.addedIdxs[i] = idx;
                    Logger.getInstance().message(this.id + "Attributre " + this.attribNames[i] + " will be overwritten"
                            + " in the main data set.", Logger.V_WARNING);
                }
                else {
                    this.addedIdxs[i] = mainHeader.numAttributes();
                    mainHeader.insertAttributeAt(newAttr, mainHeader.numAttributes());
                }
            }
        }
        catch (Exception e) {
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Some added attributes not found.");
        }

        this.out.print(mainHeader);
    }

    /**
     * This tries to read one instance from the main ARFF file and return it as an array of Strings or Doubles.
     * @return the next instance from the main ARFF file
     * @throws IOException
     */
    private Object [] readInstance() throws IOException, TaskException {

        int numTokens = 0;
        Object [] data = new Object [this.numAttribs];

        while (numTokens < data.length){

            this.arffTokenizer.nextToken();

            switch (this.arffTokenizer.ttype){

                case StreamTokenizer.TT_EOL:
                case StreamTokenizer.TT_EOF:
                    if (numTokens != 0 && numTokens != data.length){
                        throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Premature end of line "
                                + this.arffTokenizer.lineno() + ".");
                    }
                    break;

                default:
                    data[numTokens] = this.arffTokenizer.sval != null ? this.arffTokenizer.sval : "";
                    try {
                        if ((this.arffTokenizer.sval.charAt(0) >= '0' && this.arffTokenizer.sval.charAt(0) <= '9')
                                || this.arffTokenizer.sval.charAt(0) == '.'){
                            data[numTokens] = Double.valueOf(this.arffTokenizer.sval);
                        }
                    }
                    catch (Exception e){
                    }
                    numTokens++;
                    break;
            }
            if (this.arffTokenizer.ttype == StreamTokenizer.TT_EOF){
                break;
            }
        }
        return numTokens == data.length ? data : null;
    }

    /**
     * This opens the main input file and initializes the tokenizer for ARFF-style lines (code copied from WEKA ARFF reader).
     * @throws IOException
     */
    private void init() throws IOException {

        FileInputStream in = new FileInputStream(this.mainFileName);

        this.arffTokenizer = new StreamTokenizer(new InputStreamReader(in));
        this.arffTokenizer.resetSyntax();
        this.arffTokenizer.whitespaceChars(0, ' ');
        this.arffTokenizer.wordChars(' ' + 1, '\u00ff');
        this.arffTokenizer.whitespaceChars(',', ',');
        this.arffTokenizer.commentChar('%');
        this.arffTokenizer.quoteChar('"');
        this.arffTokenizer.quoteChar('\'');
        this.arffTokenizer.ordinaryChar('{');
        this.arffTokenizer.ordinaryChar('}');
        this.arffTokenizer.eolIsSignificant(true);
        while (!"@data".equalsIgnoreCase(this.arffTokenizer.sval)) {
            // skip headers
            this.arffTokenizer.nextToken();
        }
    }

    /**
     * This loads the contents of a small data file of the given name into the {@link #smallData} member. If it's
     * already there, the loading is skipped
     * @param smallDataName the name of the next data to be loaded
     */
    private void loadSmallData(String smallDataName) throws Exception {

        if (this.smallName != null && this.smallName.equals(smallDataName)){ // already loaded
            return;
        }
        String fileName = this.filePattern.replace("*", smallDataName);
        if (!this.smallFileNames.contains(fileName)){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "The main file references a small"
                    + "file that's not in the inputs: " + smallDataName + "(line "+ this.arffTokenizer.lineno() + ")");
        }
        this.smallData = FileUtils.readArff(fileName);
        this.smallCurPos = 0;
        this.smallName = smallDataName;
    }

    /**
     * This finds the instance in the currently loaded {@link #smallData} that matches the given ID fields. It also
     * moves {@link #smallCurPos} one instance beneath the current one.
     * @param idFields the values of all the instance-id attributes.
     * @return the matching instance
     */
    private Instance findInstance(Object[] idFields) throws TaskException {

        int curPos = this.smallCurPos;
        Instance inst = null;
        
        while (true){ // will surely end if we go through all the instances or find the right one

            inst = this.smallData.get(curPos);

            int i = 0; // compare
            try {
                while (i < this.instIdNames.length){
                    Attribute attr = this.smallData.attribute(this.instIdNames[i]);
                    if (((idFields[i] instanceof Double) && !attr.isNumeric())
                            || (!(idFields[i] instanceof Double) && attr.isNumeric())
                            || (attr.isNumeric() && !idFields[i].equals(inst.value(attr)))
                            || (!attr.isNumeric() && !idFields[i].equals(inst.stringValue(attr)))){
                        break;
                    }
                    i++;
                }
            }
            catch (Exception e){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Invalid data format -- cannot"
                        + " find " + StringUtils.join(idFields, " ", false) + " in " + this.smallName);
            }
            if (i == this.instIdNames.length){ // the comparison went ok
                break;
            }
            // not found -> move to next
            curPos++;
            if (curPos >= this.smallData.numInstances()){
                curPos = 0;
            }
            if (curPos == this.smallCurPos){ // already tested everything
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Cannot find the required"
                        + " instance " + StringUtils.join(idFields, " ", false) + " in " + this.smallName);
            }
        }
        this.smallCurPos = curPos + 1; // next time, start one field beneath the current position
        if (this.smallCurPos >= this.smallData.numInstances()){
            this.smallCurPos = 0;
        }
        return inst;
    }

    /**
     * This will write the next instance in the output format to the output stream.
     * @param orig the original instance
     * @param added the instance with the added data
     */
    private void writeInstance(Object[] orig, Instance added) {

        int overwritten = 0;
        
        for (int i = 0; i < this.addedIdxs.length; ++i){
            if (this.addedIdxs[i] < orig.length){

                Attribute attr = this.smallData.attribute(this.attribNames[i]);
                if (attr.isNumeric()){
                    orig [this.addedIdxs[i]] = new Double(added.value(attr));
                }
                else {
                    orig [this.addedIdxs[i]] = added.stringValue(attr);
                }
                overwritten++;
            }
        }
        this.out.print(StringUtils.join(orig, ",", true));
        for (int i = 0; i < this.addedIdxs.length; ++i){
            if (this.addedIdxs[i] <= orig.length){
                Attribute attr = this.smallData.attribute(this.attribNames[i]);
                if (attr.isNumeric()){
                    this.out.print("," + added.value(attr));
                }
                else {
                    this.out.print(",\"" + added.stringValue(attr) + "\"");
                }
            }
        }
        this.out.println();
    }
}
