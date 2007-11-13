/*

 Copyright (c) 1997-2005 The Regents of the University of California.
 All rights reserved.
 Permission is hereby granted, without written agreement and without
 license or royalty fees, to use, copy, modify, and distribute this
 software and its documentation for any purpose, provided that the above
 copyright notice and the following two paragraphs appear in all copies
 of this software.

 IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
 FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
 ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
 PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
 CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
 ENHANCEMENTS, OR MODIFICATIONS.

 PT_COPYRIGHT_VERSION_2
 COPYRIGHTENDKEY

 */

package ptolemy.actor.gt.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**

 @author Thomas Huining Feng
 @version $Id$
 @since Ptolemy II 6.1
 @Pt.ProposedRating Red (tfeng)
 @Pt.AcceptedRating Red (tfeng)
 */
public class TwoWayHashMap<K, V> extends HashMap<K, V> {

    /**
     *
     */
    public TwoWayHashMap() {
    }

    /**
     * @param initialCapacity
     */
    public TwoWayHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * @param initialCapacity
     * @param loadFactor
     */
    public TwoWayHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    /**
     * @param map
     */
    public TwoWayHashMap(Map<K, V> map) {
        super();
        for (K key : map.keySet()) {
            put(key, map.get(key));
        }
    }

    public void clear() {
        super.clear();
        _reverseMap.clear();
    }

    @SuppressWarnings("unchecked")
    public Object clone() {
        TwoWayHashMap<K, V> map = (TwoWayHashMap<K, V>) super.clone();
        map._reverseMap = new HashMap<V, K>(_reverseMap);
        return map;
    }

    public boolean containsValue(Object value) {
        return _reverseMap.containsKey(value);
    }

    public K getKey(Object value) {
        return _reverseMap.get(value);
    }

    public V put(K key, V value) {
        V oldValue = super.put(key, value);
        _reverseMap.put(value, key);
        return oldValue;
    }

    public V remove(Object key) {
        V oldValue = super.remove(key);
        if (oldValue != null) {
            _reverseMap.remove(oldValue);
        }
        return oldValue;
    }

    public Set<V> values() {
        return Collections.unmodifiableSet(_reverseMap.keySet());
    }

    private HashMap<V, K> _reverseMap = new HashMap<V, K>();

}
