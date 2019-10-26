package info.kinterest.datastores.tests.containers

import org.testcontainers.containers.GenericContainer

class KGenericContainer(image: String) : GenericContainer<KGenericContainer>(image)