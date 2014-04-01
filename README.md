# Protean

Mock RESTful API's, for all of your projects, swap behaviour out real time.  Control it all with REST via curl.  Simulate errors.  Automatically generate curl commands to test your services.  All projects added get documentation generated in webapp form.

This is a Clojure project which uses edn to build simulated RESTful API projects.

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

An example RESTful API project configuration is list below.

    {:sample
      {:paths {"get/test/*" {:rsp {:body {"t1key" "t1val"}
                                   :errors {:status [504]
                                            :probability 50}}}        [1]
               "get/xml" {:rsp {:content-type "text/xml"
                                :body [:parent
                                        [:child {:type "xml"}]]}}     [2]
               "get/slow" {:rsp {:time 10}}                           [3] 
               "post/test" {:req {:method :post                       [4]
                                  :form {"k1" "v1"}}
                            :rsp {:headers {"Location" "7"}}}
               "put/test1" {:req {:body {"k1" "v1" "k2" "v2"}}        [5]
                            :rsp {:status 200}}
               "put/test2" {}
               "random/test2" {:req {:headers {"X-Auth" "XYZ"}        [6]
                                     :req-params {"blurb" "flibble"}} [7]
                               :rsp {:body {"t2key" "t2val"}}}}
       :errors {:status [500 503] :probability 25}}}                  [8]


This demonstrates:

* [1] GET with response body, wildcard match and resource path errors and error probability
* [2] GET with XML response body
* [3] GET with slow response specified (10 seconds)
* [4] POST with response headers and verification of request method and url encoded form payload
* [5] PUT with overriden response status and request body json payload verification
* [6] verification of request headers
* [7] verification of request query string parameters
* [8] project level simulated response errors with a configurable probability


## Contributing

All contributions ideas/pull requests/bug reports are welcome, we hope you find it useful. 



## License

Protean is licensed with Apache License v2.0.
