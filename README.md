![Protean - evolving api's](/public/resource/img/logo.png?raw=true "Protean - evolving api's")

# About

Evolve RESTful API's. Encode them, simulate them, document how to use them, test them and figure out how failure affects your architecture. No invasive changes to your code base.

* Automatic documentation generated for all configured projects - map your services
* Customise API doc look and feel completely
* Simulate API's with a portable concise JSON like language
* Hotswap API behaviour on the fly over the network
* Simulate error response status codes per project or per resource path
* Configure probability of error per service or per resource path
* Verify request structure; headers, query string params, body payload json keys, url encoded forms
* Auto generate curl commands to test your API's

This is a Clojure project which uses edn to simulate and document RESTful API's. Protean is used commerically to help speed development and test complex distributed systems.


## Release information

* Latest development release is 0.9.0-alpha.2
    * [Code](https://github.com/passivsystems/protean/tree/0.9.0-alpha.2)
    * [Download](https://github.com/passivsystems/protean/releases/download/0.9.0-alpha.1/protean-0.9.0-alpha.2.tgz)


## API stability

Protean is still new and will be subject to some change until it hits the 1.0.0 release.  All efforts will be made to minimise change to the API (which is represented in the form of the *codex* - service definitions in EDN).  We expect to change the codex schema only to align it more closely to the datastructures used in Clojure Ring to represent requests and responses (so we share a common well understood language).  There are still a few minor discrepancies.


## Usage

### Overview

Protean helps you to evolve RESTful API's.  We define our API's in a codex which is an edn file with a .cod.edn extension.
There is a home for codex files which varies depending on how you installed the app.  Debian flavours of Linux use /usr/lib/protean,
while OSX uses ~/bin.

Protean ships with a sample petstore service codex, you can test the API docs creation and simulation capabilities with this.


## Documentation

Documentation is available on http://www.proteanic.org.  Below is a quickstart guide to help you with
setting up services and getting information on how to curl them.

### Creating API Documentation for a service

The following assumes a Debian Linux flavour install.

Create API documentation for the sample petstore service codex with

    protean doc -f /usr/lib/protean/sample-petstore.cod.edn

view your API docs with

    firefox /usr/lib/protean/silk_templates/site/index.html

### Starting the simulation server

Start the simulation server with

    protean-server

### How to query the petstore service

Lists all services:

    protean services

Shows the service configuration for the petstore service.

    protean service -n petstore

Shows the curl commands that can be used for the petstore service.

    protean service-usage -n petstore

Please explore the CLI or documentation to learn more.

### Setting up your services

Create a file with a .cod.edn extension. See *sample-petstore.cod.edn* at the root of this repository. Once you are finished you can add your service *codex* by;
* uploading with the basic Protean CLI
    - protean add-services -f /path/to/service.cod.edn
* drop the .cod.edn file in the root of your Protean directory and restart it


## Contributing

All contributions ideas/pull requests/bug reports are welcome, we hope you find it useful.


## License

Protean is licensed with Apache License v2.0.
