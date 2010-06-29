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

package en_deep.mlprocess.computation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Given the probability distributions of the individual classifications of words in a sentence, this assigns
 * the most likely semantic roles.
 * 
 * @author Ondrej Dusek
 */
public class SemanticResolver extends Task {

    /* CONSTANTS */

    /** The 'distr' parameter name */
    private static final String DISTR = "distr";
    /** The 'sentence' parameter name */
    private static final String SENTENCE = "sentence";
    /** The 'no_duplicate' parameter name */
    private static final String NO_DUP = "no_duplicate";
    /** The 'threshold' parameter name */
    private static final String THRESHOLD = "threshold";

    /* DATA */

    /** Prefix for the probability distribution attributes */
    private final String distrPrefix;
    /** Name of the sentence-membership identification parameter */
    private final String sentenceId;
    /** The threshold for no-duplicate parameters */
    private final double threshold;
    
    /** Probability distribution attributes from the input data */
    private Vector<Attribute> distrAttribs;
    /** The newly created class attribute */
    private Attribute classAttrib;

    /* METHODS */

    /**
     * This creates a new {@link SemanticResolver} task, checking the numbers of inputs and outputs
     * and the necessary parameters:
     * <ul>
     * <li><tt>sentence</tt> -- the name of the attribute whose identical values indicate the membership of the same 
     * sentence</li>
     * <li><tt>distr</tt> -- prefix for the attributes of the probability distribution</li>
     * <li><tt>no_duplicate</tt> (optional) -- list of no-duplicate semantic roles</li>
     * <li><tt>threshold</tt> (must be set if <tt>no_duplicate</tt> is set) -- minimum probability that the most
     * likely instance in a sentence must have so that this semantic role is set at all</li>
     * </ul>
     */
    public SemanticResolver(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        // check the inputs and outputs
        if (this.input.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have 1 input.");
        }
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Must have 1 output.");
        }

        // check the parameters
        if (this.getParameterVal(DISTR) == null || this.getParameterVal(SENTENCE) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
        this.distrPrefix = this.getParameterVal(DISTR);
        this.sentenceId = this.getParameterVal(SENTENCE);

        if (this.getParameterVal(NO_DUP) != null && this.getParameterVal(THRESHOLD) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "If no duplicate"
                    + " roles are set, threshold must be set.");
        }
        if (this.getParameterVal(THRESHOLD) != null){
            try {
                this.threshold = Double.parseDouble(this.getParameterVal(THRESHOLD));
            }
            catch (NumberFormatException e) {
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Threshold must be numeric.");
            }
        }
        else {
            this.threshold = 0.0;
        }
    }



    @Override
    public void perform() throws TaskException {
        
        try {
            Instances data = FileUtils.readArff(this.input.get(0));

            this.getDistrAttrib(data);
            this.createClassAttrib(data);
            
            if (this.getParameterVal(NO_DUP) != null) {
                String [] noDupRoles = this.getParameterVal(NO_DUP).split("\\s+");
                this.resolveNoDuplicate(data, noDupRoles);
            }
            this.assignMaxProb(data);
            this.removeDistrAttrib(data);
            
            FileUtils.writeArff(this.output.get(0), data);
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
     * This resolves the no-duplicate semantic roles: it selects the instance with the best probability for the given
     * role in the sentence and sets the probabilities of all others to 0 for this role.
     * @param data the data to be processed
     * @param noDupRoles list of no-duplicate roles
     */
    private void resolveNoDuplicate(Instances data, String[] noDupRoles) throws TaskException {

        int sentBase = 0;
        int sentLen = 0;
        Attribute sentIdAttr = data.attribute(this.sentenceId);

        if (sentIdAttr == null){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attribute "
                    + this.sentenceId + " missing.");
        }

        while (sentBase < data.numInstances()){

            // find out the length of the sentence
            double sentId = data.instance(sentBase).value(sentIdAttr);
            while (sentLen + sentBase < data.numInstances()
                    && data.instance(sentBase + sentLen).value(sentIdAttr) == sentId){
                sentLen++;
            }

            // deal with all the no-duplicate roles
            for (int roleNo = 0; roleNo  < noDupRoles.length; roleNo ++) {

                if (this.classAttrib.indexOfValue(noDupRoles[roleNo]) == -1){
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "No-duplicate role not found: "
                            + noDupRoles[roleNo]);
                }
                Attribute attr = data.attribute(this.distrPrefix + "_" + noDupRoles[roleNo]);

                // select the most-likely instance and set its role
                int bestInst = -1;
                double bestProb = -1.0;
                for (int i = sentBase; i < sentBase + sentLen; i++) {
                    double val = data.instance(i).value(attr);
                    if (val > this.threshold && val > bestProb){
                        bestInst = i;
                        bestProb = val;
                    }
                }
                if (bestInst != -1){
                    data.instance(bestInst).setValue(this.classAttrib, noDupRoles[roleNo]);
                }

                // set the probabilities of all others to zero
                for (int i = sentBase; i < sentBase + sentLen; i++) {
                    data.instance(i).setValue(attr, 0.0);
                }
            }

            sentBase += sentLen;
            sentLen = 0;
        }
    }

    /**
     * Assign the most likely semantic role to each instance that hasn't got the class attribute assigned.
     * @param data the data to be processed
     */
    private void assignMaxProb(Instances data) {

        Enumeration<Instance> insts = data.enumerateInstances();
        while (insts.hasMoreElements()){
            Instance inst = insts.nextElement();

            if (inst.isMissing(this.classAttrib)){
                
                String bestRole = null;
                double bestVal = -1;

                for (Attribute attr : this.distrAttribs){
                    if (inst.value(attr) > bestVal){
                        bestRole = attr.name().substring(this.distrPrefix.length() + 1);
                        bestVal = inst.value(attr);
                    }
                }
                inst.setValue(this.classAttrib, bestRole);
            }
        }
    }

    /**
     * Given that {@link #distrAttribs} are already set, this finds out the possible values of the class
     * attribute and adds it to the data with no values set.
     *
     * @param data the data to be processed
     */
    private void createClassAttrib(Instances data) {

        ArrayList<String> classVals = new ArrayList<String>();
        for (Attribute attr : this.distrAttribs){
            classVals.add(attr.name().substring(this.distrPrefix.length() + 1));
        }
        Attribute attr = new Attribute(this.distrPrefix, classVals);
        data.insertAttributeAt(attr, data.numAttributes());
        this.classAttrib = data.attribute(data.numAttributes()-1);
    }

    /**
     * This finds all the attributes from the data that are a part of the probability distribution of semantic
     * roles.
     * @param data the data to be examined
     */
    private void getDistrAttrib(Instances data) {

        this.distrAttribs = new Vector<Attribute>();

        Enumeration<Attribute> attribs = data.enumerateAttributes();
        while (attribs.hasMoreElements()){
            Attribute curAttr = attribs.nextElement();
            if (curAttr.name().startsWith(this.distrPrefix)){
                this.distrAttribs.add(curAttr);
            }
        }
    }

    /**
     * This removes all the attributes that are a part of the probability distribution of semantic roles,
     * but keeps the final class attribute.
     * @param data the data to be processed
     */
    private void removeDistrAttrib(Instances data) {

        int [] indexes = new int [this.distrAttribs.size()];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = this.distrAttribs.get(i).index();
        }

        Arrays.sort(indexes);
        for (int i = indexes.length - 1; i >= 0; i--) {
            data.deleteAttributeAt(indexes[i]);
        }
    }

}
