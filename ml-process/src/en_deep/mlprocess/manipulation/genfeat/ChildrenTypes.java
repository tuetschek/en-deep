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

import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.DataReader;
import en_deep.mlprocess.manipulation.DataReader.FeatType;
import en_deep.mlprocess.manipulation.DataReader.WordInfo;
import en_deep.mlprocess.manipulation.StToArff;
import en_deep.mlprocess.utils.StringUtils;

/**
 * This provides several features for the different POS-types of children (nominal, verbal,
 * preposition, particle -- according to the configuration) + features for their total number.
 * @author Ondrej Dusek
 */
public class ChildrenTypes extends ParametrizedFeature {

    /* CONSTANTS */

    /** The children_types parameter name */
    private static final String CHILDREN_TYPES = "children_types";

    /* DATA */

    /** Children patterns */
    private final String [] patterns;
    /** Names for all possible features (depend on {@link #patterns}) */
    private final String [] names;
    
    /* METHODS */
    
    /**
     * This initializes the feature using the following {@link StToArff} (necessary!) parameter:
     * <ul>
     * <li><tt>children_types</tt> -- space-separated patterns matching morphological tags
     * for the individual features to be generated</li>
     * <ul>
     * Examples:
     * <ul>
     * <li>English: <tt>^[NJPF].* ^[VM].* ^[MNJPFV].* ^[IT].* ^RP</tt></li>
     * <li>Czech: <tt>^[ACNPD].* ^V.* ^[ACNPDV].* ^R.* ^J.*</tt></li>
     * </ul>
     * @param reader
     */
    public ChildrenTypes(DataReader reader) throws TaskException {

        super(reader, FeatType.SYNT);

        if (reader.getTaskParameter(CHILDREN_TYPES) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, reader.getTaskId(),
                    "ChildrenTypes patterns specification is missing.");
        }

        patterns = reader.getTaskParameter(CHILDREN_TYPES).split("\\s+");

        names = new String [patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            names[i] = patterns[i].replaceAll("[^A-Z]", "");
        }
    }


    /**
     * This returns the header for one feature (and one count feature).
     * @param suffix
     */
    private String getHeaderText(String suffix){
        return this.getParametrizedHeader("ChildrenType_" + suffix, DataReader.STRING) + LF
                + DataReader.ATTRIBUTE + " ChildrenTypeNum_" + suffix + " " + DataReader.INTEGER;
    }

    @Override
    public String getHeader() {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.names.length; i++) {
            sb.append(this.getHeaderText(names[i]));
            if (i < this.names.length-1){
                sb.append(LF);
            }
        }
        return sb.toString();
    }

    @Override
    public String generate(int wordNo, int predNo) {

        int [] children = this.reader.getChildren(wordNo);
        String [] pos = this.reader.getWordsInfo(children, WordInfo.POS);
        String [] [] data = new String [children.length] [];

        for (int i = 0; i < children.length; ++i){
            data[i] = this.getFields(children[i]);
        }

        StringBuilder out = new StringBuilder();

        for (int j = 0; j < patterns.length; j++) {

            // find and count matching children
            int num = 0;
            String [] [] matching = new String [children.length] [];

            for (int i = 0; i < pos.length; i++) {
                if (pos[i].matches(patterns[j])){
                    matching[i] = data[i];
                    num++;
                }
            }
            // add them to the data
            out.append(StringUtils.join(StringUtils.nGrams(matching, this.attrPos.length, SEP), ",", true));
            out.append(",").append(num);

            if (j < patterns.length-1){
                out.append(",");
            }
        }
        
        return out.toString();
    }
}
