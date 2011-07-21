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

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * This removes all the attributes that are irrelevant for the classification (attributes that have different
 * values for all instances and attributes that don't have more than one possible value).
 *
 * @author Ondrej Dusek
 */
public class IrrelevantAttributesRemoval extends MergedHeadersOutput {

    /* CONSTANTS */

    /** The name of the "preserve" parameter */
    private static final String PRESERVE = "preserve";
    /** The name of the "merge_input" parameter */
    private static final String MERGE_INPUTS = "merge_inputs";
    /** The name of the "remove" parameter */
    private static final String REMOVE = "remove";
    /** The name of the "ranking" parameter */
    private static final String RANKING = "ranking";


    /**
     * Possible conditions for removal (unary attribute,
     * attribute with no identical values, preselected for removal)
     */
    private enum Condition {
        PRESELECTED,
        UNARY,
        NON_IDENTICAL,
        LOW_RANKING;

        @Override
        public String toString() {
            switch (this){
                case PRESELECTED:
                    return "preselected";
                case UNARY:
                    return "unary";
                case NON_IDENTICAL:
                    return "non_identical";
                case LOW_RANKING:
                    return "low-ranked";
            }
            return "";
        }
    }

    /** Conditions for removal to be applied with ranking */
    private Condition [] RANKING_CONDS = { Condition.PRESELECTED, Condition.LOW_RANKING };
    /** Conditions for removal to be applied in normal mode, without ranking */
    private Condition [] USUAL_CONDS = { Condition.PRESELECTED, Condition.UNARY, Condition.NON_IDENTICAL };

    /* DATA */

    /** The list of attributes to be preserved */
    private HashSet<String> preserveAttribs;
    /** The list of attributes that need to be removed */
    private HashSet<String> removeAttribs;
    /** The list of attributes that meet the ranking requirement */
    private HashSet<String> goodRankings;

    /** Attribute rankings -- the number of attributes to be used */
    private int rankMaxAttribs;

    /* METHODS */

    /**
     * This just creates the new task and checks the inputs and outputs and parameters.
     * The class has no compulsory parameters. There are voluntary parameters:
     * <ul>
     * <li><tt>preserve</tt> -- space-separated list of attributes that need to be preserved at any rate.</li>
     * <li><tt>remove</tt> -- space-separated list of attributes that need to be removed at any rate</li>
     * <li><tt>merge_inputs</tt> -- the inputs are merged before consideration</li>
     * <li><tt>info_file</tt> -- if set, the number of inputs must be one/twice bigger (depends on <tt>merge_inputs</tt>)
     * and the last input(s) are considered to be saved information about the filtering process. This will
     * then ignore all other parameters (except <tt>merge_inputs</tt>) and perform the process exactly as
     * instructed in the file.</li>
     * <li><tt>output_info</tt> -- if set, the number of outputs must be one/twice bigger (depends on <tt>merge_inputs</tt>)
     * and the last outputs(s) are considered to be output files where the processing info about this filtering
     * is saved for later use.</tt>
     * <li><tt>ranking</tt> -- if set, the number of inputs must be one/twice bigger (depends on <tt>merge_inputs</tt>)
     * and the last input(s) are considered to contain a list of attribute rankings. The number specified in the parameter
     * value is the number of attributes that are to be preserved (additional to those listed in <tt>preserve</tt>).
     * All other causes for removal but {@link Condition#PRESELECTED} are ignored in this mode.
     * </ul>
     * <p>
     * <tt>info_file</tt> and <tt>ranking</tt> are mutually exclusive.
     * </p>
     * <p>
     * The number of inputs must be the same as the number of outputs (if not specified otherwise in the parameters).
     * </p>
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     * @throws TaskException
     */
    public IrrelevantAttributesRemoval(String id, Hashtable<String, String> parameters, Vector<String> input, 
            Vector<String> output) throws TaskException {
        
        super(id, parameters, input, output);

        if (this.hasParameter(INFO_FILE) && this.hasParameter(RANKING)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameters "
                    + INFO_FILE + " and " + RANKING + " are mutually exclusive.");
        }

        if (this.hasParameter(RANKING)){
            this.infoFilesIn = this.getInfoFiles(this.input);
            this.rankMaxAttribs = this.getIntParameterVal(RANKING);
            if (this.rankMaxAttribs == 0){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Number of kept ranked parameters"
                        + " must be greater than 0.");
            }
        }

        // check the number of inputs and outputs
        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have some inputs.");
        }
        if (this.output.size() != this.input.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "The number of outputs must "
                    + "be the same as that of inputs.");
        }
        this.eliminatePatterns(input);
        this.eliminatePatterns(output);

        // check the parameters
        this.preserveAttribs = this.saveToHashSet(this.parameters.get(PRESERVE));
        this.removeAttribs = this.saveToHashSet(this.parameters.get(REMOVE));
    }


    /**
     * This removes all the irrelevant attributes in all the given data sets.
     * @param data the data sets to be filtered
     * @param info the input information about removed attributes
     * @return list of removed attributes
     */
    @Override
    protected String processData(Instances [] data, String info) throws Exception {

        // check data compatibility
        if (data.length > 1) {
            for (int i = 1; i < data.length; ++i) {
                String message = data[i].equalHeadersMsg(data[0]);
                if (message != null) {
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id,
                            "Data from different files are not compatible: " + message);
                }
            }
        }

        // processing information: just remove the attributes that were given in the info
        if (this.rankMaxAttribs == 0 && info != null){
            this.removeAttributes(data, Arrays.asList(info.split("\\s+")), Condition.PRESELECTED);
            return info;
        }
        // ranking information: 
        else if (info != null){
            info = info.split("\\r?\\n", 2)[0];
            this.readRankings(data[0], StringUtils.readListOfInts(info));
        }

        // remove the attributes
        Condition [] conds = this.rankMaxAttribs == 0 ? USUAL_CONDS : RANKING_CONDS;

        Vector<String> removedAttributes = new Vector<String>();

        for (int i = 0; i < conds.length; ++i){
            Vector<String> selected = this.selectForRemoval(data, conds[i]);
            this.removeAttributes(data, selected, conds[i]);
            removedAttributes.addAll(selected);
        }
        return StringUtils.join(removedAttributes, " ");
    }


    /**
     * This removes attributes that fail to fulfill the given type of condition from all the data sets.
     * @param data the data to be filtered 
     * @param condition the {@link Condition} that the attributes must fulfill
     */
    private Vector<String> selectForRemoval(Instances[] data, Condition condition) {

        Enumeration<Attribute> attribs = data[0].enumerateAttributes();
        Vector<String> forRemoval = new Vector<String>();

        while (attribs.hasMoreElements()) {
            Attribute attr = attribs.nextElement();

            if (!this.preserveAttribs.contains(attr.name())) {
                switch (condition){
                    case NON_IDENTICAL:
                        if (!this.checkIdenticalValues(data, attr.name())){
                            forRemoval.add(attr.name());
                        }
                        break;

                    case UNARY:
                        if (!this.checkDifferentValues(data, attr.name())){
                            forRemoval.add(attr.name());
                        }
                        break;
                        
                    case PRESELECTED:
                        if (this.removeAttribs.contains(attr.name())){
                            forRemoval.add(attr.name());
                        }
                        break;

                    case LOW_RANKING:
                        if (!this.goodRankings.contains(attr.name())){
                            forRemoval.add(attr.name());
                        }
                        break;
                }
            }
        }
        return forRemoval;
    }

    /**
     * Removes the listed attributes.
     * @param data the data to be processed
     * @param forRemoval list of attribute names for removal
     * @condition reason of removal
     */
    private void removeAttributes(Instances [] data, List<String> forRemoval, Condition condition)
            throws Exception {

        if (forRemoval != null && !forRemoval.isEmpty() && 
                !(forRemoval.size() == 1 && (forRemoval.get(0) == null || forRemoval.get(0).equals("")))){
            Logger.getInstance().message(this.id + " : removing " + condition.toString() + " "
                    + StringUtils.join(forRemoval, ", ") + ".", Logger.V_DEBUG);
        }
        else {
            Logger.getInstance().message(this.id + " : no " + condition.toString() + " features found.",
                    Logger.V_DEBUG);
            return;
        }

        for (int i = 0; i < data.length; ++i) {

            BitSet bm = new BitSet(data[i].numAttributes());
            bm.set(0, data[i].numAttributes());

            for (String attrName : forRemoval) {
                if (data[i].attribute(attrName) == null){
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attribute " + attrName
                            + " not found in " + data[i].relationName());
                }
                bm.clear(data[i].attribute(attrName).index());
            }

            data[i] = FileUtils.filterAttributes(data[i], bm);
        }
    }

    /**
     * This checks that there are two or more identical values in attribute data (for all given data sets).
     * @param data the data sets to check
     * @param name the attribute name to check
     * @return true if there are two identical values of the given attribute in the data
     */
    private boolean checkIdenticalValues(Instances[] data, String name) {

        HashSet<Double> foundValues = new HashSet<Double>();

        for (int i = 0; i < data.length; ++i){

            double [] values = data[i].attributeToDoubleArray(data[i].attribute(name).index());

            for (int j = 0; j < values.length; ++j){
                if (foundValues.contains(values[j])){ // value already encountered
                    return true;
                }
                foundValues.add(values[j]);
            }
        }
        return false;
    }

    /**
     * This checks that there are at least two different values in the attribute data (for all given data sets)
     *
     * @param data the data sets to check
     * @param name the attribute name to check
     * @return true if there are two different values of the given attribute in the data
     */
    private boolean checkDifferentValues(Instances[] data, String name) {

        double firstValue = Double.NaN;

        for (int i = 0; i < data.length; ++i){

            double [] values = data[i].attributeToDoubleArray(data[i].attribute(name).index());

            if (i == 0){
                firstValue = values[0];
            }
            for (int j = 0; j < values.length; ++j){
                if (values[j] != firstValue){
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Save the space-separated values of an attribute to a hash-set.
     * @param values the value of an attribute, which should contain space-separated values (may be null)
     * @return the resulting hash-set with all values saved, empty hash-set in case values is null
     */
    private HashSet<String> saveToHashSet(String values) {
        
        HashSet<String> ret = new HashSet<String>();

        if (values != null){
            String [] valArr = values.split("\\s+");

            for (int i = 0; i < valArr.length; ++i){
                ret.add(valArr[i]);
            }
        }

        return ret;
    }

    /**
     * Read rankings data and save the {@link #rankMaxAttribs} best attributes to be preserved (excluding those that
     * are to be preserved or removed in any case).
     *
     * @param data one of the data files, just for the data format
     * @param rankings the n-best list of attributes
     */
    private void readRankings(Instances data, int[] rankings) {
        
        int i = 0;
        this.goodRankings = new HashSet<String>(this.rankMaxAttribs);

        while (i < rankings.length && this.goodRankings.size() < this.rankMaxAttribs){

            String attName = data.attribute(rankings[i]).name();
            
            if (!this.preserveAttribs.contains(attName) && !this.removeAttribs.contains(attName)){
                this.goodRankings.add(attName);
            }
            i++;
        }
    }

}
