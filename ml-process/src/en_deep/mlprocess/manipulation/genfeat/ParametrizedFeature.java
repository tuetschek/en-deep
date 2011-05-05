/*
 *  Copyright (c) 2011 Ondrej Dusek
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

import en_deep.mlprocess.manipulation.DataReader;
import en_deep.mlprocess.manipulation.DataReader.FeatType;
import en_deep.mlprocess.utils.StringUtils;

/**
 * This is the super-class for all generated {@link Feature}s which may use different
 * attributes based on the parameters specified in the {@link Task} class.
 * 
 * @author Ondrej Dusek
 */
public abstract class ParametrizedFeature extends Feature {


    /* DATA */

    /** Attribute input positions to be used */
    protected int [] attrPos;

    /** Attribute names to be used */
    private String [] attrNames;

    /* METHODS */

    /**
     * Constructor, to be used by subclasses only
     * @param reader the language-specific ST-file configuration
     * @param type the type of this generated feature -- morphological or syntactic
     */
    protected ParametrizedFeature(DataReader reader, FeatType type){
        super(reader);

        this.attrPos = reader.getGenFeatColumns(type);
        this.attrNames = reader.getAttributeNames(this.attrPos);
    }

    /**
     * Return the parametrized ARFF header, with the given prefix and attribute types.
     *
     * @param prefix the prefix for parametrized attribute names
     * @param type the attribute type to be used on the output
     * @return
     */
    protected String getParametrizedHeader(String prefix, String type){

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < attrNames.length; ++i){
            if (i > 0){
                sb.append(LF);
            }
            sb.append(DataReader.ATTRIBUTE).append(" ").append(prefix)
                    .append("_").append(attrNames[i]).append(" ").append(type);
        }

        return sb.toString();
    }

    /**
     * This returns all the needed information about one word, as a field. The values
     * are already escaped using {@link StringUtils#escape(java.lang.String)}.
     * 
     * @param wordNo the number of the word
     * @return the information about the given word
     */
    protected String [] getFields(int wordNo) {

        String [] info = new String [this.attrPos.length];

        for (int i = 0; i < this.attrPos.length; ++i){
            info[i] = StringUtils.escape(this.reader.getWordInfo(wordNo, this.attrPos[i]));
        }
        return info;
    }

}
