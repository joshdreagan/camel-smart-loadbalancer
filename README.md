# Camel Smart LoadBalancer

This example shows how you might create a custom Camel `org.apache.camel.processor.loadbalancer.LoadBalancer` implementation to intelligently route messages based on system load.

## Requirements

- [Apache Maven 3.x](http://maven.apache.org)

## Building Example

Run the Maven build

```
~$ cd $PROJECT_ROOT
~$ mvn clean install
```

## Running Camel

Open up four terminal windows.

Terminal 1:

```
~$ cd $PROJECT_ROOT
~$ mvn -P gateway camel:run
```

Terminal 2:

```
~$ cd $PROJECT_ROOT
~$ mvn -P backend1 camel:run
```

Terminal 3:

```
~$ cd $PROJECT_ROOT
~$ mvn -P backend2 camel:run
```

Terminal 4:

```
~$ cd $PROJECT_ROOT
~$ mvn -P backend3 camel:run
```

Testing
-------

Using any REST client, send a GET HTTP request to http://localhost:9000/gateway/GreeterService.

_Note: The current configuration polls the `ProcessCpuLoad` attribute. For a real test, you'll want to modify the `applicationContext.xml` to make the monitors poll the `SystemCpuLoad` attribute instead. Also, you'll want to run the backend instances on separate machines. If you do so, don't forget to update the hosts and ports as well._
