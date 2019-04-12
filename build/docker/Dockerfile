FROM openjdk:8-alpine
ADD protean.tgz /home/
CMD cd /home; java -DPROTEAN_CODEX_DIR=/home/codex -DPROTEAN_ASSET_DIR=/home/public -DHOSTNAME=localhost -cp protean.jar protean.server.main
