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
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This will just merge the files that originate from different groups and give them compatible names
 * (and issue errors if the names collide).
 * @author Ondrej Dusek
 */
public class FileGroupsMerger extends GroupInputsTask {
    
    /* CONSTANTS */

    /* DATA */

    /* METHODS */

    /**
     * This creates a new {@link FileGroupsMerger} task. It just checks the numbers of inputs and
     * outputs (must have one input and multiple outputs. The inputs must be captured by <tt>patternK</tt>
     * parameters, so that the expansions are visible.
     */
    public FileGroupsMerger(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (this.output.size() != 1 || !this.output.get(0).contains("**")){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "There must be 1 "
                    + "output pattern.");
        }
        this.extractPatterns(0);
    }



    @Override
    public void perform() throws TaskException {
        
        try {
            Hashtable<String, String> [] files = this.sortInputs();
            this.checkCollisions(files);
            this.copyToOutput(files, this.output.get(0));
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
     * This checks if some files that match different input patterns do not collide in their expansions.
     * @param files the inputs, sorted according to patterns
     * @throws TaskException
     */
    private void checkCollisions(Hashtable<String, String>[] files) throws TaskException {

        for (int i = 0; i < files.length; ++i){
            for (String key : files[i].keySet()){
                for (int j = i+1; j < files.length; ++j){
                    if (files[j].containsKey(key)){
                        throw new TaskException(TaskException.ERR_INVALID_DATA, this.id,
                                "Files " + files[i].get(key) + " and " + files[j].get(key) +
                                " collide in their expansion.");
                    }
                }
            }
        }
    }

    /**
     * This copies all the input files to their output destinations, using the pattern expansions.
     * @param files the inputs, sorted according to input patterns
     */
    private void copyToOutput(Hashtable<String, String>[] files, String outputPattern) throws IOException {

        for (int i = 0; i < files.length; ++i){
            for (String key : files[i].keySet()){
                FileUtils.copyFile(files[i].get(key), outputPattern.replace("**", key));
            }
        }
    }

}
