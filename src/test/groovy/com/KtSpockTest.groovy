package com


import org.slf4j.LoggerFactory
import spock.lang.Specification

class KtSpockSpec extends Specification {

    def log = LoggerFactory.getLogger({}.getClass())

    Mother mother = Mock()
    def kid = new Kid(mother)

    def "test"() {

        when:
        def result = kid.wantMoney()

        then:
        mother.giveMoney(100) >> { Integer m ->
            println("-------")
            m
        }
        result == 101 - 1

    }

    def "test2"() {
        when:
        def res = 1 + 1
        then:
        res == 2
    }


}
