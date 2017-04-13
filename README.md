![Protean - evolving api's](/public/resource/img/logo.png?raw=true "Protean - evolving api's")

[![Build Status](https://api.travis-ci.org/passivsystems/protean.svg)](https://travis-ci.org/passivsystems/protean)


## Overview

Evolve your RESTful APIs and web services. Encode them, document them, simulate them, integration test them and figure out how failure affects your architecture. No invasive changes to your code base.  Browse HATEOAS APIs with [Omnom](https://github.com/rossputin/omnom)

## Features

* Encode APIs - build a specification
* Document APIs using [Silk Web Toolkit](http://www.silkyweb.org)
* Simulate APIs
* Hotswap API behaviour on the fly over the network
* Auto generate curl commands to test your API's
* Auto integration test your simulations

This is a Clojure project which uses edn to simulate and document and integration test RESTful API's. Protean is used commercially to help speed development and test complex distributed systems.


## Release information

* Latest development release is 0.10.0
    * [Code](https://github.com/passivsystems/protean/tree/0.10.0)
    * [Download](https://github.com/passivsystems/protean/releases/download/0.10.0/protean-0.10.0.tgz)


## API stability

Protean is still new and will be subject to some change until it hits the 1.0.0 release.  All efforts will be made to minimise change to the API (which is represented in the form of the *codex* - service definitions in EDN).  We expect to change the codex schema only to align it more closely to the datastructures used in Clojure Ring to represent requests and responses (so we share a common well understood language).  There are still a few minor discrepancies.


## Usage

### Getting Started

If you want to leap right in and see the examples working see [Getting Started](http://passivsystems.github.io/protean/getting-started.html).


### Tutorial

See the [Tutorial](http://passivsystems.github.io/protean/tutorial.html) for concepts and documentation.


## Contributing

All contributions ideas/pull requests/bug reports are welcome, we hope you find it useful.


## License

Protean is licensed with Apache License v2.0.
