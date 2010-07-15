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
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.Hashtable;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This class merges several data sets into one.
 * @author Ondrej Dusek
 */
public class DataMerger extends Task {

    /* CONSTANTS */
    
    /** Line feed character */
    private static final String LF = System.getProperty("line.separator");
    /** The 'merge_attr' parameter name */
    private static final String MERGE_ATTR = "merge_attr";
    /** The 'uniq' parameter name */
    private static final String UNIQ = "uniq";
    /** The name of the 'file_attr' parameter */
    static final String FILE_ATTR = "file_attr";
    /** The prefix of all 'pattern' parameters */
    private static final String PATTERN_PREFIX = "pattern";

    /* DATA */

    /** Indexes of attributes used in merging */
    private int [] mergeAttribsIdxs;
    /** Should we discard duplicate lines (in terms of merging attribute values)? */
    private boolean uniq;
    /** If the 'file_attr' parameter is set, this holds all the patterns to match the file names (and set only parts
     * of them as values of the file attribute).
     */
    private final String[] fileNamePatterns;
    /** The name of the 'file attribute' (if 'file_attr' is set) */
    private final String fileAttributeName;

    /* METHODS */

    /**
     * This creates a new {@link DataMerger} task. The number of output data sources must be divisible by the number
     * of input data sources. This has two voluntary parameters:
     * <ul>
     * <li><tt>merge_attr<tt>-- space-separated list of attributes whose values are used in merging (the
     * instances with lowest values of these attributes will go first).</li>
     * <li><tt>uniq</tt> -- (boolean, valid only if <tt>merge_attr</tt> is set) -- if set, the instances with the
     * same values of the merging attributes coming from different files are discarded, only the first one is left.
     * <li><tt>file_attr</tt> -- if set, an attribute will be created that contains an information about the
     * file from which the given data originated (the value is the name of the new attribute). All the tasks
     * should then match <tt>pattern0 ... patternN</tt> corresponding to the outputs and only the matches are
     * used as attribute values. If no <tt>pattern0..patternN</tt>
     * parameters are set, whole filenames are used as values of the attribute.</li>
     * </ul> 
     */
    public DataMerger(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException{

        super(id, parameters, input, output);

        if (this.input.isEmpty() || this.input.size() % this.output.size() != 0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (this.getBooleanParameterVal(UNIQ) && !this.hasParameter(MERGE_ATTR)){
            Logger.getInstance().message(this.id + ": uniq setting has no sense if merge_attr is not set.", 
                    Logger.V_DEBUG);
        }
        if (this.hasParameter(FILE_ATTR)){
            this.fileAttributeName = this.getParameterVal(FILE_ATTR);
            this.fileNamePatterns = StringUtils.getValuesField(parameters, PATTERN_PREFIX, this.output.size());
            if (this.fileNamePatterns == null){
                Logger.getInstance().message(this.id + ": file attribute set but no file name patterns!",
                        Logger.V_WARNING);
            }
        }
        else {
            this.fileAttributeName = null;
            this.fileNamePatterns = null;
        }
    }


    /**
     * Tries to merge the input sources to the output sources.
     * Checks if the number of inputs is divisible by the number of outputs, then tries to read all the
     * inputs and write the outputs.
     *
     * @throws TaskException for wrong number of inputs, or if an I/O error occurs
     */
    @Override
    public void perform() throws TaskException {

        int ratio = this.input.size() / this.output.size();

        for (int j = 0; j < this.output.size(); ++j){

            try {
                this.mergeData(this.input.subList(ratio * j, ratio * j + ratio), this.output.get(j),
                        this.fileNamePatterns != null ? this.fileNamePatterns[j] : null);
            }
            catch(TaskException e){
                throw e;
            }
            catch(Exception e){
                Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
                throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
            }
        }
    }

    /**
     * Tries to merge several input files into one output, using WEKA code.
     *
     * @param in the list of input files to be merged
     * @param out the output file to write to
     * @param fileAttrPattern pattern to match on file names to get the values of the file_attribute,
     *  if such setting is imposed.
     */
    private void mergeData(List<String> in, String out, String fileAttrPattern) throws Exception {

        Instances [] data = new Instances [in.size()];

        Logger.getInstance().message(this.id + ": Merging " + StringUtils.join(in, ", ") + " to " + out + " ...",
                Logger.V_INFO);

        // read all data
        for (int i = 0; i < in.size(); i++) {
            data[i] = FileUtils.readArff(in.get(i));
        }

        // merge headers
        Instances mergedHeaders = new Instances(data[0], 0);
        for (int i = 1; i < in.size(); i++){
            this.mergeHeaders(mergedHeaders, data[i]);
        }
        // add the file attribute, if needed
        if (fileAttrPattern != null){
            this.addFileAttribute(in, fileAttrPattern, mergedHeaders, data);
        }

        // write the merged headers to the output
        PrintStream os = new PrintStream(out);
        os.print(mergedHeaders.toString());

        this.findMergeAttribsIndexes(mergedHeaders);

        // write the data to the output
        int [] dataPos = new int [data.length];
        Vector<Integer> first;
        while (!(first = this.selectFirst(data, dataPos)).isEmpty()){

            os.println(data[first.get(0)].get(dataPos[first.get(0)]).toString());
            if (!this.uniq){
                dataPos[first.get(0)]++;
            }
            else {
                for (Integer oneOfFirst : first){ // skip lines with the same merging attrib values
                    dataPos[oneOfFirst]++;
                }
            }
        }

        os.close();
    }

    /**
     * This finds out the indexes of the merge attributes.
     * @param dataHeaders  the headers of the data, where the attributes are looked up
     */
    private void findMergeAttribsIndexes(Instances dataHeaders) throws TaskException {
        
        if (this.hasParameter(MERGE_ATTR)) {

            String [] mergeAttribsNames = this.getParameterVal(MERGE_ATTR).split("\\s+");
            this.mergeAttribsIdxs = new int[mergeAttribsNames.length];

            for (int i = 0; i < mergeAttribsNames.length; ++i) {
                try {
                    this.mergeAttribsIdxs[i] = dataHeaders.attribute(mergeAttribsNames[i]).index();
                }
                catch (NullPointerException e) {
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Merginng attribute "
                            + mergeAttribsNames[i] + " missing.");
                }
            }
            this.uniq = this.getBooleanParameterVal(UNIQ);
        }
    }

    /**
     * This selects the instance of all data files that goes first (has the lowest values of {@link #mergeAttribsIdxs}.
     * If there are no merge attributes, it always selects the first file.
     * @param data the data to be examined
     * @param dataPos the current positions in the data
     * @return the number of data set whose current instance should go first
     */
    private Vector<Integer> selectFirst(Instances[] data, int[] dataPos) {
        
        Vector<Integer> bestIdxs = new Vector<Integer>();
        double [] bestVals = null;

        if (this.mergeAttribsIdxs != null){
            bestVals = new double [this.mergeAttribsIdxs.length];
            Arrays.fill(bestVals, Double.POSITIVE_INFINITY);
        }

        for (int i = 0; i < data.length; ++i){
            if (dataPos[i] >= data[i].numInstances()){ // skip ended files
                continue;
            }
            if (this.mergeAttribsIdxs == null){ // if there are no merged attributes, always select the first one
                bestIdxs.add(i);
                return bestIdxs;
            }
            for (int j = 0; j < this.mergeAttribsIdxs.length; ++j){
                double val = data[i].get(dataPos[i]).value(this.mergeAttribsIdxs[j]);

                if (val < bestVals[j]){
                    bestIdxs.clear();
                    bestIdxs.add(i);
                    break;
                }
                else if (val > bestVals[j]){
                    break;
                }
                if (j == this.mergeAttribsIdxs.length-1){
                    bestIdxs.add(i);
                }
            }
            if (bestIdxs.size() == 1 && bestIdxs.get(0) == i){
                for (int j = 0; j < this.mergeAttribsIdxs.length; ++j){
                    bestVals[j] = data[i].get(dataPos[i]).value(this.mergeAttribsIdxs[j]);
                }
            }
        }
        return bestIdxs;
    }


    /**
     * This will merge the headers of two data sets, provided they have attributes with same names and types (not necessary
     * the same possible values.
     * @param a the first set of instances, which will then contain the result
     * @param b the second set of instances that remains unchanged by the operation
     */
    private void mergeHeaders(Instances a, Instances b) throws TaskException {

        if (a.numAttributes() != b.numAttributes()){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "The datasets "
                    + a.relationName() + " and " + b.relationName() + " don't have the same number of attributes.");
        }
        Enumeration<Attribute> attribs = b.enumerateAttributes();
        while (attribs.hasMoreElements()){
            Attribute bAttr = attribs.nextElement();
            Attribute aAttr = a.attribute(bAttr.name());

            if (aAttr == null){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attribute "
                        + bAttr.name() + " missing from dataset " + a.relationName() + ".");
            }
            if (aAttr.type() != bAttr.type()){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attributes "
                        + aAttr.name() + " are of different types in " + a.relationName() + " and "
                        + b.relationName() + ".");
            }
            if (aAttr.index() != bAttr.index()){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attributes "
                        + "don't have the same order in " + a.relationName() + " and " + b.relationName());
            }

            if (aAttr.isNominal()){ // merge values for nominal attributes
                Attribute merged = this.mergeAttribute(aAttr, bAttr);
                int pos = aAttr.index();
                a.deleteAttributeAt(pos);
                a.insertAttributeAt(merged, pos);
            }
        }
    }

    /**
     * This will create a merged set of values for a nominal attribute.
     * @param a the first version of the attribute
     * @param b the second version of the attribute
     * @return the merged result
     */
    private Attribute mergeAttribute(Attribute a, Attribute b) {

        HashSet<String> values = new HashSet<String>();

        Enumeration<String> valA = a.enumerateValues();
        while (valA.hasMoreElements()){
            values.add(valA.nextElement());
        }
        Enumeration<String> valB = b.enumerateValues();
        while (valB.hasMoreElements()){
            values.add(valB.nextElement());
        }

        String [] arr = values.toArray(new String [0]);
        Arrays.sort(arr);
        return new Attribute(a.name(), Arrays.asList(arr));
    }

    private void addFileAttribute(List<String> fileNames, String fileAttrPattern, Instances mergedHeaders,
            Instances[] data) {

        ArrayList<String> values = new ArrayList<String>();

        for (String fileName : fileNames){
            
            fileName = StringUtils.truncateFileName(fileName);
            String match = StringUtils.matches(fileName, fileAttrPattern);

            if (match != null){
                values.add(match);
            }
            else {
                values.add(fileName);
                Logger.getInstance().message(this.id + ": the file name " + fileName
                        + " doesn't match the pattern " + fileAttrPattern + ".", Logger.V_WARNING);
            }
        }

        Attribute fileAttr = new Attribute(this.fileAttributeName, values);
        mergedHeaders.insertAttributeAt(fileAttr, 0);

        for (int i = 0; i < data.length; ++i){
            data[i].insertAttributeAt(fileAttr, 0);
            Enumeration<Instance> insts = data[i].enumerateInstances();
            while (insts.hasMoreElements()){
                insts.nextElement().setValue(0, (double) i);
            }
        }
    }

}
