# Document Library Unarchive consumer

A document library specific consumer to extract files and push the resulting files back into the document library

## Execution

This is a scala application that runs in side the JVM

```bash
java -jar consumer-unarchive.jar
```

## Runtime Configuration

The app allows runtime configuration via environment variables

* **MONGO_USERNAME** - login username for mongodb
* **MONGO_PASSWORD** - login password for mongodb
* **MONGO_HOST** - host to connect to
* **MONGO_PORT** - optional: port to connect to (default: 27017) 
* **MONGO_DATABASE** - database to connect to
* **MONGO_AUTH_DB** - optional: database to authenticate against (default: admin)
* **MONGO_COLLECTION** - default collection to read and write to
* **RABBITMQ_USERNAME** - login username for rabbitmq
* **RABBITMQ_PASSWORD** - login password for rabbitmq
* **RABBITMQ_HOST** - host to connect to
* **RABBITMQ_PORT** - optional: port to connect to (default: 5672)
* **RABBITMQ_VHOST** - optional: vhost to connect to (default: /)
* **RABBITMQ_EXCHANGE** - optional: exchange that the consumer should be bound to
* **UPSTREAM_QUEUE** - optional: name of the queue to consume (default: klein.unarchive)
* **UPSTREAM_CONCURRENT** - optional: number of messages to handle concurrently (default: 1)
* **DOWNSTREAM_QUEUE** - optional: name of queue to enqueue new files to (default: klein.prefetch)
* **AWS_ACCESS_KEY_ID** - optional: AWS access key for use when not run withing AWS 
* **AWS_SECRET_ACCESS_KEY** - optional: AWS secret key for use when not run withing AWS

## Results

The results of the consumer will provide an array of all extracted  files which will be stored in the property 
`unarchived` along with a flag at `klein.unarchive`
