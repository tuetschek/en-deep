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
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * This allows re-adding an attribute to an instances set that was previously deleted because of attribute selection.
 * @author Ondrej Dusek
 */
public class AttributeAdder extends Task {

    /* CONSTANTS */
    
    /** The name of the 'attribs' parameter */
    private static final String ATTRIBS = "attribs";

    /* METHODS */

    /**
     * This creates the new {@link AttributeAdder} {@link Task}. The class just checks for the number of inputs
     * (must be two, the first of which is the file with deleted attributes and the second is the file with the
     * attributes to be added) and outputs (must be one) and the following parameter:
     * <ul>
     * <li><tt>attribs</tt> -- names of the attributes to be added (space-separated)</li>
     * </ul>
     * If there is an attribute of the same name in the original file, it is replaced by the new one.
     */
    public AttributeAdder(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException {
            
        super(id, parameters, input, output);

        if (this.getParameterVal(ATTRIBS) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameter 'attribs'");
        }
        if (this.input.size() != 2){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have 2 inputs.");
        }
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Must have 1 output.");
        }
    }

    @Override
    public void perform() throws TaskException {
        try {
            this.addAttribs(this.input.get(0), this.input.get(1), this.output.get(0));
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
     * This adds all the attributes given in the {@link #ATTRIBS} parameter from the second input file
     * to the first input file and saves the result to the output file.
     * @param whereFile where should the attributes be added to
     * @param whatFile where should the values be taken from
     * @param outFile the output file
     */
    private void addAttribs(String whereFile, String whatFile, String outFile) throws Exception {

        Instances base = FileUtils.readArff(whereFile);
        Instances add = FileUtils.readArff(whatFile);
        String [] attribs = this.getParameterVal(ATTRIBS).split("\\s+");

        if (base.numInstances() != add.numInstances()){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Number of instances"
                    + " differ in " + whereFile + " and " + whatFile + ".");
        }

        for (int attrNo = 0; attrNo < attribs.length; attrNo ++) {
            
            Attribute orig = add.attribute(attribs[attrNo]);
            if (orig == null){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Attribute " + attribs[attrNo]
                        + " not found in " + whatFile  + ".");
            }
            int idx = base.numAttributes();
            if (base.attribute(attribs[attrNo]) != null){
                idx = base.attribute(attribs[attrNo]).index();
                base.deleteAttributeAt(idx);
                Logger.getInstance().message(this.id + ": Attribute overwritten -- " + attribs[attrNo], 
                        Logger.V_WARNING);
            }
            base.insertAttributeAt(orig, idx);
            
            for (int i = 0; i < base.numInstances(); i++) {
                base.instance(i).setValue(idx, add.instance(i).value(orig));
            }
        }

        FileUtils.writeArff(outFile, base);
    }

}
