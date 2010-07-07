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

import en_deep.mlprocess.Task;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This divides an attribute in two, filling the missing values with an empty one.
 * @author Ondrej Dusek
 */
public class AttributeDivider extends AbstractAttributeManipulation {
    
    /* CONSTANTS */

    /** The 'attr' parameter name */
    private static final String ATTR = "attr";
    /** The 'other_name' parameter name */
    private static final String OTHER_NAME = "other_name";
    /** The 'removed_vals' parameter name */
    private static final String REMOVED_VALS = "removed_vals";

    /* DATA */

    /** The name of the attribute that should be split in two. */
    private String attribName;
    /** The name of the newly created attribute */
    private String newName;
    /** Prefixes of values that should be moved to the new attribute */
    private String [] moveValues;

    /* METHODS */

    /**
     * This creates a new {@link AttributeDivider} task, checking the numbers of inputs and outputs
     * and the necessary parameters:
     * <ul>
     * <li><tt>attr</tt> -- the name of the attribute that should be split in two.</li>
     * <li><tt>other_name</tt> -- the name of the new attribute</li>
     * <li><tt>removed_vals</tt> -- space-separated prefixes of all values that should be moved
     * to the other attribute</li>
     * </ul>
     * The number of inputs must be the same as the number of outputs.
     */
    public AttributeDivider(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (!this.hasParameter(ATTR) || !this.hasParameter(OTHER_NAME) || !this.hasParameter(REMOVED_VALS)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
        this.attribName = this.getParameterVal(ATTR);
        this.newName = this.getParameterVal(OTHER_NAME);
        this.moveValues = this.getParameterVal(REMOVED_VALS).split("\\s+");
    }


    /**
     * Divide the attribute in the given data set.
     * @param data the data set to be processed
     */
    protected void manipulateAttributes(Instances data) throws TaskException{

        if (data.attribute(this.attribName) == null){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "The attribute "
                    + this.attribName + " has not been found in " + data.relationName());
        }
        if (data.attribute(this.newName) != null){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "The attribute "
                    + this.newName + " already exists in " + data.relationName());
        }

        // find out which values should be moved
        Attribute attr = data.attribute(this.attribName);
        HashSet<String> kept = new HashSet<String>(), moved = new HashSet<String>();
        String [] stringVals = new String [attr.numValues()];

        for (int i = 0; i < stringVals.length; ++i){

            stringVals[i] = attr.value(i);
            boolean isMoved = false;
            
            for (int j = 0; !isMoved && j < this.moveValues.length; ++j){
                if (stringVals[i].startsWith(this.moveValues[j])){
                    moved.add(stringVals[i]);
                    isMoved = true;
                }
            }
            if (!isMoved){
                kept.add(stringVals[i]);
            }
        }
        kept.add(EMPTY);
        moved.add(EMPTY);

        // create new attributes and delete the old one
        String [] keptArr = kept.toArray(new String [0]);
        String [] movedArr = moved.toArray(new String [0]);
        Arrays.sort(keptArr);
        Arrays.sort(movedArr);
        Attribute keptAttr = new Attribute(attr.name(), Arrays.asList(keptArr));
        Attribute movedAttr = new Attribute(this.newName, Arrays.asList(movedArr));

        int attrIndex = attr.index();
        double [] numericVals = data.attributeToDoubleArray(attrIndex);
        data.deleteAttributeAt(attrIndex);
        data.insertAttributeAt(keptAttr, attrIndex);
        data.insertAttributeAt(movedAttr, attrIndex+1);

        // move the values to the new attributes
        for (int i = 0; i < numericVals.length; ++i){
            Instance inst = data.get(i);

            if (moved.contains(stringVals[(int) numericVals[i]])){
                inst.setValue(attrIndex, EMPTY);
                inst.setValue(attrIndex+1, stringVals[(int) numericVals[i]]);
            }
            else {
                inst.setValue(attrIndex, stringVals[(int) numericVals[i]]);
                inst.setValue(attrIndex+1, EMPTY);
            }
        }
    }
}
