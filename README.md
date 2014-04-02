# Protean

Mock RESTful API's, for all of your projects, hotswap API behaviour at run time.  Control it all with REST via curl or a command line interface.  Simulate errors.  Automatically generate curl commands to test your services.  All projects added get documentation generated in webapp form.

This is a Clojure project which uses edn to build simulated RESTful API projects. Protean is used commerically to help speed development and test complex distributed systems.


## Usage

    lein deps
    lein run

by default the admin area runs on 3001 and the main app area on 3000.

You can override the port with:

    lein run 4000 4001

Build a distributable with lein uberjar, then run with:

    java -jar protean.jar 4000 4001


## Documentation

Documentation is available on http://localhost:3001 when you run Protean locally. 

### How to setup your project

Create a file with a .edn extension. An example is shown below. Once you are finished your can add your project by;
* pushing your project with Protean CLI
    - protean-cli add-projects -f /path/to/project.edn
* pushing your project with Curl
    - curl -v -X PUT http://locahost:3001/projects --data-binary "@/path/to/project.end"
* drop the .edn file in the root of your Protean directory and restart it

An example RESTful API project configuration is listed below.

    {:myproject
      {:paths {"get/test/*" {:rsp {:body {"t1key" "t1val"}
                                   :errors {:status [504]
                                            :probability 50}}}        
               "get/xml" {:rsp {:content-type "text/xml"
                                :body [:parent
                                        [:child {:type "xml"}]]}}     
               "get/slow" {:rsp {:time 10}}                            
               "post/test" {:req {:method :post                       
                                  :form {"k1" "v1"}}
                            :rsp {:headers {"Location" "7"}}}
               "put/test1" {:req {:body {"k1" "v1" "k2" "v2"}}        
                            :rsp {:status 200}}
               "put/test2" {}
               "random/test2" {:req {:headers {"X-Auth" "XYZ"}        
                                     :req-params {"blurb" "flibble"}} 
                               :rsp {:body {"t2key" "t2val"}}}}
       :errors {:status [500 503] :probability 25}}}                  


This demonstrates:

* [get/test] GET with response body, wildcard match and resource path errors and error probability
* [get/xml] GET with XML response body
* [get/slow] GET with slow response specified (10 seconds)
* [post/test] POST with response headers and verification of request method and url encoded form payload
* [put/test1] PUT with overriden response status and request body json payload verification
* [put/test2] PUT simplest example of a path, 200 response code
* [random/test2] verification of request headers and verification of request query string parameters
* [errors] project level simulated response errors with a configurable probability


### How to query your project

    protean-cli projects 
    
Lists all projects.

    protean-cli project -n myproject 

Shows the project configuration for myproject.

    protean-cli project-usage -n myproject 
    
Shows the curl commands that can be used for myproject.

Please explore the CLI to find out further commands.


## Contributing

All contributions ideas/pull requests/bug reports are welcome, we hope you find it useful. 



## License

Protean is licensed with Apache License v2.0.
