# Protean

Take control of your RESTful API's. Test them, simulate them, document how to use them and figure out how failure affects your architecture. No invasive changes to your code base. Swap API behaviour out real time over the network. 

* Simulate API's with a portable concise JSON like language
* Hotswap API behaviour on the fly over the network
* Simulate error response status codes per project or per resource path
* Configure probability of error per service or per resource path
* Verify request structure; headers, query string params, body payload json keys, url encoded forms
* Auto generate curl commands to test your API's
* Automatically integration test your API simulation
* Automatically integration test your real API's passing in a small initial seed of data
* Automatic documentation generated for all configured projects - map your services

This is a Clojure project which uses edn to simulate, integration test and document RESTful API's. Protean is used commerically to help speed development and test complex distributed systems.


## Usage

    lein deps
    lein run

by default the admin area runs on 3001 and the main app area on 3000.

You can override the port with:

    lein run 4000 4001

Build a distributable with lein uberjar, then run with:

    java -jar protean.jar 4000 4001


## Documentation

Documentation is available on http://localhost:3001 when you run Protean locally.  Below is a quickstart guide.

### Setting up your services

Create a file with a .edn extension. See *sample-codex.edn* at the root of this repository. Once you are finished your can add your service *codex* by;
* uploading with the basic Protean CLI
    - protean-cli add-projects -f /path/to/project.edn
* uploading with curl
    - curl -v -X PUT http://locahost:3001/projects --data-binary "@/path/to/project.edn"
* drop the .edn file in the root of your Protean directory and restart it


### How to query your service

Lists all services:

    protean-cli projects 
    
Shows the service configuration for myproject.

    protean-cli project -n myproject 

Shows the curl commands that can be used for myproject.

    protean-cli project-usage -n myproject 

Please explore the CLI or documentation to learn more.


## Contributing

All contributions ideas/pull requests/bug reports are welcome, we hope you find it useful. 



## License

Protean is licensed with Apache License v2.0.
