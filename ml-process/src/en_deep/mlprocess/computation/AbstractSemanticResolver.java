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

import en_deep.mlprocess.Task;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * This class unifies the functions of all tasks that set the semantic roles based on probability distributions
 * fed from the classifier.
 * @author Ondrej Dusek
 */
public abstract class AbstractSemanticResolver extends Task {

    /* CONSTANTS */

    /** The 'distr' parameter name */
    protected static final String DISTR = "distr";
    /** The 'sentence' parameter name */
    protected static final String SENTENCE = "sentence";
    /** The 'no_duplicate' parameter name */
    protected static final String NO_DUP = "no_duplicate";

    /* DATA */

    /** Prefix for the probability distribution attributes */
    protected final String distrPrefix;
    /** Name of the sentence-membership identification parameter */
    protected final String sentenceId;
    /** Probability distribution attributes from the input data */
    protected Vector<Attribute> distrAttribs;
    /** The newly created class attribute */
    protected Attribute classAttrib;
    /** The data to be processed */
    protected Instances data;

    /* METHODS */

    /**
     * This creates a new {@link AbstractSemanticResolver} task, checking the numbers of inputs and outputs
     * and the necessary parameters. It should be used only for derived classes.
     */
    protected AbstractSemanticResolver(String id, Hashtable<String, String> parameters,
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
    }



    /**
     * This finds all the attributes from the data that are a part of the probability distribution of semantic
     * roles.
     */
    private void getDistrAttrib() {

        this.distrAttribs = new Vector<Attribute>();

        Enumeration<Attribute> attribs = this.data.enumerateAttributes();
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
     */
    private void removeDistrAttrib() {

        int [] indexes = new int [this.distrAttribs.size()];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = this.distrAttribs.get(i).index();
        }

        Arrays.sort(indexes);
        for (int i = indexes.length - 1; i >= 0; i--) {
            this.data.deleteAttributeAt(indexes[i]);
        }
    }

    /**
     * Given that {@link #distrAttribs} are already set, this finds out the possible values of the class
     * attribute and adds it to the data with no values set.
     */
    private void createClassAttrib() {

        ArrayList<String> classVals = new ArrayList<String>();
        for (Attribute attr : this.distrAttribs){
            classVals.add(attr.name().substring(this.distrPrefix.length() + 1));
        }
        Attribute attr = new Attribute(this.distrPrefix, classVals);
        this.data.insertAttributeAt(attr, this.data.numAttributes());
        this.classAttrib = this.data.attribute(this.data.numAttributes()-1);
    }


    @Override
    public final void perform() throws TaskException {

        try {
            this.data = FileUtils.readArff(this.input.get(0));

            this.getDistrAttrib();
            this.createClassAttrib();

            this.resolve();

            this.removeDistrAttrib();

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
     * This is the actual semantic label resolution process -- to be implemented by the individual resolver classes.
     */
    protected abstract void resolve() throws TaskException;

}
