package edu.berkeley.util.domain

import spock.lang.Specification

/**
 * Does nothing.  Here to make Bamboo happy.  All the real tests are in the
 * domain-util-tests subproject.
 */
class NoOpSpec extends Specification
{
    void "tests nothing"() {
        when:
            true
        then:
            true
    }
}
