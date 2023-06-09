# RestEasyScala
The simplest REST client imaginable, in Scala!

Looking through options for REST clients in Scala I found many that were way over kill for what I needed. I wrapped one of them in a super simple `String => Try[String]` interface. My suggestion would be to pick your favorite data encoder/decoder, I like [Argonaut](https://github.com/argonaut-io/argonaut) for JSON, and add a layer on top of this.

### Usage
#### Run your own client
```scala
  val client = RestClient.defaultClient
  client.get(...)
  client.terminate()
```

 
#### Run a client which cleans itself up
```scala
  RestClient.withDefaultClient { client => 
    val getResult = client.get(...)
    val postResult = client.post(...)
  }
```

 
#### Create a client per call, cleaning up after each 
```scala
  RestClient.get(...)
```
