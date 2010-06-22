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

package en_deep.mlprocess.evaluation;

import com.google.common.collect.HashMultimap;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Instances;

/**
 * This creates a summary about the number of some attribute's values in the various files.
 * @author Ondrej Dusek
 */
public class AttributeStats extends Task {

    /* CONST */
    
    /** The name of the "attrib_name" parameter */
    private static final String ATTRIB_NAME = "attrib_name";
    
    /* DATA */
    
    /** The name of the attribute to compute statistics about */
    private String attribName;

    /** The collected statistics */
    private HashMultimap<Integer, String> values;
    
    /* METHODS */


    /**
     * This creats a new {@link AttributeStats} task, just checking the inputs and outputs and the compulsory
     * parameter. There is one parameter:
     * <ul>
     * <li><tt>attrib_name</tt> -- name of the attribute to make stats about</li>
     * </ul>
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     * @throws TaskException
     */
    public AttributeStats(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException {
        
        super(id, parameters, input, output);
        
        if (this.getParameterVal(ATTRIB_NAME) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Attrib_name parameter missing.");
        }
        this.attribName = this.getParameterVal(ATTRIB_NAME);

        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Only one output allowed.");
        }
        this.eliminatePatterns(output);
        this.values = HashMultimap.create();
    }


    @Override
    public void perform() throws TaskException {

        try {
            for (String file : this.input){
                this.collectStats(file);
            }
            this.printStats(this.output.get(0));
        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e){
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
        
    }

    /**
     * Retrieve the statistics from one file.
     * @param file the name of the file to open and read
     */
    private void collectStats(String file) throws Exception {

        Instances structure = FileUtils.readArffStructureAndClose(file);

        if (structure.attribute(this.attribName) == null){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "File " + file
                    + "doesn't have the given attribute.");
        }
        file = StringUtils.truncateFileName(file);
        this.values.put(structure.attribute(this.attribName).numValues(), file);
    }

    /**
     * Print all the statistics into the output file.
     */
    private void printStats(String file) throws IOException {

        PrintStream ps = new PrintStream(file);
        Integer [] keys = this.values.keySet().toArray(new Integer[0]);

        Arrays.sort(keys);

        for (int i = 0; i < keys.length; ++i){

            Collection<String> files = this.values.get(keys[i]);

            ps.print(keys[i] + ": " + files.size() + " -- ");
            for (String item : files){
                ps.print(item + " ");
            }
            ps.println();
        }
        ps.close();
    }

}
