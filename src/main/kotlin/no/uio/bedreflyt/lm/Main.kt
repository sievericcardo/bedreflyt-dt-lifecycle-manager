package no.uio.bedreflyt.lm

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@ComponentScan(basePackages = ["no.uio.bedreflyt.lm.config"])
open class API

fun main(args: Array<String>) {
    SpringApplication.run(API::class.java, *args)
}