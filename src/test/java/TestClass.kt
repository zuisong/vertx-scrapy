package com

open
class Mother {
    fun giveMoney(money: Int): Int {
        return money
    }
}

open
class Kid(private val mother: Mother) {
    var money = 0
        private set

    fun wantMoney(): Int {
        money += mother.giveMoney(100)
        return money
    }
}
