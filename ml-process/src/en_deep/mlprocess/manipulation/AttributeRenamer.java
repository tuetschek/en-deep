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
import en_deep.mlprocess.utils.StringUtils;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * This {@link Task} allows renaming selected attributes.
 * @author Ondrej Dusek
 */
public class AttributeRenamer extends Task {

    /* CONSTANTS */
    
    /** The name of the 'attribs' parameter */
    private static final String ATTRIBS = "attribs";
    /** The name of the 'numbers' parameter */
    private static final String NUMBERS = "numbers";

    /** The name of the 'new_names' parameter */
    private static final String NEW_NAMES = "new_names";

    /** Name list of parameters to be renamed */
    private String[] toRenameNames;
    /** Index list of parameters to be renamed */
    private int [] toRenameIdxs;
    /** List of new parameter names */
    private String[] newNames;

    /* METHODS */

    /**
     * This creates the new {@link AttributeRenamer} {@link Task}, checking for inputs
     * and outputs (non-empty, no patterns, same number) and the following parameters:
     * <ul>
     * <li><tt>attribs</tt> -- names of the attributes to be renamed (space-separated)</li>
     * <li><tt>numbers</tt> -- numbers of the attributes to be renamed (space-separated, 1-based)</li>
     * <li><tt>new_names</tt> -- a list of corresponding new names (space-separated)</li>
     * </ul>
     * <p>
     * Just one of the <tt>attribs</tt> and <tt>numbers</tt> parameters must be set.
     * </p>
     */
    public AttributeRenamer(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException {
            
        super(id, parameters, input, output);
        
        this.requireParameter(NEW_NAMES);
        if ((this.hasParameter(ATTRIBS) ^ this.hasParameter(NUMBERS)) == false){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Just one of " + ATTRIBS + ", "
                    + NUMBERS + " parameters must be set.");
        }

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

        if (this.toRenameNames != null){
            
            this.toRenameIdxs = new int [this.toRenameNames.length];
            
            for (int i = 0; i < this.toRenameNames.length; ++i){
                
                Attribute attr = data.attribute(this.toRenameNames[i]);
                
                if (attr == null){
                    this.toRenameIdxs[i] = -1;
                    Logger.getInstance().message("Attribute " + this.toRenameNames[i] + 
                            " not found in data file " + inFile, Logger.V_WARNING);
                }
                else {
                    this.toRenameIdxs[i] = attr.index();                    
                }
            }
        }
                
        for (int i = 0; i < this.newNames.length; ++i){
            
            
            
            if (this.toRenameIdxs[i] < 0 || this.toRenameIdxs[i] >= data.numAttributes()){
                Logger.getInstance().message("Did not perform the " + i + "th rename -- attribute index out of range"
                        + " in data file " + inFile, Logger.V_WARNING);
                continue;
            }
            data.renameAttribute(this.toRenameIdxs[i], this.newNames[i]);
        }

        FileUtils.writeArff(outFile, data);
    }

    /**
     * This retrieves the lists of attribute old / new names (or indexes) from the corresponding parameter and
     * checks if their numbers match.
     * 
     * @throws TaskException if the numbers of old and new parameters do not match or there are no names given
     */
    private void parseAttributeNames() throws TaskException {

        int listLen = 0;
        
        // parse old names
        if (this.hasParameter(ATTRIBS)){
            this.toRenameNames = this.getParameterVal(ATTRIBS).split("\\s+");
            listLen = this.toRenameNames.length;
        }
        // parse the indexes
        else {
            this.toRenameIdxs = StringUtils.readListOfInts(this.getParameterVal(NUMBERS));
            for (int i = 0; i < this.toRenameIdxs.length; ++i){ // 1-based to zero-based
                this.toRenameIdxs[i]--;
            }
            listLen = this.toRenameIdxs.length;
        }   
        this.newNames = this.getParameterVal(NEW_NAMES).split("\\s+");
        Logger.getInstance().message("LEN: " + listLen + " " + this.newNames.length, Logger.V_DEBUG);

        if (listLen == 0 || listLen != this.newNames.length){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "The lists of attribute names"
                    + " must be non-empty and of the same length.");
        }
    }

}
