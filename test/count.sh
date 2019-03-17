#!/usr/bin/env bash


while :;do
        res=`docker-compose -f mysql.yml exec mysql_0 mysql -uroot -proot test_0 -N -s -e "select count(1) from news;select count(1) from news_bak;" 2>/dev/null | tr -s 'A-Z' 'a-z' | grep -v "warn" | xargs`
        src=`echo $res | awk '{print $1}'`
        dst=`echo $res | awk '{print $1}'`
	timestamp=$(date +"%Y%m%d-%H%M%S")
	echo "ts=$timestamp"
        echo "src=$src"
        echo "dst=$dst"
        sleep 1
done
