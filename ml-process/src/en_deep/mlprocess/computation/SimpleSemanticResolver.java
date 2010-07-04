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

import en_deep.mlprocess.exception.TaskException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instance;

/**
 * Given the probability distributions of the individual classifications of words in a sentence, this assigns
 * the most likely semantic roles.
 * 
 * @author Ondrej Dusek
 */
public class SimpleSemanticResolver extends AbstractSemanticResolver {

    /* CONSTANTS */

    /** The 'threshold' parameter name */
    private static final String THRESHOLD = "threshold";

    /* DATA */

    /** The threshold for no-duplicate parameters */
    private final double threshold;
    

    /* METHODS */

    /**
     * This creates a new {@link SimpleSemanticResolver} task, checking the numbers of inputs and outputs
     * (must be both 1) and the necessary parameters:
     * <ul>
     * <li><tt>sentence</tt> -- the name of the attribute whose identical values indicate the membership of the same 
     * sentence</li>
     * <li><tt>distr</tt> -- prefix for the attributes of the probability distribution</li>
     * <li><tt>no_duplicate</tt> (optional) -- list of no-duplicate semantic roles</li>
     * <li><tt>threshold</tt> (must be set if <tt>no_duplicate</tt> is set) -- minimum probability that the most
     * likely instance in a sentence must have so that this semantic role is set at all</li>
     * </ul>
     */
    public SimpleSemanticResolver(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

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

    /**
     * This is a simple implementation of the semantic resolution. The most likely candidate for each 
     * of the non-duplicate roles is selected in the whole sentence and the most probable roles are assigned
     * to the remaining candidates.
     */
    @Override
    protected void resolve() throws TaskException {

        if (this.getParameterVal(NO_DUP) != null) {
            String [] noDupRoles = this.getParameterVal(NO_DUP).split("\\s+");
            this.resolveNoDuplicate(noDupRoles);
        }
        this.assignMaxProb();
    }



    /**
     * This resolves the no-duplicate semantic roles: it selects the instance with the best probability for the given
     * role in the sentence and sets the probabilities of all others to 0 for this role.
     * @param noDupRoles list of no-duplicate roles
     */
    private void resolveNoDuplicate(String[] noDupRoles) throws TaskException {


        while (this.loadNextSentence()){

            // deal with all the no-duplicate roles
            for (int roleNo = 0; roleNo  < noDupRoles.length; roleNo ++) {

                if (this.data.classAttribute().indexOfValue(noDupRoles[roleNo]) == -1){
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "No-duplicate role not found: "
                            + noDupRoles[roleNo]);
                }
                Attribute attr = this.data.attribute(this.distrPrefix + "_" + noDupRoles[roleNo]);

                // select the most-likely instance and set its role
                int bestInst = -1;
                double bestProb = -1.0;
                for (int i = this.curSentBase; i < this.curSentBase + this.curSentLen; i++) {
                    double val = this.data.instance(i).value(attr);
                    if (val > this.threshold && val > bestProb){
                        bestInst = i;
                        bestProb = val;
                    }
                }
                if (bestInst != -1){
                    this.data.instance(bestInst).setClassValue(noDupRoles[roleNo]);
                }

                // set the probabilities of all others to zero
                for (int i = this.curSentBase; i < this.curSentBase + this.curSentLen; i++) {
                    this.data.instance(i).setValue(attr, 0.0);
                }
            }
        }
    }

    /**
     * Assign the most likely semantic role to each instance that hasn't got the class attribute assigned.
     */
    private void assignMaxProb() {

        Enumeration<Instance> insts = this.data.enumerateInstances();
        while (insts.hasMoreElements()){
            Instance inst = insts.nextElement();

            if (inst.classIsMissing()){
                
                String bestRole = null;
                double bestVal = -1;

                for (Attribute attr : this.distrAttribs){
                    if (inst.value(attr) > bestVal){
                        bestRole = attr.name().substring(this.distrPrefix.length() + 1);
                        bestVal = inst.value(attr);
                    }
                }
                inst.setClassValue(bestRole);
            }
        }
    }



}
