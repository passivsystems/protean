![Protean - evolving api's](/public/resource/img/logo.png?raw=true "Protean - evolving api's")

[![Build Status](https://api.travis-ci.org/passivsystems/protean.svg)](https://travis-ci.org/passivsystems/protean)


## Overview

Evolve your RESTful APIs and web services. Encode them, document them, simulate them, integration test them and figure out how failure affects your architecture. No invasive changes to your code base.

## Features

* Encode APIs - build a specification
* Document APIs using [Silk Web Toolkit](http://www.silkyweb.org)
* Simulate APIs
* Hotswap API behaviour on the fly over the network
* Auto generate curl commands to test your API's
* Auto integration test your simulations
* Browse HATEOAS APIs with [Omnom](https://github.com/rossputin/omnom) the Eater of APIs (pre-alpha)

This is a Clojure project which uses edn to simulate and document and integration test RESTful API's. Protean is used commercially to help speed development and test complex distributed systems.


## Release information

* Latest development release is 0.12.0
    * [Code](https://github.com/passivsystems/protean/tree/0.12.0)
    * [Download](https://github.com/passivsystems/protean/releases/download/0.12.0/protean-0.12.0.tgz)
    * [Documentation](http://passivsystems.github.io/protean/)


## API stability

Protean is still new and will be subject to some change until it hits the 1.0.0 release.  All efforts will be made to minimise change to the API (which is represented in the form of the *codex* - service definitions in EDN) and in the simulation API which is exposed to sim extensions.


## Usage

### Quick Start

For those who want to get stuck in right now play with our sample Docker image.

Install Docker for your platform.

Start our sample Protean server:

```
docker run -it -p 3000:3000 -p 3001:3001 rossputin/protean-example
```

SSH into the Protean Docker image:

```
docker run -it rossputin/protean-example /bin/bash
```

Navigate to the /home directory where all the software is installed:

```
cd /home
```

Run the Protean client to learn how to interact with the sample Petstore service:

```
./protean -H 172.17.0.1 service-usage -n petstore
```

Use any of the curl commands or connect another way remembering to substitute the '172.17.0.1' host

Read on below to learn more.


### Getting Started

If you want to leap right in and see the examples working see [Getting Started](http://passivsystems.github.io/protean/getting-started.html).

### Documentation

For an explanation on concepts and general documentation see [Documentation](http://passivsystems.github.io/protean/documentation.html).


### Tutorial

See the [Tutorial](http://passivsystems.github.io/protean/tutorial.html) for a progressive howto guide.


### API Documentation

For the latest version of the API documentation see [API Documentation](http://passivsystems.github.io/protean/api-documentation.html).


## Developers

### Getting Started

#### Running the server

In the root directory run Protean server with:

```
lein run -m protean.server.main
```

#### Running the client

To learn how to interact with the sample Petstore API in the root directory in another terminal run:

```
lein run service-usage -n petstore
```

Now enter one of the curl commands listed to interact with the API.


### Building and distributing

See the instructions in build/README.md.


## Contributing

All contributions ideas/pull requests/bug reports are welcome, we hope you find it useful.


## License

Protean is licensed with Apache License v2.0.
