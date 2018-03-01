package org.jetbrains.research.kfg.ir.value

interface Usable<T> {
    fun addUser(user: User<T>)
    fun removeUser(user: User<T>)
    fun getUsers(): List<User<T>>
    fun replaceAllUsesWith(to: T)
}

interface User<T> {
    fun replaceUsesOf(from: T, to: T)
}