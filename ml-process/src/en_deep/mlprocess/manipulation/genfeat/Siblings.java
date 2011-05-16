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

import en_deep.mlprocess.manipulation.DataReader;
import en_deep.mlprocess.manipulation.DataReader.Direction;
import en_deep.mlprocess.manipulation.DataReader.FeatType;
import en_deep.mlprocess.utils.StringUtils;

/**
 * This generated feature adds the word form of the left and right sibling of
 * the given word. If the word doesn't have the appropriate sibling, an empty string is returned.
 * 
 * @author Ondrej Dusek
 */
public class Siblings extends ParametrizedFeature {


    /* METHODS */

    public Siblings(DataReader reader){
        super(reader, FeatType.SYNT);
    }

    @Override
    public String getHeader() {
        return this.getParametrizedHeader("LeftSibling", DataReader.STRING) + LF
                + this.getParametrizedHeader("RightSibling", DataReader.STRING) + LF
                + this.getParametrizedHeader("LeftSiblings", DataReader.STRING) + LF
                + this.getParametrizedHeader("RightSiblings", DataReader.STRING);
    }

    @Override
    public String generate(int wordNo, int predNo) {

        int head = this.reader.getHead(wordNo);
        String left = null;
        String right = null;

        // only non-root nodes may have some siblings
        if (head != -1){
            int [] siblings = this.reader.getChildren(head);

            int leftLen = 0;
            while (siblings[leftLen] != wordNo){
                leftLen++;
            }

            if (leftLen > 0){ // there are some left siblings
                String [] [] data = new String [leftLen] [];
                for (int i = 0; i < data.length; ++i){
                    data[i] = this.getFields(siblings[i]);
                }
                left = StringUtils.join(StringUtils.nGrams(data, SEP), ",", true);
            }
            if (leftLen < siblings.length - 1){ // there are some right siblings
                String [] [] data = new String [siblings.length - leftLen - 1] [];

                for (int i = 0; i < data.length; ++i){
                    data[i] = this.getFields(siblings[leftLen + i + 1]);
                }
                right = StringUtils.join(StringUtils.nGrams(data, SEP), ",", true);
            }
        }
        // dummy values if there are no such siblings
        if (left == null){
            left = StringUtils.join(StringUtils.nGrams(new String [this.attrPos.length] [], SEP), ",", true);
        }
        if (right == null){
            right = StringUtils.join(StringUtils.nGrams(new String [this.attrPos.length] [], SEP), ",", true);
        }

        return StringUtils.join(this.getFields(this.reader.getSibling(wordNo, Direction.LEFT)), ",", true)
                + "," + StringUtils.join(this.getFields(this.reader.getSibling(wordNo, Direction.RIGHT)), ",", true)
                + "," + left + "," + right;
    }

}
