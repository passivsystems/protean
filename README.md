# protean

Mock RESTful Apis, for all of your projects, swap behaviour out real time.  Control it all with REST via curl.  Simulate errors.  Automatically generate curl commands to test your services.  All projects added get documentation generated in webapp form.

## Usage

    lein deps
    lein run

by default the admin area runs on 3001 and the main app area on 3000.

You can override the port with:

    lein run 4000 4001

Build a distributable with lein uberjar, then run with:

    java -jar protean.jar 4000 4001

## License

Protean is licensed with Apache License v2.0.
