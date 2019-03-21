<p align="center">
  <img src="/public/resource/img/logo.png?raw=true" alt="Protean - evolving api's" />
</p>
<p align="center">
  <a href="https://travis-ci.org/passivsystems/protean" alt="Build Status">
    <img src="https://api.travis-ci.org/passivsystems/protean.svg" /></a>
  </a>
  <a href="https://github.com/passivsystems/protean-api/releases/latest" alt="Latest Release">
    <img src="https://img.shields.io/github/downloads/passivsystems/protean/0.12.1/total.svg" /></a>
  </a>
</p>

Protean is a command line tool, written in Clojure, which is used commercially
to encode, document & simulate RESTful APIs.

## Key Features

* Encode - build a specification in a codex file (`*.cod.edn`)
* Document - generate customisable site docs from your codex (see [silk_templates](silk_templates) and [Silk Web Toolkit](http://www.silkyweb.org) for more details)
* Service Usage - generate Curl commands from your codex
* Simulate - create drop in replacements for existing or planned HTTP APIs
* Customisable - override the default simulation strategy via [Protean API](https://github.com/passivsystems/protean-api) in a sim file (`*.sim.edn`)
* Auto reload - rebuild documents and sims on change

## Installation

### On [Debian-based](https://en.wikipedia.org/wiki/Category:Debian-based_distributions) Linux distributions E.G. Ubuntu

Download [protean_0.14.0-SNAPSHOT_all.deb](https://github.com/passivsystems/protean/releases/download/0.14.0-SNAPSHOT/protean_0.14.0-SNAPSHOT_all.deb) and double click on the file or run.

``` bash
sudo dpkg -i ~/Downloads/protean_0.14.0-SNAPSHOT_all.deb
```

### On [RPM-based](https://en.wikipedia.org/wiki/Category:RPM-based_Linux_distributions) Linux distributions E.G. Fedora & Centos

Download [protean-0.14.0-SNAPSHOT-develop.noarch.rpm](https://github.com/passivsystems/protean/releases/download/0.14.0-SNAPSHOT/protean-0.14.0-SNAPSHOT-develop.noarch.rpm) and double click on the file or run.

``` bash
sudo rpm -i ~/Downloads/protean-0.14.0-SNAPSHOT-develop.noarch.rpm
```

### On macOS

Download [protean-osx.tgz](https://github.com/passivsystems/protean/releases/download/0.14.0-SNAPSHOT/protean-osx.tgz), extract the file and run.

```bash
sudo ~/Downloads/protean/install.sh
```

### On Windows

TODO - see docker install for now.

### Via Nix

You can install `protean` using the [nix package manager](https://nixos.org/nix).

```bash
nix-env -i -f https://github.com/passivsystems/protean/releases/download/0.14.0-SNAPSHOT/protean-nix.tgz
```

### Via Docker

There is a [Docker image](https://hub.docker.com/r/rossputin/protean-example) that you can use to run `protean` in a container.

```bash
docker pull rossputin/protean-example
```

### Other

Download [protean.tgz](https://github.com/passivsystems/protean/releases/download/0.14.0-SNAPSHOT/protean.tgz) and extract it. Place the contents into `$HOME/bin` and add this directory to your `$PATH` by either:
* editing `~/.profile` or `~/.bashrc` with `export PATH="$PATH:$HOME/bin"` then running `source ~/.profile` or `source ~/.bashrc`
* creating a symlink to the Protean executable file to a directory thats already on your `$PATH`

## Getting Started

For an overview of all Protean commands, and to test that your install went well, run this command in a terminal.

```bash
protean
```

Clone [Protean examples](https://github.com/passivsystems/protean-examples) and navigate into the directory.

```bash
git clone git@github.com:passivsystems/protean-examples.git
cd protean-examples
```

## The Codex File - RESTful API specification

Taken from reference codex -  [protean-examples/petstore-default/petstore.cod.edn](https://github.com/passivsystems/protean-examples/tree/master/petstore-default/petstore.cod.edn).

```clojure
; A codex is an API specification for RESTful resources. Each individual
; resource is comprised from an API name, the resource path and HTTP method.
; Specifications can be made individually, shared with all resources with the
; same path or global to all. This means specifications can be inherited,
; overridden or removed (by setting the value to :remove).
;
; Path definitions such as :includes, :body-examples and :body-schema can be
; relative or absolute paths. For relative paths the first attempt is to
; resolve them to the the codex directory. The second is the directory that the
; Protean command was run in. Then final is PROTEAN_HOME which defaults to
; where Protean was installed on your OS.
;
; Content types are determined by :body-examples or :body-schema file extension
; but can also be manually set.
;
; :body-schema helps ensure received payloads are validated and return types are
; accurate. This is important when simulating an API and helps when documenting
; an API.
{
  ; The API header/title displayed in the site docs.
  :title "Pet Store API Docs"

  ; :doc is a markdown descriptive field for site docs.
  ; See https://github.com/yogthos/markdown-clj for markdown support.
  :doc "Demonstrates what is possible with **Protean** without creating a custom sim"

  ; Merges in external definitions to be used throughout this codex.
  ; defaults.edn is in the PROTEAN_HOME directory and contains typical
  ; definitions for types :Date :DateTime :Token :Ip :String
  :includes [ "defaults.edn" ]

  ; Define global types using regular expression notation. Without including
  ; any files Protean supports :Int :Long :Double :Boolean :Uuid
  :types {
    :Status "(available|pending|sold)"
  }

  ; Variables are defined, documented and illustrated like so. They are
  ; referenced by wrapping the name with ${}. Note if an example is not
  ; provided a random value is generated from the :type regular expression.
  :vars {
    "petId" {:type :Uuid :doc "Universally unique identifier for a pet" :examples ["cc11d131-ed9e-4d8b-b038-fdc1ded07978"]}
    "name" {:type :String :doc "Name of the pet" :examples ["tiddles"]}
    "status" {:type :Status :doc "Status of the pet"}
    "bearerToken" {:type :Token :doc "Auth token" :examples ["08d2301e-ee81-4654-b448-0636f454612a"]}
  }

  ; Add authentication headers to everything.
  :req {:headers {"Authorization" "Bearer ${bearerToken}"}}

  ; API name is mandatory, can only be defined once per codex and can't be blank.
  "petstore" {
    ; An API consists of multiple resources.
    "pets" {
      ; Each resource must have one or many distinct HTTP methods.
      :get {
        ; Use keys :doc :types :vars :req :rsp to form a specification.
        :doc "Finds all pets, or pets filtered by status or name"
        ; Possible :req values are :headers :query-params :form-params :body-schema :doc and :body-examples
        :req {
          :headers {"Authorization" :remove} ; Remove authorization
          :query-params {
            "status" ["${status}" :optional] ; :optional or :required
            "name" ["${name}" :optional] ; append with :multiple for comma separated lists
          }
        }

        ; :rsp requires a status code which can contain :headers :body-schema
        ; :body-examples & :doc
        :rsp {
          :200 {:body-examples ["data/rsp/pets.json"]}
          :400 {}
        }
      }

      :post {
        :doc "Add a new pet to the store"
        :req {:body-examples ["data/req/new-pet.json"]
              :body-schema "data/pet.schema.json"}
        :rsp {
          :201 {:headers {"Location" "/pet/${petId}"}}
          :400 {}
        }
      }
    }

    ; A resource can contain multiple path and matrix params. Please note
    ; matrix params must start with ; (see next resource)
    "pets/${petId}" {
      ; Lets inherit some responses so we don't repeat ourselves for just this resource
      :rsp {
        :200 {:body-examples ["data/rsp/pet.json"]
              :body-schema "data/pet.schema.json" }
        :404 {}
      }

      :get {:doc "Find a pet by ID"}

      :put {
        :doc "Updates a pet in the store with form data"
        :req {:form-params {"name" ["${name}" :required]
                            "status" ["${status}" :required]}}
        :rsp {:400 {}} ; Add an additional bad request response
      }

      :delete {
        :doc "Deletes a pet"
        :rsp {:200 :remove :204 {}} ; Change the inherited response from :200 to :204
      }

      :patch {
        :doc "Partial updates to a pet"
        :rsp {:400 {}} ; Add an additional bad request response
      }
    }

    ; A matrix param example - note param name must start with ;
    "pets${;petsFilter}" {
      :get {
        :doc "Finds all pets, or pets filtered by status or name"
        ; :vars could have been defined globally for reuse
        :vars {
          ";petsFilter" {
            :type :MatrixParams
            ; To explicitly set examples - otherwise Protean will have a go
            ; :examples [";status=in;name=tiddles"]
            :struct {
              "status" ["${status}" :optional] ; :optional or :required
              "name" ["${name}" :optional]    ; append with :multiple for comma separated lists
            }
          }
        }
        :rsp {
          :200 {:body-examples ["data/rsp/pets.json"]}
          :400 {}
        }
      }
    }
  }
}
```

To generate documentation for this codex run.

```bash
protean doc --file petstore-default/petstore.cod.edn --host localhost --port 3000
```

Serve this codex, and any others in the same directory, by running Protean's simulation server.

```bash
protean sim --directory petstore-default/ --host localhost --port 3000
```

A list of Curl commands can be found by running (or in the documentation you generated).

```bash
protean service-usage --name petstore
```

Interact with Protean's server using the supplied Curl commands and test out Protean's default simulation strategy by altering requests.

## The Sim File - override Protean's default simulation strategy

The default Protean simulation strategy:

* Validates requests - must satisfy codex and schema specifications (400 if not)
* Preserves parameter values - variables keep input values the same as output (`${petId}` in request path is same as body response)
* Stateless - HTTP actions such as `DELETE` do not change resource state (you can still `GET` it afterwards)

Taken from reference sim file -  [protean-examples/petstore-sim/petstore.sim.edn](https://github.com/passivsystems/protean-examples/tree/master/petstore-sim/petstore.sim.edn).

```clojure
; Sims override Protean's default server behaviour with the power of Clojure.
;
; Namespace the sim to avoid collision with others.
(ns protean.petstore
  (:require [petstorelib :as lib] ; imports petstorelib.clj from same directory as codex.
            [protean.api.transformation.sim :as sim])) ; import Protean's sim API - see https://passivsystems.github.io/protean-api/ for API Docs

; Import dependencies from https://clojars.org to maximise your sim behaviour.
(sim/dependencies '[[clj-http "3.7.0"] ; to make http calls - https://github.com/dakrone/clj-http
                    [com.taoensso/timbre "4.10.0"]]) ; to write to a log file - https://github.com/ptaoussanis/timbre
; Actually both these libraries are currently on Protean's classpath and could
; be imported normally. But Protean's dependency tree may change so this is best
; practice.

; Note the ' prefix is required when importing external dependencies.
(require '[clj-http.client :as client]
         '[taoensso.timbre :as log])

; Add shared methods or variables here.
(def counter (atom 0))

{
  ; Sim struture is the same as the codex.
  "petstore" {
    ; Like the codex, :sim-cfg can be inherited or overridden to reconfigure the
    ; default behaviour. Default :sim-cfg is as follows.
    :sim-cfg {
      :cors true ; for any request  - adds "Access-Control-Allow-Origin" "*" to response header for Cross-Origin Resource Sharing.
                 ; for HTTP OPTIONS requests - responds to requests accurately saying what methods exists for Cross-Origin Resource Sharing.
      :validate? true ; Protean will validate using the :validate-rule when true
      ; Change the validation rule to provide a reusable validation strategy across your API.
      ; This function could be shared between multiple sims (local clj or clojars)
      ; Below shows how Protean's default implementation would look.
      :validate-rule (fn [request rule] ; This anonymous function takes two args
                                        ; request - map of HTTP request data
                                        ; rule - sim implementation (can be nil)
        (let [errs (sim/validate request)] ; map of error messages per param type (:header-errors :query-errors :form-errors :matrix-errors :body-errors)
                                           ; empty values are removed from the map so errs can be nil.
          (cond
            ; When an error is found, by default Protean will respond with a 400 response code
            ; (doesn't matter if its in the codex or not) and includes the error as custom
            ; Protean header value, as to not influence spec.
            errs {:status 400 :headers {"Protean-error" "Bad Request"
                                        "Protean-error-messages" (clojure.string/join ", " (vals errs))}}
            ; run the sim implementation code if it exists
            rule (apply rule [request])
            ; otherwise first success response defined in codex
            :else (sim/response request (first (sim/success-codes request))))))
    }

    ; Only need to specify the resources that you wish to enhance.
    ; The default sim behaviour (plus any sim-cfg overrides) will take care of the rest.
    "api/pet" {
      ; A simulation rule is an anonymous function which is passed a Ring request map plus some
      ; additional Protean related values. Most Protean APIs will require the request to be fed to them.
      ; Use either Clojure short hand notation #(sim/response % 200) or (fn [r] (sim/response r 200))
      ; when creating sim rules. See https://github.com/ring-clojure/ring for details on Ring.
      ;
      ; See https://github.com/passivsystems/protean-api for a full list of protean APIs
      ;
      ; Your simulation rule must return a Ring response map E.G - {:status Int :headers String :body String}
      ; Status is the only required key. Protean's sim API allows you to construct a response that has been defined
      ; in the codex, as illustrated below. Note an exception would be thrown if 200 was not found.
      :get #(sim/response % 200) ; This request is validated via :sim-cfg :validate-rule above
    }

    "api/pet/${petId}" {
      ; 200 response for petId cc11d131-ed9e-4d8b-b038-fdc1ded07978 only
      :get #(case (sim/path-param % "petId")
             "cc11d131-ed9e-4d8b-b038-fdc1ded07978" (sim/response % 200)
             (sim/response % 404))

      ; 50% chance of a random codex error response.
      :put #(sim/response % (if (< (rand) 0.5)
                              (first (sim/success-codes %))
                              (rand-nth (sim/error-codes %))))

      ; Error every third access
      :delete #(sim/response % (if (= 0 (mod (swap! counter inc) 3))
                                 (rand-nth (sim/error-codes % %))
                                 (first (sim/success-codes %))))
    }
  }
}
```

Stop Protean's simulation server with `Ctrl C` in the terminal it was started and point to the `petstore-sim` directory.

```bash
protean sim --directory petstore-sim/ --host localhost --port 3000
```

See [protean-examples/text-adventure](https://github.com/passivsystems/protean-examples/tree/master/text-adventure) for an illustration on how to make sims stateful via [enduro](https://github.com/alandipert/enduro).

## Auto reload

When creating your own codex or simulation files, run Protean commands such as `doc` and `sim` with the `--reload` argument to auto rebuild on file change.

```bash
protean doc --reload --file path/to/project/my-codex.cod.edn --host localhost --port 3000
protean sim --reload --directory path/to/project/ --host localhost --port 3000
```

These commands should be run in separate terminals as the auto reload process is kept alive till `enter` is struck in the terminal.

## Developers

Clone or fork the project. Install [Leiningen](https://leiningen.org) and substitute `protean` with `lein run` in the commands above.

Run Protean's integration tests before committing.

```bash
lein run test
```
## Contributing

All contributions ideas/pull requests/bug reports are welcome, we hope you find it useful.

## License

Protean is licensed with Apache License v2.0.
