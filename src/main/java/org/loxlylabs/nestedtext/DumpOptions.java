package org.loxlylabs.nestedtext;

/**
 * Options to use when dumping an Object to Minimal NestedText
 *
 * @param eol   the line separator string to use when dumping data.
 * @param indentAmount the number of spaces for indentation (must be positive).
 */
public record DumpOptions(String eol, int indentAmount) {}
