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

package en_deep.mlprocess.computation.wekaclassifier;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.genfeat.Feature;
import java.util.ArrayList;
import java.util.Enumeration;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * A {@link Sequence} that browses through the ARFF data containing syntactic trees in a DFS order, tree-by-tree,
 * propagating the newly set class values to the syntactic neighborhood of the current node.
 *
 * @author Ondrej Dusek
 */
public class TreeReader implements Sequence {

    /* CONSTANT */

    /** Binary mode name */
    private static final String BINARY = "bin";

    /** Possible fillers for missing nominal values */
    private static final String [] FILLERS = { "[OTHER]", "" };

    /** Multiple valued features: separator for the individual values */
    private static final String SEP = Feature.SEP;

    /* DATA */

    /** The current task id */
    private String taskId;

    /** The data set (original, with no filtered attributes) */
    private Instances data;

    /** Are we working with binary attributes ? */
    private boolean binMode;

    /** The current instance node */
    private Node curNode;
    /** The current root */
    private Node curRoot;

    /** Start of the current sentence in the data file */
    private int curSentBase;
    /** Length of the current sentence in the data file */
    private int curSentLen;

    /** Name of the attribute with the head of the current node's class value */
    private String headClass;
    /** Name of the attribute with the left sibling of the current node's class value */
    private String leftClass;
    /** Name of the attribute with class values of all the current node left siblings */
    private String leftClasses;

    /** The word ID attribute index */
    private int wordIdOrd;
    /** The sentence ID attribute index */
    private int sentIdOrd;
    /** The syntactic attribute index */
    private int headOrd;

    /* METHODS */

    /**
     * Given the ARFF data file and a parameter string from the {@link Task} parameters,
     * this creates a new {@link TreeReader}.
     * <p>
     * The format of the class parameters is as follows:
     * </p>
     * <pre>
     * mode wordId sentId head headClass leftClass leftClasses
     * </pre>
     * Where:
     * <ul>
     * <li><tt>mode</tt> is <tt>bin</tt> or <tt>nom</tt>, i.e. working with binary or nominal attributes</li>
     * <li><tt>wordId</tt> is the word id attribute name</li>
     * <li><tt>sentId</tt> is the sentence id attribute name</li>
     * <li><tt>head</tt> is the attribute name for the word id of this node's head</li>
     * <li><tt>headClass</tt> is the attribute name for the class value of the head of the current node</li>
     * <li><tt>leftClass</tt> is the attribute name for the class value of the nearest left sibling of the current node</li>
     * <li><tt>leftClasses</tt> is the attribute name for the class values all left siblings of the current node</li>
     * </ul>
     *
     * @param taskId the current task id
     * @param data the input ARFF data file (with no filtered attributes, to be used as classification output)
     * @param params parameters: format -- mode wordId sentId head headClass leftClass leftClasses
     */
    public TreeReader(String taskId, Instances data, String params) throws TaskException, Exception {

        this.taskId = taskId;
        String [] paramArr = params.split("\\s+");

        if (paramArr.length != 7){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.taskId, "Invalid parameters for a TreeReader class!");
        }

        this.binMode = paramArr[0].equals(BINARY);

        this.data = data;
        this.findIdxAttributes(paramArr[1], paramArr[2], paramArr[3]);

