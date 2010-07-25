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
import en_deep.mlprocess.utils.StringUtils;
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

    /** THe name of the `prefixes' parameter */
    private static final String PREFIXES = "prefix";
    /** The name of the `suffixes' parameter */
    private static final String SUFFIXES = "suffix";
    
    /* DATA */

    private String [] prefixes;
    private String [] suffixes;

    /* METHODS */

    /**
     * This creates a new {@link FileGroupsMerger} task. It checks the numbers of inputs and
     * outputs (must have one input and multiple outputs. The inputs must be captured by <tt>patternK</tt>
     * parameters, so that the expansions are visible. There are also (voluntary) parameters:
     * <ul>
     * <li><tt>prefixK</tt> -- the <tt>K</tt>-the group will have the given prefix in the output file name</li>
     * <li><tt>suffixK</tt> -- the <tt>K</tt>-the group will have the given suffix in the output file name</li>
     * </ul>
     */
    public FileGroupsMerger(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (this.output.size() != 1 || !this.output.get(0).contains("**")){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "There must be 1 "
                    + "output pattern.");
        }
        this.extractPatterns(0);
        // extracts the prefixes and suffixes
        prefixes = StringUtils.getValuesField(this.parameters, PREFIXES, this.patterns.length);
        suffixes = StringUtils.getValuesField(this.parameters, SUFFIXES, this.patterns.length);
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

                    key = this.reAffix(key, i, j);

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
     * Given a file name from one group, this adds the prefix &amp; suffix for this group and
     * removes the prefix &amp; suffix for the other group, if possible, so that an unaffixed filename
     * of the other group emerges.
     *
     * @param fileName the original, un-fixed filename from one group
     * @param from the first group (the file name belongs to)
     * @param to the second group
     * @return a version of the filename with affixes from the first group and without the affixes from
     *  the
     */
    String reAffix(String fileName, int from, int to){

        if (prefixes != null && prefixes[from] != null){
            fileName = prefixes[from] + fileName;
        }
        if (suffixes != null && suffixes[from] != null){
            fileName = fileName + suffixes[from];
        }
        if (prefixes != null && prefixes[to] != null && fileName.startsWith(prefixes[to])){
            fileName = fileName.substring(prefixes[to].length());
        }
        if (suffixes != null && suffixes[to] != null && fileName.endsWith(suffixes[to])){
            fileName = fileName.substring(0, fileName.length() - suffixes[to].length());
        }
        return fileName;
    }

    /**
     * This copies all the input files to their output destinations, using the pattern expansions.
     * @param files the inputs, sorted according to input patterns
     */
    private void copyToOutput(Hashtable<String, String>[] files, String outputPattern) throws IOException {

        for (int i = 0; i < files.length; ++i){
            for (String key : files[i].keySet()){

                FileUtils.copyFile(files[i].get(key), StringUtils.replace(outputPattern, this.affix(key, i)));
            }
        }
    }

    /**
     * If the prefix or suffix corresponding to the given group number exists, this adds it to the
     * filename.
     * @param fileName the filename to be (possibly) affixed
     * @param group the group number
     * @return the affixed version of the filename (or the filename, unchanged)
     */
    private String affix(String fileName, int group) {

        if (prefixes != null && prefixes[group] != null){
            fileName = prefixes[group] + fileName;
        }
        if (suffixes != null && suffixes[group] != null){
            fileName = fileName + suffixes[group];
        }
        return fileName;
    }

}
