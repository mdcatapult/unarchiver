# Document Library Unarchive consumer

A document library specific consumer to extract files and push the resulting files back into the document library

## Execution

This is a scala application that runs in side the JVM

```bash
java $JAVA_OPTS -jar consumer.jar start --config
```

## Runtime Configuration

The app allows runtime configuration via environment variables. 

It also allows for a --config flag to allow injection of common configs that will override the default config and environment variable driven config

* **MONGO_USERNAME** - login username for mongodb
* **MONGO_PASSWORD** - login password for mongodb
* **MONGO_HOST** - host to connect to
* **MONGO_PORT** - optional: port to connect to (default: 27017) 
* **MONGO_DOCLIB_DATABASE** - database to connect to
* **MONGO_AUTHSOURCE** - optional: database to authenticate against (default: admin)
* **MONGO_DOCUMENTS_COLLECTION** - default collection to read and write to
* **RABBITMQ_USERNAME** - login username for rabbitmq
* **RABBITMQ_PASSWORD** - login password for rabbitmq
* **RABBITMQ_HOST** - host to connect to
* **RABBITMQ_PORT** - optional: port to connect to (default: 5672)
* **RABBITMQ_VHOST** - optional: vhost to connect to (default: /)
* **RABBITMQ_EXCHANGE** - optional: exchange that the consumer should be bound to
* **CONSUMER_QUEUE** - optional: name of the queue to consume (default: unarchive)
* **UPSTREAM_CONCURRENCY** - optional: number of messages to handle concurrently (default: 1)
* **DOWNSTREAM_QUEUE** - optional: name of queue to enqueue new files to (default: prefetch)

