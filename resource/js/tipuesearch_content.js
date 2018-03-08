var tipuesearch={"pages":[{"title":"API Documentation \/ Simulation Request API","text":" Request API Extracting information out of simulation requests (query-param &quot;my-param&quot;) Get a specified query parameter out of the sim request Parameter Description Type p specific parameter String (path-param &quot;my-param&quot;) Get a specified path parameter out of the sim request Parameter Description Type p specific parameter String (param :my-param) Get a specified parameter out of the sim request Parameter Description Type p specific parameter keyword (form-param &quot;my-param&quot;) Get a specified form parameter out of the sim request Parameter Description Type p specific parameter String (body-param &quot;my-param&quot;) Get a specified body parameter out of the sim request Parameter Description Type p specific parameter String (route-param route-params) Get the last route parameter out of the sim request Parameter Description Type route-params route params datastructure map (header &quot;my-header&quot;) Get a specified header out of the sim request Parameter Description Type h specific header String (valid-inputs?) Check if request is well formed according to codex specification. Used with an if or when form. (validate (respond 200)) Same sa valid-inputs? but creates a 400 error where validation fails. Parameter Description Type response a bespoke sim ext response structure Composite response form ","tags":"","loc":"api-documentation.html#sim-request"},{"title":"API Documentation \/ Simulation Response API","text":" Response API Forming synchronous simulation responses The most flexible (and low level) way to create responses in a sim extension is to create Ring responses. An example is listed below. { :status 200 :headers {&quot;Content-Type&quot; &quot;application\/json&quot;} :body &quot;json string&quot; } Or an example of reading a file to specify the body content: { :status 200 :headers {&quot;Content-Type&quot; &quot;application\/json&quot;} :body (qslurp &quot;path\/file.json&quot;) } (success) Gets a random success response for this endpoint from the codex (error) Gets a random error response for this endpoint from the codex (error 500) Gets a specified error response for this endpoint from the codex Parameter Description Type status specific error status code Int (respond 200) Create a response with a specific status header Parameter Description Type status specific status code Int (respond 200 :body-url &quot;my-file.json&quot;) Create a response with a specific status header and body. Content type is inferred from file extension. Parameter Description Type status specific status code Int body-url path to file String (qslurp &quot;my-file.json&quot;) Read a supporting resource, converting relative path to absolute. Parameter Description Type path path to resource (passed as relative in sim ext). String ","tags":"","loc":"api-documentation.html#sim-response"},{"title":"API Documentation \/ Simulation Payload Transport API","text":" Payload Transport API Sending synchronous and asynchronous payloads over different protocols. We often need to send route data to different destinations synchronously and asynhronously as a result of receiving a request. We provide one low level API mechanism for doing this and several abstractions over this in the sim library. (make-request :put &quot;http:\/\/host:port\/path&quot; {:content-type &quot;application\/json&quot; :body &quot;something&quot;}) Sends a specified payload with a specified method to a given url Parameter Description Type method HTTP method keyword url fully qualified url String payload datastructure defining headers etc map ","tags":"","loc":"api-documentation.html#sim-payload-transport"},{"title":"API Documentation \/ Response Library","text":" Response Example Library Forming synchronous simulation responses (rsp 200) Create a response with a specific status code. Parameter Description Type status specific status code Int (h-rsp 200 &quot;\/items\/12&quot;) Create a response with a specific status code and location header. Parameter Description Type status specific status code Int hdr location header String (jsn 200 &quot;my response string&quot;) Create a response with a specific status header, &quot;application\/json&quot; content type and body Parameter Description Type status specific status code Int body body content String (txt 200 &quot;my response string&quot;) Create a response with a specific status header, &quot;text\/plain&quot; content type and body Parameter Description Type status specific status code Int body body content String ","tags":"","loc":"api-documentation.html#library-response"},{"title":"API Documentation \/ Payload Transport Library","text":" Payload Transport Example Library (post &quot;http:\/\/host:port\/path&quot; &quot;something&quot;) Posts a specified payload to a given uri Assumes content type is the same as requests content type. Optional headers parameter. Parameter Description Type url fully qualified url String hdrs optional headers map payload datastructure defining headers etc map (put &quot;http:\/\/host:port\/path&quot; &quot;something&quot;) Puts a specified payload to a given uri Assumes content type is the same as requests content type. Optional headers parameter. Parameter Description Type url fully qualified url String hdrs optional headers map payload datastructure defining headers etc map (patch &quot;http:\/\/host:port\/path&quot; &quot;something&quot;) Patches a specified payload to a given uri Assumes content type is the same as requests content type. Optional headers parameter. Parameter Description Type url fully qualified url String hdrs optional headers map payload datastructure defining headers etc map ","tags":"","loc":"api-documentation.html#lib-payload-transport"},{"title":"Documentation \/ Overview","text":" Overview What does Protean do ? Protean is a Clojure project which supports the design and evolution of RESTful APIs. It lets you: Specify an API Generate API documentation from the specification Run a simulation of an API - derived from the specification Run an integration test on an API - derived from the specification Hotswap API simulation behaviour on the fly over the network Autogenerate curl commands to interact with your API ","tags":"","loc":"documentation.html#overview"},{"title":"Documentation \/ Concepts","text":" Concepts How does Protean work ? Protean includes a Server and a Client. Building an API specification Protean uses EDN to build an API specification. We call the specification a Codex. A Codex is an EDN file with a .cod.edn extension. Protean ships with a sample Pet Store Codex and several more full featured examples and tutorials. Generating API documentation As soon as we have a Codex we can generate API documentation. The Protean Client uses Silk Web Toolkit to build the documentation for your API. We can customise the API documentation by making changes to a directory called silk_templates which is in the same location as the Codex. Running a simulation of your API Once we have a Codex we can run an out of the box simulation derived from it using the Protean Server. Drop the Codex into place and start the server up. We can customise the simulation by creating a Sim Extension. Place the Sim Extension in the same location as the Codex. Protean provides a comprehensive API and the full power of Clojure to let you customise your API simulation any way you want. A Sim Extension is an EDN file with a .sim.edn extension. ","tags":"","loc":"documentation.html#concepts"},{"title":"Documentation \/ Commands","text":" Commands A list of commands Server protean-server - start the Protean Server Client protean doc -f \/path\/to\/a-codex.cod.edn - generate API documentation protean services - list all the APIs (services) protean service -n an-api - show API (service) configuration protean service-usage -n an-api - show curl commands for interacting with the API ","tags":"","loc":"documentation.html#commands"},{"title":"Documentation \/ Environment Variables","text":" Environment Variables Name Description Default PROTEAN_SIM_PORT the port for hitting sim endpoints 3000 PROTEAN_ADMIN_PORT the port for managing sims 3001 PROTEAN_CODEX_DIR the location for codex specifications N\/A PROTEAN_LOG_LEVEL log level info ","tags":"","loc":"documentation.html#environment-variables"},{"title":"Getting Started \/ Installing","text":" Installing Docker If you would like to try Protean out and are a Docker fan : Docker Hub Linux There is a tried and tested .deb for Debian\/Ubuntu flavours of Linux at : https:\/\/github.com\/passivsystems\/protean\/releases\/download\/0.12.0\/protean_0.12.0_all.deb Mac OSX For OSX we have a brogrammer script, trust us, it is safe :-) This will install to ~\/bin and in a very impolite way spew a couple of files out in there. In a terminal run bash &lt;(curl -fksSL https:\/\/raw.githubusercontent.com\/passivsystems\/protean\/develop\/install\/install.sh) ","tags":"","loc":"getting-started.html#installing"},{"title":"Getting Started \/ Sample API - The Pet Store","text":" Sample API - The Pet Store Protean ships with a sample service\/API - The Pet Store. The instructions below will describe how to run it and interact with it. ","tags":"","loc":"getting-started.html#sample"},{"title":"Getting Started \/ API Documentation","text":" API Documentation Generation In a terminal run protean doc -f \/protean\/home\/path\/petstore.cod.edn. The output in the terminal will tell you how to open your docs. ","tags":"","loc":"getting-started.html#apidocs"},{"title":"Getting Started \/ Simulation","text":" Simulation In a terminal run protean-server. This will start the sim server. You will see some output when you run this startup command, including information on where service\/API definitions live. Protean ships with a sample 'petstore' service for you to experiment with. See sample-petstore.cod.edn in the 'codex' directory which is listed in the output when you run the startup command. You can use a command line interface CLI to play with Protean. In a terminal run protean to get some help with things. Run protean services to list all services. Run protean service-usage -n petstore to get a list of curl commands you can hit the service with. ","tags":"","loc":"getting-started.html#simulation"},{"title":"Getting Started \/ Integration Testing","text":" Integration Testing It is possible to integration test your simulations in a fairly simplistic manner. You do not need to write any code to do this. In a terminal run protean test -f \/path\/to\/myservice.cod.edn. ","tags":"","loc":"getting-started.html#integration-testing"},{"title":"index \/ Overview","text":" Overview Evolve your RESTful APIs and web services. Encode them, document them, simulate them, integration test them and figure out how failure affects your architecture. No invasive changes to your code base. This is a Clojure project which uses edn to simulate and document and integration test RESTful API's. Protean is used commercially to help speed development and test complex distributed systems. ","tags":"","loc":"index.html#overview"},{"title":"index \/ Features","text":" Features Encode APIs - build a specification Document APIs using Silk Web Toolkit Simulate API's Hotswap API behaviour on the fly over the network Auto generate curl commands to test your APIs Auto integration test your simulations Browse HATEOAS APIs with Omnom the Eater of APIs (pre-alpha) ","tags":"","loc":"index.html#features"},{"title":"index \/ Examples","text":" Examples Protean ships with several examples out of the box, see the public\/examples directory. These are intended to supplement the information in the tutorial. Single and multiplayer text adventure loosely based on a well known cult classic comedy film Petstore demo with examples of failure response and general sim api capabilities ","tags":"","loc":"index.html#examples"},{"title":"Tutorial \/ Overview","text":" Overview A progressive tutorial This is a progressive tutorial designed to gradually demonstrate the main features of Protean. It is aimed to be fun so each part will follow on from the previous, the aim being to build an online text adventure game driven by a RESTful API for exploring it, and building it. ","tags":"","loc":"tutorial.html#tut-overview"},{"title":"Tutorial \/ Part 1 - encoding (basics)","text":" Part 1 - Encoding The basics Protean encodes RESTful API's using a codex. A codex is pretty much just some Clojure code in a file with an extension cod.edn. The codex is the single point of truth. Nothing leaks from Protean into your source code... no annotations, no pollution of any kind. { :title &quot;Tutorial 1&quot; :doc &quot;Demonstrates the simplest possibe codex structure&quot; &quot;tutorial-1&quot; {&quot;play&quot; {:get {:rsp {:204 {}}}}} } Explanation This is just about the simplest codex. Out of this we get : an endpoint tutorial-1\/play a get method a nice title for the api docs Run it We will start with creating the apidocs for the service defined in this tutorial. copy tutorial-1.cod.edn from public\/tutorial to your codex directory enter protean doc -f tutorial-1.cod.edn open the index.html file created in silk_templates\/site ","tags":"","loc":"tutorial.html#tut-part-1"},{"title":"Tutorial \/ Part 2 - encoding (docs\/types)","text":" Part 2 - Encoding Documentation and types { :includes [&quot;defaults.edn&quot;] :title &quot;Tutorial 2&quot; &quot;tutorial-2&quot; { &quot;play\/${stateId}&quot; { :get { :doc &quot; A single player REST adventure world A simple text adventure world for one player. Sample usage may be something like `\/tutorial-2\/play\/cave`, indicating the player is in a cave. &quot; :vars { &quot;stateId&quot; {:type :Int :doc &quot;ID for the state of the game&quot;} } :rsp { :204 {} :404 {} } } } } } Explanation This adds a few things to the previous codex definition : a path parameter stateId a simple doc string some free preferences for response status codes and types in 'defaults.edn' some variable information defining the type of the path parameter a 404 (Not Found) error response Run it Create your apidocs again. copy tutorial-2.cod.edn from public\/tutorial to your codex directory enter protean doc -f tutorial-2.cod.edn open the index.html file created in silk_templates\/site ","tags":"","loc":"tutorial.html#tut-part-2"},{"title":"Tutorial \/ Part 3 - encoding (response bodies)","text":" Part 3 - Encoding Response bodies { :includes [&quot;defaults.edn&quot;] :title &quot;Tutorial 3&quot; &quot;tutorial-3&quot; { &quot;play\/${stateId}&quot; { :get { :doc &quot; A single player REST adventure world A simple text adventure world for one player. Sample usage may be something like `\/tutorial-3\/play\/cave`, indicating the player is in a cave. &quot; :vars { &quot;stateId&quot; {:type :Int :doc &quot;ID for the state of the game&quot;} } :rsp { :200 { :body-examples [&quot;public\/tutorial\/3\/200-ref.json&quot;] } :503 { :headers {&quot;Content-Type&quot; &quot;application\/problem+json&quot; &quot;Content-Language&quot; &quot;en&quot;} :body-examples [&quot;public\/tutorial\/3\/lazy-server-gremlins.json&quot;] } } } } } } Explanation We now add some new concepts to the previous codex definition : a response body for a 200 (OK) response swap the 404 response for a 503 and add a response header Where it is possible for a response to have a body it is good practice to include a reference example in the codex. It is entirely possible that an error response, like the 503 defined in this example could have a completely different body Run it Create your apidocs again. copy tutorial-3.cod.edn from public\/tutorial to your codex directory enter protean doc -f tutorial-3.cod.edn open the index.html file created in silk_templates\/site ","tags":"","loc":"tutorial.html#tut-part-3"},{"title":"Tutorial \/ Part 4 - simulation (basics)","text":" Part 4 - Simulation The basics Explanation You do not need to do anything to get a basic simulation for free. It has always been available. Run it Run the simulation, using the tutorial-3 codex which you have already copied into your codex directory. run protean-server run protean services run protean service-usage -n tutorial-3 select the curl statement generated as output of the previous step and execute it You should see output like : { &quot;description&quot;: &quot;You are in a cave, it is very dark&quot; } ","tags":"","loc":"tutorial.html#tut-part-4"},{"title":"Tutorial \/ Part 5 - simulation (errors)","text":" Part 5 - Simulation Simulating errors To override the default simulation behaviour (which returns success responses) create a sim extension file. We will re-use the codex from tutorial-3 (renaming it to tutorial-5) and place the new sim extension in the same location. The renamed codex is listed below. { :includes [&quot;defaults.edn&quot;] :title &quot;Tutorial 5&quot; &quot;tutorial-5&quot; { &quot;play\/${stateId}&quot; { :get { :doc &quot; A single player REST adventure world A simple text adventure world for one player. Sample usage may be something like `\/tutorial-5\/play\/cave`, indicating the player is in a cave. &quot; :vars { &quot;stateId&quot; {:type :Int :doc &quot;ID for the state of the game&quot;} } :rsp { :200 { :body-examples [&quot;public\/tutorial\/3\/200-ref.json&quot;] } :503 { :headers {&quot;Content-Type&quot; &quot;application\/problem+json&quot; &quot;Content-Language&quot; &quot;en&quot;} :body-examples [&quot;public\/tutorial\/3\/lazy-server-gremlins.json&quot;] } } } } } } Now we list our first sim extension. (refer 'protean.api.transformation.sim) { &quot;tutorial-5&quot; { &quot;play\/${stateId}&quot; { :get [#(error)] } } } Explanation In our first sim extension example we override the get method to return a random error status code, which in this case must be the 503 we defined in the codex. As you can see the general structure of the sim matches that of the codex. Run it Run the simulation. copy tutorial-5.cod.edn and tutorial-5.sim.edn from public\/tutorial to your codex directory run protean-server run protean services run protean service-usage -n tutorial-5 select the curl statement generated as output of the previous step and execute it You should see output like : { &quot;type&quot;: &quot;http:\/\/proteanic.org\/api\/problems\/examples\/lazy-server-gremlins&quot;, &quot;title&quot;: &quot;The service is unavailable - lazy server gremlins.&quot;, &quot;detail&quot;: &quot;You have asked for something, but the service is unavailable, the server gremlins cannot be bothered to do any work right now.&quot;, &quot;instance&quot;: &quot;http:\/\/host.port\/tutorial-3\/play&quot; } ","tags":"","loc":"tutorial.html#tut-part-5"},{"title":"Tutorial \/ Part 6 - simulation (responses)","text":" Part 6 - Simulation Simulating dynamic responses { :includes [&quot;defaults.edn&quot;] :title &quot;Tutorial 6&quot; &quot;tutorial-6&quot; { &quot;play\/${stateId}&quot; { :types { :StateId &quot;(cave|forest)&quot; } :get { :doc &quot; A single player REST adventure world A simple text adventure world for one player. Sample usage may be something like `\/tutorial-6\/play\/cave`, indicating the player is in a cave. &quot; :vars { &quot;stateId&quot; {:type :StateId :doc &quot;ID for the state of the game&quot;} } :rsp { :200 { :body-examples [&quot;public\/tutorial\/3\/200-ref.json&quot;] } :404 {} } } } } } Now the sim extension. (refer 'protean.api.transformation.sim) (defn param2rsp [data-path] (if-let [rsp (rsp-body-file data-path (path-param &quot;stateId&quot;) &quot;.json&quot;)] (slurp rsp) (respond 404))) { &quot;tutorial-6&quot; { &quot;play\/${stateId}&quot; { :get [#(param2rsp &quot;public\/tutorial\/6&quot;)] } } } Explanation In the codex above we have converged on a slightly more realistic example. Our endpoint includes a path parameter, and we are now provisioning for cases where the resource requested does not exist. We have created a custom type for our path parameter, indicating that its value can be either 'cave' or 'forest'. In our sim extension we now look up a response body based on the value of the path parameter. We provide two nonsense responses in json files. Run it Run the simulation. copy tutorial-6.cod.edn and tutorial-6.sim.edn from public\/tutorial to your codex directory run protean-server run protean services run protean service-usage -n tutorial-6 select the curl statement generated as output of the previous step, ensure the path parameter is either cave or forest and execute it You should see output like : { &quot;description&quot;: &quot;You are in a cave, it is very dark&quot; } ","tags":"","loc":"tutorial.html#tut-part-6"}]};var tipuedrop=tipuesearch;var tipuesearch_stop_words = ["and", "be", "by", "do", "for", "he", "how", "if", "is", "it", "my", "not", "of", "or", "the", "to", "up", "what", "when"];var tipuesearch_replace = {"words": []};var tipuesearch_stem = {"words": []};