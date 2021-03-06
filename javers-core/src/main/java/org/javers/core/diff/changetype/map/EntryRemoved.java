package org.javers.core.diff.changetype.map;

import org.javers.common.string.PrettyValuePrinter;

import static org.javers.common.string.ToStringBuilder.format;

/**
 * Entry removed from a Map
 *
 * @author bartosz walacik
 */
public class EntryRemoved extends EntryAddOrRemove {

    public EntryRemoved(Object key, Object value) {
        super(key, value);
    }

    @Override
    public String toString() {
        return prettyPrint(PrettyValuePrinter.getDefault());
    }

    @Override
    protected String prettyPrint(PrettyValuePrinter valuePrinter) {
        return valuePrinter.formatWithQuotes(getKey()) + " -> " +
                valuePrinter.formatWithQuotes(getValue()) + " removed";
    }
}
