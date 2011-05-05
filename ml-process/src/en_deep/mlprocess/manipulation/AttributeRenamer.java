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
import weka.core.Instances;

/**
 * This {@link Task} allows renaming selected attributes.
 * @author Ondrej Dusek
 */
public class AttributeRenamer extends Task {

    /* CONSTANTS */
    
    /** The name of the 'attribs' parameter */
    private static final String ATTRIBS = "attribs";

    /** The name of the 'new_names' parameter */
    private static final String NEW_NAMES = "new_names";

    /** List of parameters to be renamed */
    private String[] toRename;
    /** List of new parameter names */
    private String[] newNames;

    /* METHODS */

    /**
     * This creates the new {@link AttributeRenamer} {@link Task}, checking for inputs
     * and outputs (non-empty, no patterns, same number) and the following parameters:
     * <ul>
     * <li><tt>attribs</tt> -- names of the attributes to be renamed (space-separated)</li>
     * <li><tt>new_names</tt> -- a list of corresponding new names (space-separated)</li>
     * </ul>
     */
    public AttributeRenamer(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException {
            
        super(id, parameters, input, output);

        this.requireParameter(ATTRIBS);
        this.requireParameter(NEW_NAMES);

        this.eliminatePatterns(this.input);
        this.eliminatePatterns(this.output);

        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have some inputs.");
        }
        if (this.output.size() != this.input.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Numbers of inputs and outputs must "
                    + "be the same.");
        }

        this.parseAttributeNames();
    }

    @Override
    public void perform() throws TaskException {
        try {
            for (int i = 0; i < this.input.size(); ++i){
                this.renameAttribs(this.input.get(i), this.output.get(i));
            }
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
     * This renames all the attributes given in the {@link #ATTRIBS} parameter to their new
     * names given in the {@link #NEW_NAMES} parameter and saves the result to the output file.
     *
     * @param inFile where should the attributes be added to
     * @param outFile the output file
     */
    private void renameAttribs(String inFile, String outFile) throws Exception {

        Instances data = FileUtils.readArff(inFile);

        for (int i = 0; i < this.toRename.length; ++i){
            if (data.attribute(this.toRename[i]) == null){
                Logger.getInstance().message("Attribute " + this.toRename[i] + " not found in data " + inFile, Logger.V_WARNING);
            }
            data.renameAttribute(data.attribute(this.toRename[i]), this.newNames[i]);
        }

        FileUtils.writeArff(outFile, data);
    }

    /**
     * This retrieves the lists of attribute old / new names from the corresponding parameter and
     * checks if their numbers match.
     * 
     * @throws TaskException if the numbers of old and new parameters do not match or there are no names given
     */
    private void parseAttributeNames() throws TaskException {

        this.toRename = this.getParameterVal(ATTRIBS).split("\\s+");
        this.newNames = this.getParameterVal(NEW_NAMES).split("\\s+");

        if (this.toRename.length == 0 || this.toRename.length != this.newNames.length){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "The lists of attribute names"
                    + " must be non-empty and of the same length.");
        }
    }

}
