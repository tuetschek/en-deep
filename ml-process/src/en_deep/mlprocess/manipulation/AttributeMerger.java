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

import en_deep.mlprocess.exception.TaskException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This merges two attributes into one, replacing the missing values. If both attributes have a
 * non-empty value, that of the first one is kept.
 * @author Ondrej Dusek
 */
public class AttributeMerger extends AbstractAttributeManipulation {
    
    /* CONSTANTS */

    /** The 'attr' parameter name */
    private static final String ATTR = "attr";
    /** The 'added' parameter name */
    private static final String ADDED = "added";

    /* DATA */

    /** The name of the first attribute to be merged */
    private String attribute;
    /** The name of the second attribute to be merged */
    private String addedAttrib;

    /* METHODS */

    /**
     * This creates a new {@link AttributeMerger} task, checking the numbers of inputs and outputs
     * and the necessary parameters:
     * <ul>
     * <li><tt>attr</tt> -- the first attribute to be merged</li>
     * <li><tt>added</tt> -- second attribute (to be added to the first one)</li>
     * </ul>
     * The number of inputs and outputs must be equal. It is necessary that some values are '_', otherwise
     * the second attribute will just be deleted.
     */
    public AttributeMerger(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (!this.hasParameter(ATTR) || !this.hasParameter(ADDED)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
        this.attribute = this.getParameterVal(ATTR);
        this.addedAttrib = this.getParameterVal(ADDED);
    }

    /**
     * This merges the pre-set two attributes in the given data set.
     * @param data the data set to be processed
     */
    @Override
    protected void manipulateAttributes(Instances data) throws TaskException {

        if (data.attribute(this.attribute) == null || data.attribute(this.addedAttrib) == null){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Needed attributes "
                      + " have not been found in " + data.relationName());
        }
        Attribute first = data.attribute(this.attribute);
        Attribute second = data.attribute(this.addedAttrib);
        HashSet<String> vals = new HashSet<String>();
        String [] valArr;
        String [] firstPossible = new String [first.numValues()];
        String [] secondPossible = new String [second.numValues()];

        for (int i = 0; i < first.numValues(); ++i){
            vals.add(firstPossible[i] = first.value(i));
        }
        for (int i = 0; i < second.numValues(); ++i){
            vals.add(secondPossible[i] = second.value(i));
        }
        Arrays.sort(valArr = vals.toArray(new String [0]));
        Attribute merged = new Attribute(this.attribute, Arrays.asList(valArr));

        double [] firstVals = data.attributeToDoubleArray(first.index());
        double [] secondVals = data.attributeToDoubleArray(second.index());
        int attrIdx = Math.min(first.index(), second.index());
        data.deleteAttributeAt(first.index());
        data.deleteAttributeAt(second.index()-1);
        data.insertAttributeAt(merged, attrIdx);

        for (int i = 0; i < firstVals.length; ++i){
            Instance inst = data.get(i);
            if (firstPossible[(int) firstVals[i]].equals(EMPTY)){
                inst.setValue(attrIdx, secondPossible[(int) secondVals[i]]);
            }
            else {
                inst.setValue(attrIdx, firstPossible[(int) firstVals[i]]);
            }
        }
    }

}
