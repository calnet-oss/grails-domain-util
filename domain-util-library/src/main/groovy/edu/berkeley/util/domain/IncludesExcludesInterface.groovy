package edu.berkeley.util.domain

interface IncludesExcludesInterface {
    // fields to exclude
    List<String> getExcludes()

    // fields to include
    List<String> getIncludes()
    
    // if true, include null values in converter output
    Boolean getIncludeNulls()
}
