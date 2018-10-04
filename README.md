![Protean - evolving api's](/public/resource/img/logo.png?raw=true "Protean - evolving api's")

[![Build Status](https://api.travis-ci.org/passivsystems/protean.svg)](https://travis-ci.org/passivsystems/protean)

Latest release is 0.12.2

## Overview

Protean is a command line tool, written in Clojure, which is used commercially
to encode, document & simulate RESTful APIs.

## Key Features

* Encode - build a specification in a codex file (`*.cod.edn`)
* Document - generate customisable site docs from your codex (see [silk_templates](silk_templates) and [Silk Web Toolkit](http://www.silkyweb.org) for more details)
* Service Usage - generate Curl commands from your codex
* Simulate - create drop in replacements for existing or planned HTTP APIs (`*.sim.edn`)
* Customisable - override Protean's default simulation strategy via Clojure in a sim filez
* Auto reload - rebuild documents and sims on change

## Getting Started

Download and install [the latest release](https://github.com/passivsystems/protean/releases/latest) for you operating system.

For an overview of all Protean commands, and to test that your install went well, run this command in a terminal.

```bash
> protean
```

The examples mentioned below are shipped with Protean and can be run from any directory.

### The Codex File - RESTful API specification

See [examples/petstore-default/petstore.cod.edn](examples/petstore-default/petstore.cod.edn) for a well documented example of a codex file.

To generate documentation for this codex and to receive a command to open them in a browser, run.

```bash
> protean doc --file examples/petstore-default/petstore.cod.edn --host localhost --port 3000
```

Serve this codex, and any others in the same directory, by running Protean's simulation server.

```bash
> protean sim --directory examples/petstore-default/ --host localhost --port 3000
```

A list of Curl commands can be found by running (or in the documentation you generated).

```bash
> protean service-usage --name petstore
```

Interact with Protean's server using the supplied Curl commands and test out Protean's default simulation strategy by altering requests.

### The Sim File - override Protean's default simulation strategy

The default Protean simulation strategy:

* Validates requests - must satisfy codex and schema specifications (400 if not)
* Preserves parameter values - variables keep input values the same as output (`${petId}` in request path is same as body response)
* Stateless - HTTP actions such as `DELETE` does not change resource state (you can still `GET` it afterwards)

See [examples/petstore-sim/petstore.sim.edn](examples/petstore-sim/petstore.sim.edn) which for a well documented example of a sim file and learn how to make overrides that suits your scenario.

Stop Protean's simulation server with `Ctrl C` in the terminal it was started and point to the `petstore-sim` directory.

```bash
> protean sim --directory examples/petstore-sim/ --host localhost --port 3000
```

Also look at [examples/tutorial](examples/tutorial) and [examples/text-adventure](examples/text-adventure) for examples of sims working with state.

### Auto reload

When creating your own codex or simulation files, run Protean commands such as `doc` and `sim` with the `--reload` attribute to auto rebuild on file change.

```bash
> protean doc --reload --file path/to/project/my-codex.cod.edn --host localhost --port 3000
> protean sim --reload --directory path/to/project/ --host localhost --port 3000
```

These commands should be run in separate terminals as the auto reload proccess is kept alive till `enter` is struck in the terminal.

## Developers

Clone or fork the project. Install [Leiningen](https://leiningen.org) and substitue `protean` with `lein run` in the commands above.

Run Protean's integration tests before committing.

```bash
> lein run test
```
## Contributing

All contributions ideas/pull requests/bug reports are welcome, we hope you find it useful.

## License

Protean is licensed with Apache License v2.0.