        this.headClass = paramArr[4];
        this.leftClass = paramArr[5];
        this.leftClasses = paramArr[6];
    }

    /**
     * Returns the instance number of the root node for the next tree. Creates a tree-like structure
     * in {@link #curRoot} to be browsed by all the further calls to {@link #getNextInstance()}.
     * 
     * @return the instance number of the root node for the next tree
     */
    private int getNextTree(){
        
        this.curSentBase += this.curSentLen; // first call: 0 + 0 = 0 and the further search begins

        if (this.curSentBase >= this.data.numInstances()){
            return -1;
        }

        this.curSentLen = 0;
        int curSentId = (int) this.data.get(this.curSentBase).value(this.sentIdOrd);
        int root = -1;

        while (this.curSentBase + this.curSentLen < this.data.numInstances()
                && (int) this.data.get(this.curSentBase + this.curSentLen).value(this.sentIdOrd) == curSentId){

            if (this.data.get(this.curSentBase + this.curSentLen).value(this.headOrd) == 0){
                root = this.curSentBase + this.curSentLen;
            }
            this.curSentLen++;
        }

        this.curRoot = this.exploreSubtree(root, null);
        this.curNode = null;

        return root;
    }

    /**
     * This creates a new node and explores its whole subtree recursively.
     * @param inst the input data instance whose subtree is to be explored
     * @param head the syntactic head of the current node
     * @return the whole subtree of the newly created node
     */
    private Node exploreSubtree(int inst, Node head){

        Node n = new Node(inst);

        n.head = head;
        n.subtreeLast = n;
        
        int [] childrenInst = this.getChildren(n.instance);
        if (childrenInst != null && childrenInst.length > 0){

            n.children = new Node [childrenInst.length];

            for (int i = 0; i < childrenInst.length; ++i){
                n.children[i] = exploreSubtree(childrenInst[i], n);
                if (i > 0){
                    n.children[i-1].subtreeLast.next = n.children[i];
                    n.children[i-1].rightSibling = n.children[i];
                    n.children[i].leftSibling = n.children[i-1];
                }
            }
            n.next = n.children[0];
            n.subtreeLast = n.children[childrenInst.length-1].subtreeLast;
        }

        return n;
    }

    /**
     * Returns the instance numbers of all syntactical children of the given node.
     * @param instance the instance number of the desired node
     * @return the instance numbers of all its children
     */
    private int[] getChildren(int instance) {
        
        ArrayList<Integer> children = new ArrayList<Integer>();
        int [] childrenArr;

        for (int i = 0; i < this.curSentLen; ++i){
            
            if (this.data.get(this.curSentBase + i).value(this.headOrd)
                    == this.data.get(instance).value(this.wordIdOrd)){

                children.add(this.curSentBase + i);
            }
        }
        childrenArr = new int[children.size()];
        for (int i = 0; i < children.size(); ++i){
            childrenArr[i] = children.get(i);
        }
        return childrenArr;
    }

    /**
     * Returns the instance number of the next node (in DFS).
     * @return the instance number of the next node (in DFS).
     */
    @Override
    public int getNextInstance(){

        // usual case
        if (this.curNode != null){
            this.curNode = this.curNode.next;
        }

        // end of a tree -- move to next one if possible
        if (this.curNode == null){
            if (this.getNextTree() >= 0){
                this.curNode = this.curRoot;
            }
            else {
                return -1;
            }
        }
        return this.curNode.instance;
    }

    /**
     * Sets the class value for the current node and propagates it to all children and right siblings of the current node.
     * @param value the class value to be propagated
     */
    @Override
    public void setCurrentClass(double value){

        String stringVal = this.data.classAttribute().value((int) value);
        this.data.get(this.curNode.instance).setClassValue(value);

        if (this.binMode){
            if (this.curNode.children != null){
                for (Node child : this.curNode.children){
                    this.setBinaryValue(child.instance, this.headClass, stringVal);
                }
                if (this.curNode.rightSibling != null){

                    Node right = this.curNode.rightSibling;
                    this.setBinaryValue(right.instance, this.leftClass, stringVal);

                    while (right != null){
                        this.addBinaryValue(right.instance, this.leftClasses, stringVal);
                        right = right.rightSibling;
                    }
                }
            }
        }
        else {
            if (this.curNode.children != null){
                for (Node child : this.curNode.children){
                    this.setNominalValue(child.instance, this.data.attribute(this.headClass), stringVal);
                }
                if (this.curNode.rightSibling != null){
                    Node right = this.curNode.rightSibling;
                    this.setNominalValue(this.curNode.rightSibling.instance, this.data.attribute(this.leftClass), stringVal);

                    while (right != null){
                        this.addNominalValue(right.instance, this.data.attribute(this.leftClasses), stringVal);
                        right = right.rightSibling;
                    }
                }
            }
        }
    }

    /**
     * This finds the necessary word ID, sentence ID and syntactic head attributes in the input data
     * and throws an exception if they are not present.
     *
     * @param wordId name of the word id parameter
     * @param sentId name of the sentence id parameter
     * @param head name of the syntactic head parameter
     */
    private void findIdxAttributes(String wordId, String sentId, String head) throws TaskException {

        if (this.data.attribute(wordId) == null || this.data.attribute(sentId) == null
                || this.data.attribute(head) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.taskId, "TreeReader:"
                    + "wordId, sentId or head attribute not found in the ARFF data");
        }

        this.wordIdOrd = this.data.attribute(wordId).index();
        this.sentIdOrd = this.data.attribute(sentId).index();
        this.headOrd = this.data.attribute(head).index();
    }

    /**
     * Sets a nominal value of a given attribute for the given instance. If the nominal value is not found in
     * the list of possible values and a current value is missing, it is replaced by {@link #FILLERS}.
     *
     * @param instanceNo number of the instance in question
     * @param attr the attribute to be set
     * @param value the value to be set
     */
    private void setNominalValue(int instanceNo, Attribute attr, String value) {

        if (attr == null){
            return;
        }

        try {
            this.data.get(instanceNo).setValue(attr, value);
        }
        catch (IllegalArgumentException e){

            if (this.data.get(instanceNo).isMissing(attr)){

                int filler = -1;
                int i = 0;

                while (filler == -1 && i < FILLERS.length){
                    filler = attr.indexOfValue(FILLERS[i++]);
                }
                if (filler != -1){
                    this.data.get(instanceNo).setValue(attr, filler);
                }
            }
        }
    }

    /**
     * Sets a binary value for a given original nominal attribute name. Sets all other possible values
     * of this original attribute to zero. If the given nominal value is not found, it is replaced by {@link #FILLERS}.
     *
     * @param instanceNo number of the instance in question
     * @param attrName the name of the original nominal attribute (now expected to be binarized)
     * @param value the value of the original nominal attribute (now expected to be one of the attributes with
     *  the name <tt>attrName=value</tt>)
     */
    private void setBinaryValue(int instanceNo, String attrName, String value) {

        Attribute attr;

        this.makeZero(instanceNo, attrName + "=");

        if ((attr = this.data.attribute(attrName + "=" + value)) != null){
            this.data.get(instanceNo).setValue(attr, 1);
        }
        else {
            int i = 0;
            while (attr == null && i < FILLERS.length){
                attr = this.data.attribute(attrName + "=" + FILLERS[i++]);
            }
            if (attr != null){
                this.data.get(instanceNo).setValue(attr, 1);
            }
        }
    }

    /**
     * Sets a binary value for the given nominal attribute set. All possible values of this set will be zeroed.
     * If the given nominal value is not found, nothing is set.
     * 
     * @param instanceNo number of the instance in question
     * @param attrName the name of the original nominal set attribute (now expected to be binarized)
     * @param value a member of the original nominal set attribute (now expected to be one of the attributes with
     *  the name <tt>attrName&gt;value</tt>)
     */
    private void addBinaryValue(int instanceNo, String attrName, String value) {

        Attribute attr;

        this.makeZero(instanceNo, attrName + ">");

        if ((attr = this.data.attribute(attrName + ">" + value)) != null){
            this.data.get(instanceNo).setValue(attr, 1);
        }
    }

    /**
     * Adds a value to the set for a nominal set attribute. If the combination of the current value + the
     * new value is not in the list of possible values, nothing is done.
     *
     * @param instanceNo number of the instance in question
     * @param attr the nominal set attribute to be modified
     * @param addedValue the new value to be added to the set
     */
    private void addNominalValue(int instanceNo, Attribute attr, String addedValue) {

        if (attr == null){
            return;
        }

        String origValue = this.data.get(instanceNo).stringValue(attr);

        // value is just a filler -> keep it out
        if (origValue != null){
            for (String filler : FILLERS){
                if (origValue.equals(filler)){
                    this.data.get(instanceNo).setMissing(attr);
                    origValue = null;
                    break;
                }
            }
        }

        // first value in the set
        if (origValue == null){
            this.setNominalValue(instanceNo, attr, addedValue);
        }
        // further values
        else {
            this.setNominalValue(instanceNo, attr, origValue + SEP + addedValue);
        }
    }

    /**
     * Set all attributes with the given prefix to zero, if their values are missing for the
     * given instance.
     * @param instanceNo the number of the instance in question
     * @param prefix the prefix for attribute names
     */
    private void makeZero(int instanceNo, String prefix) {

        Enumeration<Attribute> attribs = this.data.enumerateAttributes();
        while (attribs.hasMoreElements()){
            Attribute attrib = attribs.nextElement();
            
            if (attrib.name().startsWith(prefix) && this.data.get(instanceNo).isMissing(attrib)){
                this.data.get(instanceNo).setValue(attrib, 0);
            }
        }
    }


    /**
     * Tree representation of one sentence, used in DFS searches.
     */
    private class Node {

        /** Syntactic head of the current node */
        Node head;
        /** Syntactic children of the current node */
        Node [] children;
        /** The last node in DFS ordering in the subtree of the current node */
        Node subtreeLast;
        /** The next node in DFS ordering for the current tree */
        Node next;
        /** The nearest left sibling of the current node */
        Node leftSibling;
        /** The nearest right sibling of the current node */
        Node rightSibling;

        /** Instance number corresponding to the current node */
        int instance;

        /**
         * Create a node for the given instance
         * @param instance the instance number for the current node
         */
        private Node(int instance) {
            this.instance = instance;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(instance);
            if (this.children != null){
                sb.append("(");
                for (Node child : this.children){
                    sb.append(" ");
                    sb.append(child);
                }
                sb.append(" )");
            }
            return sb.toString();
        }
    }
}

