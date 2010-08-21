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

package en_deep.mlprocess.manipulation.genfeat;

import en_deep.mlprocess.Process;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.StReader;
import en_deep.mlprocess.manipulation.StReader.Direction;
import en_deep.mlprocess.manipulation.StToArff;
import en_deep.mlprocess.utils.StringUtils;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Hashtable;

/**
 * This class adds the number(s) of the clusters for the individual words to the list of
 * usable features. It needs an additional parameter to the {@link StToArff} class:
 * <ul>
 * <li><tt>cluster_file</tt> -- a space-separated list of filenames in the working directory that contain the
 *      clusterType associations</li>
 * </ul>
 * The clusterType file must contain the list of words in one clusterType on each line.
 * @author Ondrej Dusek
 */
public class Clusters extends Feature {

    /* CONSTANTS */

    /** The cluster_file parameter name */
    private static final String CLUSTER_FILE = "cluster_file";
    /** Feature name prefix */
    private static final String FEAT_NAME_PREFIX = "Cluster";

    /** Separator for different data types within one clustering definition */
    private static final char DATA_TYPE_SEP = '^';
    /** Separator for data types in clusterType feature names */
    private static final String FEAT_NAME_SEP = "_";
    /** Number of the clusterType that contains not-associated tokens */
    private static final String NO_CLUSTER = "-1";
    /** Separator for data values in bigrams etc. */
    private static final String DATA_SEP = "|";

    /* DATA */
    
    /** The names of all the given clusterType files */
    private String [] clusterFileNames;
    /** The data types used for all the individual clusters */
    private int [] [] clusterDataTypes;
    /** The names of the features (corresponding to {@link #clusterFileNames} */
    private String [] featNames;

    /** The clusterType information variants for all words and all clusterType files */
    private Hashtable<String, Integer> [] clusters;
    
    /* METHODS */


    /**
     * Just the initialization -- reads the clusterType files and saves the clusters for later use.
     * @param reader
     */
    public Clusters(StReader reader) throws TaskException {

        super(reader);

        if (reader.getTaskParameter(CLUSTER_FILE) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, reader.getTaskId(),
                    "Parameter cluster_file is missing.");
        }
        this.clusterFileNames = reader.getTaskParameter(CLUSTER_FILE).split("\\s+");
        this.clusterDataTypes = new int [this.clusterFileNames.length] [];
        this.featNames = new String [this.clusterFileNames.length];

        // select the clusterType file names
        for (int i = 0; i < this.clusterFileNames.length; ++i){
            this.clusterFileNames[i] = StringUtils.getPath(this.clusterFileNames[i]);
        }

