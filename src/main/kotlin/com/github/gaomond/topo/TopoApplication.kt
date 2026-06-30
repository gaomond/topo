package com.github.gaomond.topo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TopoApplication

fun main(args: Array<String>) {
    runApplication<TopoApplication>(*args)
}
