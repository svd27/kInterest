docker rm $(docker ps -a | grep "/opt/haze" | awk 'BEGIN {OFS=" "}{split($0,res," *"); print res[1]}' | tr "\n" " ")
docker stop $(docker ps -a | grep "/opt/haze" | awk 'BEGIN {OFS=" "}{split($0,res," *"); print res[1]}' | tr "\n" " ")

docker run --rm --name myignite1 -p 47503:47500 -p 47103:47100 -p 10803:10800 -it ignite-image


docker run -p 8080:8080 -e "OPTION_LIBS=ignite-rest-http" -e "IGNITE_CONFIG=https://raw.githubusercontent.com/apache/ignite/master/examples/config/example-cache.xml" apacheignite/ignite
docker run -p 8080:8080 -p 10800:10800 -p 11211:11211 -p 47400:47400 -p 48100:48100  -p 47100:47100 -p 47500:47500 -e "OPTION_LIBS=ignite-rest-http" -e "IGNITE_CONFIG=https://raw.githubusercontent.com/apache/ignite/master/examples/config/example-cache.xml" apacheignite/ignite
docker run -p 8080:8080 -p 10800:10800 -p 11211:11211 -p 47400:47400 -p 48100:48100  -p 47100:47100 -p 47500:47500 -e "OPTION_LIBS=ignite-rest-http" -e "JVM_OPTS=-Djava.net.preferIPv4Stack=true" apacheignite/ignite

#windows admin
net stop com.docker.service
net start com.docker.service

ping -c1 -q host.docker.internal 2>&1 | grep "bad address" >/dev/null && echo "$(netstat -nr | grep '^0.0.0.0' | awk '{print $2}') host.docker.internal" >> /etc/hosts && echo "Hosts File Entry Added for Linux!!!!!" ||:

sudo ip link add dummy0 type dummy
sudo ip link set dev dummy0 up
ip link show type dummy

sudo ip addr add 169.254.1.1/32 dev dummy0
sudo ip link set dev dummy0 up
ip addr show dev dummy0

echo "169.254.1.1 host.docker.internal" >> /etc/hosts

