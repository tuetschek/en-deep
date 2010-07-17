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

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.MathUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This makes an average from several different attribute rankings (it averages the attributes' positions,
 * if the resulting numbers are the same, it ranks them according to the first ranking given).
 * @author Ondrej Dusek
 */
public class AverageAttributeRanks extends Task {

    /* CONSTANTS */

    /** The num_selected parameter name */
    private static final String NUM_SELECTED = WekaClassifier.NUM_SELECTED;

    /* DATA */

    /** Maximum number of selected attributes */
    private int numSelected;

    /* METHODS */

    /**
     * This just checks the inputs and outputs and one voluntary parameter.
     * <ul>
     * <li><tt>num_selected</tt> -- number of attributes selected (the rest of the list is truncated)</li>
     * </ul>
     */
    public AverageAttributeRanks(String id, Hashtable<String, String> parameters, Vector<String> input,
            Vector<String> output) throws TaskException{

        super(id, parameters, input, output);

        if (this.input.size() < 2){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "Must have at least 2 inputs.");
        }
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "Must have 1 output.");
        }
        if (this.hasParameter(NUM_SELECTED)){
            this.numSelected = this.getIntParameterVal(NUM_SELECTED);
        }
        else {
            this.numSelected = -1;
        }
    }


    @Override
    public void perform() throws TaskException {

        try {

            int [][] ranks = new int [this.input.size()][];
            int i = 0;
            for (String file : this.input){
                ranks[i++] = this.readRank(file);
            }

            this.writeRank(this.output.get(0), this.average(ranks));
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
     * This reads one attribute ranking from a file
     * @param fileName the file name
     * @return the attribute ranking
     * @throws Exception if the file doesn't exist or has an invalid format
     */
    private int[] readRank(String fileName) throws Exception {

        RandomAccessFile in = new RandomAccessFile(fileName, "r");
        int [] rank = StringUtils.readListOfInts(in.readLine());
        in.close();
        return rank;
    }

    /**
     * Makes an average from various attribute ranks. Heeds the {@link #numSelected} setting.
     * @param ranks the various attribute rankings
     * @return the average attribute ranking
     */
    private int [] average(int [][] ranks) throws TaskException{

        for (int i = 0; i < ranks.length; ++i){
            if (ranks[i].length != ranks[0].length){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Numbers of attributes "
                        + "in rank files differ.");
            }
        }

        // find out the average ranking
        double [] sums = new double [ranks[0].length + 1];

        for (int i = 0; i < ranks.length; i++) {
            for (int j = 0; j < ranks[i].length; j++) {
                // 1 as minimum ensures that that class attribute (the only missing from rankings) will have the lowest
                sums[ranks[i][j]] += ranks[i].length - j; 
            }
        }
        int [] avgRank = Arrays.copyOf(MathUtils.getOrder(sums), ranks[0].length); // get rid of the class attribute

        // ensure the attributes with the same rankings are sorted according to the first ranking
        boolean change = true;
        while (change){
            change = false;
            for (int i = 1; i < avgRank.length; i++) {
                if (sums[avgRank[i]] == sums[avgRank[i-1]]){
                    if (MathUtils.find(ranks[0], avgRank[i-1]) > MathUtils.find(ranks[0], avgRank[i])){

                        int tmp = avgRank[i];
                        avgRank[i] = avgRank[i-1];
                        avgRank[i-1] = tmp;
                        change = true;
                    }
                }
            }
        }

        if (this.numSelected != -1){
            return Arrays.copyOf(avgRank, this.numSelected);
        }
        return avgRank;
    }

    /**
     * This writes the given attribute ranking into a file.
     * @param fileName the file to write into
     * @param rank the attribute ranking to be written
     * @throws IOException
     */
    private void writeRank(String fileName, int [] rank) throws IOException {

        FileUtils.writeString(fileName, StringUtils.join(rank, " ") + System.getProperty("line.separator"));
    }



}
