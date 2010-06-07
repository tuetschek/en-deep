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
import en_deep.mlprocess.manipulation.StToArff.StToArffConfig;
import en_deep.mlprocess.manipulation.StToArff;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class adds the number(s) of the clusters for the individual words to the list of
 * usable features. It needs an additional parameter to the {@link StToArff} class:
 * <ul>
 * <li><tt>clusters_file</tt> -- a space-separated list of filenames in the working directory that contain the
 *      cluster associations</li>
 * </ul>
 * The cluster file must contain the list of words in one cluster on each line.
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
    /** Separator for data types in cluster feature names */
    private static final String FEAT_NAME_SEP = "_";
    /** Number of the cluster that contains not-associated tokens */
    private static final String NO_CLUSTER = "-1";

    /* DATA */
    
    /** The names of all the given cluster files */
    private String [] clusterFileNames;
    /** The data types used for all the individual clusters */
    private int [] [] clusterDataTypes;
    /** The names of the features (corresponding to {@link #clusterFileNames} */
    private String [] featNames;

    /** The cluster information variants for all words and all cluster files */
    private Hashtable<String, Integer> [] clusters;
    
    /* METHODS */


    /**
     * Just the initialization -- reads the cluster files and saves the clusters for later use.
     * @param config
     */
    public Clusters(StToArffConfig config) throws TaskException {

        super(config);

        if (config.getTaskParameter(CLUSTER_FILE) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, config.getTaskId(),
                    "Parameter cluster_file is missing.");
        }
        this.clusterFileNames = config.getTaskParameter(CLUSTER_FILE).split("\\s+");
        this.clusterDataTypes = new int [this.clusterFileNames.length] [];
        this.featNames = new String [this.clusterFileNames.length];

        // select the cluster file names
        for (int i = 0; i < this.clusterFileNames.length; ++i){
            this.clusterFileNames[i] = Process.getInstance().getWorkDir() + this.clusterFileNames[i];
        }

        // create the clusters -- load the contents of the cluster files and create the names of new features
        try {
            this.loadClusters();
        }
        catch(IOException e){
            throw new TaskException(TaskException.ERR_IO_ERROR, config.getTaskId(), 
                    "Error reading cluster files: " + e.getMessage());
        }
    }

    @Override
    public String getHeader() {

        StringBuilder text = new StringBuilder();

        for (int i = 0; i < this.featNames.length; ++i){

            text.append(StToArff.ATTRIBUTE + " " + this.featNames[i] + " " + StToArff.STRING);
            if (i < this.featNames.length - 1){
                text.append(LF);
            }
        }

        return text.toString();
    }

    @Override
    public String generate(Vector<String[]> sentence, int wordNo, int predNo) {

        StringBuilder text = new StringBuilder();

        for (int i = 0; i < this.clusters.length; ++i){

            StringBuilder token = new StringBuilder();
            Integer clusterNo;

            for (int j = 0; j < this.clusterDataTypes[i].length; ++j){
                if (j > 0){
                    token.append(DATA_TYPE_SEP);
                }
                token.append(sentence.get(wordNo)[this.clusterDataTypes[i][j]]);
            }

            if (i > 0){
                text.append(",");
            }
            clusterNo = this.clusters[i].get(token.toString());
            if (clusterNo != null){
                text.append(clusterNo.toString());
            }
            else {
                text.append(NO_CLUSTER);
            }
        }
        return text.toString();
    }

    /**
     * Load the files that contain the word clustering information. According to that information,
     * create the corresponding cluster feature names.
     * <p>
     * The input cluster file must contain space-separated column numbers from the ST-file that
     * were used to generate the clusters on the first line, followed by the cluster associations
     * on the next lines. The cluster associations must have the following format:
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
                    throw new TaskException(TaskException.ERR_INVALID_DATA, config.getTaskId(),
                            "Invalid data types specification in " + this.clusterFileNames[i] + ".");
                }
                this.featNames[i] += FEAT_NAME_SEP + this.clusterDataTypes[i][j];
            }
            
            this.clusters[i] = this.readClusters(in, this.clusterFileNames[i]);
            in.close();
        }
    }

    /**
     * Reads all the cluster associations from one text file (format: see {@link #loadClusters()}).
     * @param in the opened input file
     * @param name the name of the opened input file
     * @return the cluster assoctiations.
     */
    private Hashtable<String, Integer> readClusters(RandomAccessFile in, String name) throws IOException, TaskException {

        Hashtable<String, Integer> data = new Hashtable<String, Integer>();
        String line;
        int lineNo = 2; // first line already read

        // read all the next lines
        while ((line = in.readLine()) != null){

            String [] lineParts = line.split(":"); // cluster_number: words
            int clusterId;
            
            if (lineParts.length != 2){
                throw new TaskException(TaskException.ERR_INVALID_DATA, config.getTaskId(), "Clustering file error in "
                        + name + " at line " + lineNo);
            }
            try {
                clusterId = Integer.parseInt(lineParts[0].trim());
            }
            catch (NumberFormatException e){
                throw new TaskException(TaskException.ERR_INVALID_DATA, config.getTaskId(), "Clustering file error in "
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
