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
import en_deep.mlprocess.manipulation.DataReader.WordInfo;
import en_deep.mlprocess.utils.StringUtils;

/**
 * This generated feature adds the LEMMA of the left and right sibling of
 * the given word. If the word doesn't have the appropriate sibling, an empty string is returned.
 * 
 * @author Ondrej Dusek
 */
public class SiblingLemma extends Feature {


    /* METHODS */

    public SiblingLemma(DataReader reader){
        super(reader);
    }

    @Override
    public String getHeader() {
        return DataReader.ATTRIBUTE + " LeftSiblingLemma " + DataReader.STRING + LF
                + DataReader.ATTRIBUTE + " RightSiblingLemma " + DataReader.STRING;
    }

    @Override
    public String generate(int wordNo, int predNo) {

        // produce output -- find the lemmas of the both siblings, if applicable
        return "\"" + StringUtils.escape(this.reader.getWordInfo(
                this.reader.getSibling(wordNo, Direction.LEFT), WordInfo.LEMMA)) + "\",\""
                + StringUtils.escape(this.reader.getWordInfo(
                this.reader.getSibling(wordNo, Direction.RIGHT), WordInfo.LEMMA)) + "\"";
    }

}
