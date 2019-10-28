 docker rm `docker ps -a | grep "/opt/haze" | awk 'BEGIN {OFS=" "}{split($0,res," *"); print res[1]}' | tr "\n" " "
 docker stop `docker ps -a | grep "/opt/haze" | awk 'BEGIN {OFS=" "}{split($0,res," *"); print res[1]}' | tr "\n" " "`

#windows admin
net stop com.docker.service
net start com.docker.service