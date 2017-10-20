/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

/**
 * This class defines where to store an unsigned integer. It is important to note that: 1. the range of the integer is
 * highly limited (unlike the Java 32bit integer values) so that the storeable part of it fits into the
 * specified number of bits (using the internal shift value) and 2. the 'raw' default value is always 0.
 * <p>
 * To illustrate why the default is always 0 and how you can still use other defaults imagine the storage engine
 * creates a new entry. Either the engine knows the higher level logic or we assume the default value is 0 and
 * map this value to the real value on every retrieval request.
 * <p>
 * How could you then implement e.g. a 'priority' value going from [-3, 3] that maps to [0,7] but should
 * have a default value of 3 instead of 0? Either you waste space and map this to [1,7], which means that 0 and 3 both
 * refer to the same 0 value (currently the preferred method due to its simplicity) or you could create a
 * MappedIntEncodedValue class that holds an array or a Map with the raw integers similarly to what StringEncodedValue does:
 * {0: 0, 1: -3, 2: -2, 3: -1, 4: 1, 5: 2, 6: 3}
 */
public class IntEncodedValue implements EncodedValue {

    private final String name;
    /**
     * There are multiple int values possible per edge. Here we specify the index into this integer array.
     */
    protected int fwdDataIndex;
    protected int bwdDataIndex;
    final int bits;
    // we need a long here as our ints are unsigned
    long maxValue;
    int fwdShift;
    int bwdShift;
    int fwdMask;
    int bwdMask;
    int defaultValue;
    boolean store2DirectedValues;

    public IntEncodedValue(String name, int bits) {
        this(name, bits, 0, false);
    }

    /**
     * This constructor reserves the specified number of bits in the underlying data structure or twice the amount if
     * store2DirectedValues is true.
     *
     * @param defaultValue         defines which value to return if the 'raw' integer value is 0.
     * @param store2DirectedValues if true the encoded value can be different for the forward and backward
     *                             direction of an edge.
     */
    public IntEncodedValue(String name, int bits, int defaultValue, boolean store2DirectedValues) {
        this.name = name;
        if (!name.toLowerCase().equals(name))
            throw new IllegalArgumentException("EncodedValue name must be lower case but was " + name);

        this.bits = bits;
        if (bits <= 0)
            throw new IllegalArgumentException("bits cannot be 0 or negative");
        if (bits > 32)
            throw new IllegalArgumentException("At the moment bits cannot be >32");
        this.defaultValue = defaultValue;
        this.store2DirectedValues = store2DirectedValues;
    }

    @Override
    public final int init(EncodedValue.InitializerConfig init) {
        if (isInitialized())
            throw new IllegalStateException("Cannot call init multiple times");

        init.find(bits);
        this.fwdMask = init.wayBitMask;
        this.fwdDataIndex = init.dataIndex;
        this.fwdShift = init.shift;
        if (store2DirectedValues) {
            init.find(bits);
            this.bwdMask = init.wayBitMask;
            this.bwdDataIndex = init.dataIndex;
            this.bwdShift = init.shift;
        }

        this.maxValue = (1L << bits) - 1;
        return store2DirectedValues ? 2 * bits : bits;
    }

    private boolean isInitialized() {
        return fwdMask != 0;
    }

    private void checkValue(int value) {
        if (!isInitialized())
            throw new IllegalStateException("EncodedValue " + getName() + " not initialized");
        if (value > maxValue)
            throw new IllegalArgumentException(name + " value too large for encoding: " + value + ", maxValue:" + maxValue);
        if (value < 0)
            throw new IllegalArgumentException("negative value for " + name + " not allowed! " + value);
    }

    /**
     * This method stores the specified integer value in the specified IntsRef.
     */
    public final void setInt(boolean reverse, IntsRef ref, int value) {
        checkValue(value);
        uncheckedSet(reverse, ref, value);
    }

    final void uncheckedSet(boolean reverse, IntsRef ref, int value) {
        if (store2DirectedValues && reverse) {
            int flags = ref.ints[bwdDataIndex + ref.offset];
            // clear value bits
            flags &= ~bwdMask;
            value <<= bwdShift;
            // set value
            ref.ints[bwdDataIndex + ref.offset] = flags | value;
        } else {
            int flags = ref.ints[fwdDataIndex + ref.offset];
            flags &= ~fwdMask;
            value <<= fwdShift;
            ref.ints[fwdDataIndex + ref.offset] = flags | value;
        }
    }

    /**
     * This method restores the integer value from the specified 'flags' taken from the storage.
     */
    public final int getInt(boolean reverse, IntsRef ref) {
        int flags;
        if (reverse && store2DirectedValues) {
            flags = ref.ints[bwdDataIndex + ref.offset];
            flags &= bwdMask;
            flags >>>= bwdShift;
        } else {
            flags = ref.ints[fwdDataIndex + ref.offset];
            flags &= fwdMask;
            flags >>>= fwdShift;
        }
        // return the integer value
        if (flags == 0)
            return defaultValue;
        return flags;
    }

    @Override
    public final int hashCode() {
        return (bwdMask | fwdMask) ^ (fwdDataIndex + bwdDataIndex);
    }


    @Override
    public final boolean equals(Object obj) {
        IntEncodedValue other = (IntEncodedValue) obj;
        return other.fwdMask == fwdMask && other.bwdMask == bwdMask && other.bits == bits
                && other.fwdDataIndex == fwdDataIndex && other.bwdDataIndex == other.bwdDataIndex && other.name.equals(name);
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String toString() {
        return getName();
    }
}