        // create the clusters -- load the contents of the clusterType files and create the names of new features
        try {
            this.loadClusters();
        }
        catch(IOException e){
            throw new TaskException(TaskException.ERR_IO_ERROR, reader.getTaskId(),
                    "Error reading cluster files: " + e.getMessage());
        }
    }

    @Override
    public String getHeader() {

        StringBuilder text = new StringBuilder();

        for (int i = 0; i < this.featNames.length; ++i){

            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append(" " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_Left3 " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_Left2 " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_Left1 " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_Left12 " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_Right12 " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_Right1 " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_Right2 " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_Right3 " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_Parent " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_Children " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_LeftSibling " + StToArff.STRING).append(LF);
            text.append(StToArff.ATTRIBUTE + " ").append(this.featNames[i]).append("_RightSibling " + StToArff.STRING);

            if (i < this.featNames.length - 1){
                text.append(LF);
            }
        }

        return text.toString();
    }

    @Override
    public String generate(int wordNo, int predNo) {

        StringBuilder text = new StringBuilder();

        for (int clusterType = 0; clusterType < this.clusters.length; ++clusterType){

            if (clusterType > 0){
                text.append(",");
            }
            // the word itself
            text.append(this.getCluster(clusterType, wordNo)).append(",");

            // left3 ... right3
            text.append(this.getCluster(clusterType, wordNo - 3)).append(",");
            text.append(this.getCluster(clusterType, wordNo - 2)).append(",");
            text.append(this.getCluster(clusterType, wordNo - 1)).append(",");
            text.append(this.getCluster(clusterType, wordNo - 2)).append(DATA_SEP).append(
                    this.getCluster(clusterType, wordNo - 1)).append(",");
            text.append(this.getCluster(clusterType, wordNo + 1)).append(DATA_SEP).append(
                    this.getCluster(clusterType, wordNo + 2)).append(",");
            text.append(this.getCluster(clusterType, wordNo + 1)).append(",");
            text.append(this.getCluster(clusterType, wordNo + 2)).append(",");
            text.append(this.getCluster(clusterType, wordNo + 3)).append(",");

            // parent
            text.append(this.getCluster(clusterType, this.reader.getHeadPos(wordNo))).append(",");

            // children
            int [] childrenPos = this.reader.getChildrenPos(wordNo);
            if (childrenPos.length == 0){
                text.append("-");
            }
            else {
                for (int j = 0; j < childrenPos.length; ++j){
                    if (j > 0){
                        text.append(DATA_SEP);
                    }
                    text.append(this.getCluster(clusterType, childrenPos[j]));
                }
            }
            text.append(",");

            // left & right sibling
            text.append(this.getCluster(clusterType, this.reader.getSiblingPos(wordNo, Direction.LEFT))).append(",");
            text.append(this.getCluster(clusterType, this.reader.getSiblingPos(wordNo, Direction.RIGHT)));
        }
        return text.toString();
    }

    
    /**
     * Returns the ID of clusterType for the given word and clusterType data type. Returns "-" for
     * out-of-range requests.
     *
     * @param dataTypeNo Order of the chosen clusterType file.
     * @param token The number of the desired word.
     * @return The clusterType ID for the token, or {@link #NO_CLUSTER} if not applicable.
     */
    private String getCluster(int dataTypeNo, int wordNo) {

        String token = this.getToken(dataTypeNo, wordNo);

        if (token.equals("")){
            return "-";
        }

        Integer clusterNo = this.clusters[dataTypeNo].get(token);

        if (clusterNo != null) {
            return clusterNo.toString();
        } else {
            return NO_CLUSTER;
        }
    }

    /**
     * Returns a token that may have been assigned a clusterType for the given clusterType type
     * (clusterType file). For out-of-range requests, an empty string is returned.
     * 
     * @param dataTypeNo Order of the chosen clusterType file.
     * @param word The number of the desired word in the current sentence.
     * @return The token that identifies a clusterType.
     */
    private String getToken(int dataTypeNo, int wordNo) {

        if (wordNo < 0 || wordNo >= this.reader.length()){
            return "";
        }

        StringBuilder token = new StringBuilder();

        for (int j = 0; j < this.clusterDataTypes[dataTypeNo].length; ++j) {
            if (j > 0) {
                token.append(DATA_TYPE_SEP);
            }
            token.append(this.reader.getWordInfo(wordNo, this.clusterDataTypes[dataTypeNo][j]));
        }
        return token.toString();
    }

    /**
     * Load the files that contain the word clustering information. According to that information,
     * create the corresponding clusterType feature names.
     * <p>
     * The input clusterType file must contain space-separated column numbers from the ST-file that
     * were used to generate the clusters on the first line, followed by the clusterType associations
     * on the next lines. The clusterType associations must have the following format:
     * </p>
     * <tt>cluster_number: word1 word2 word3 ...\n</tt>
     */
    private void loadClusters() throws IOException, TaskException {
        
        this.clusters = new Hashtable[this.clusterFileNames.length];

        for (int i = 0; i < this.clusterFileNames.length; ++i){
            
            RandomAccessFile in = new RandomAccessFile(this.clusterFileNames[i], "r");
            String [] usedCols = in.readLine().trim().split("\\s+");

            this.clusterDataTypes[i] = new int [usedCols.length];
            this.featNames[i] = FEAT_NAME_PREFIX;
            for (int j = 0; j < usedCols.length; ++j){
                try {
                    this.clusterDataTypes[i][j] = Integer.parseInt(usedCols[j]);
                }
                catch(NumberFormatException e){
                    throw new TaskException(TaskException.ERR_INVALID_DATA, reader.getTaskId(),
                            "Invalid data types specification in " + this.clusterFileNames[i] + ".");
                }
                this.featNames[i] += FEAT_NAME_SEP + this.clusterDataTypes[i][j];
            }
            
            this.clusters[i] = this.readClusters(in, this.clusterFileNames[i]);
            in.close();
        }
    }

    /**
     * Reads all the clusterType associations from one text file (format: see {@link #loadClusters()}).
     * @param in the opened input file
     * @param name the name of the opened input file
     * @return the clusterType assoctiations.
     */
    private Hashtable<String, Integer> readClusters(RandomAccessFile in, String name) throws IOException, TaskException {

        Hashtable<String, Integer> data = new Hashtable<String, Integer>();
        String line;
        int lineNo = 2; // first line already read

        // read all the next lines
        while ((line = in.readLine()) != null){

            String [] lineParts = line.split(":", 2); // cluster_number: words (which may contain a colon themselves)
            int clusterId;
            
            try {
                clusterId = Integer.parseInt(lineParts[0].trim());
            }
            catch (NumberFormatException e){
                throw new TaskException(TaskException.ERR_INVALID_DATA, reader.getTaskId(), "Clustering file error in "
                        + name + " at line " + lineNo);
            }

            String [] words = lineParts[1].trim().split("\\s+");

            for (int i = 0; i < words.length; ++i){
                data.put(words[i], clusterId);
            }
        }

        return data;
    }

}
