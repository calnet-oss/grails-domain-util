package edu.berkeley.util.domain

interface IncludesExcludesInterface {
    // fields to exclude
    List<String> getExcludes()

    // fields to include
    List<String> getIncludes()
}
