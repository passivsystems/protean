BUILD INSTRUCTIONS

This file details builds across various deployment platforms.
All builds assume that `lein uberjar` has been run and the uberjar exists in the target directory.

# Linux

## DOCKER

### Build

(assuming you are in the build directory)
```bash
./build-docker.sh
cd ../target/docker
docker rmi -f protean-example
docker build -t protean-example .
```

### Test

Run Protean on your new Docker image with:
```bash
docker run -it -p 3000:3000 -p 3001:3001 protean-example
```

SSH into your Protean Docker image with:
```bash
docker run -it protean-example /bin/bash
```

Files are located in /home so:
```bash
cd /home
```

Run the Protean client and list apis (services) to see if the server is setup and working ok:
```bash
./protean -H 172.17.0.1 services
```

Run the Protean client to learn how to interact with one of the services:
```bash
./protean -H 172.17.0.1 service-usage -n petstore
```


## TGZ

(assuming you are in the build directory)

(substitute your version for the example below)
```bash
./build-tgz.sh 0.5.0
```


## RPM

(assuming you are in the build directory)

(substitute your version for the example below)
```bash
./build-rpm.sh 0.5.0
```


## DEB

(assuming you are in the build directory)

(substitute your version for the example below)
```bash
./build-deb.sh 0.5.0
```


# OSX

(assuming you are in the build directory)

(substitute your version for the example below)
```bash
./build-osx.sh 0.5.0
```

# NIX

(assuming you are in the build directory)

(substitute your version for the example below)
```bash
./build-nix.sh 0.5.0
```
