package org.campagnelab.goby.alignments;

import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Collection;
import java.util.Map;
import java.util.Observer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Fabien Campagne
 * Date: 1/26/13
 * Time: 12:37 PM
 */
public class PositionToBasesMap<T> {
    private Int2ObjectAVLTreeMap<T> delegate = new Int2ObjectAVLTreeMap<T>();
    private IntSortedSet sortedKeys = new IntAVLTreeSet();
    private Int2BooleanAVLTreeMap ignoredPositions = new Int2BooleanAVLTreeMap();

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        IntSortedSet sorted = new IntAVLTreeSet();
        sorted.addAll(delegate.keySet());

        builder.append(String.format("key span: [%d-%d]%n", sorted.firstInt(), sorted.lastInt()));
        for (T value : delegate.values()) {
            builder.append(value.toString());
            builder.append("\n");
        }
        return builder.toString();
    }

    public IntSet keySet() {
        return sortedKeys;
    }

    public boolean containsKey(int position) {
        return sortedKeys.contains(position);
    }

    public int size() {
        return delegate.size();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public void clear() {
        sortedKeys.clear();
        delegate.clear();
        ignoredPositions.clear();
    }

    public T remove(int k) {
        sortedKeys.remove(k);
        ignoredPositions.remove(k);
        return delegate.remove(k);

    }

    public T get(int ok) {
        return delegate.get(ok);
    }

    public void put(int keyPos, T positionBaseInfos) {
        sortedKeys.add(keyPos);
        delegate.put(keyPos, positionBaseInfos);
    }

    public ObjectSet<Map.Entry<Integer, T>> entrySet() {
        return delegate.entrySet();
    }

    /**
     * Get the position of the first base.
     * @return
     */
    public int firstPosition() {
        return sortedKeys.firstInt();
    }

    public void markIgnoredPosition(int position) {
        ignoredPositions.put(position, true);
    }

    public boolean isIgnoredPosition(int position) {
        return ignoredPositions.get(position);
    }

    /**
     * Remove the first base.
     */
    public void removeFirst() {
        if (sortedKeys.isEmpty()) return;
        int keyToRemove = sortedKeys.firstInt();
        sortedKeys.remove(keyToRemove);
        delegate.remove(keyToRemove);
        ignoredPositions.remove(keyToRemove);
    }

    public void removeUpTo(int intermediatePosition) {
        final IntBidirectionalIterator iterator = sortedKeys.iterator();
        while (iterator.hasNext()) {
            int next =  iterator.next();
            if (next<=intermediatePosition) {
                remove(next);
            }else {
                break;
            }

        }
    }

    public int width() {
        if (sortedKeys.isEmpty()) return 0;
        return sortedKeys.lastInt()-sortedKeys.firstInt();
    }
    public void trimWidth(int startFlapLength, Consumer<Integer> processFunction) {
        if (isEmpty()) return;
        int firstPosition=firstPosition();
        while (width()>startFlapLength*2 ) {
            firstPosition=firstPosition();
            processFunction.accept(firstPosition);
            remove(firstPosition);


        }
    }
}
