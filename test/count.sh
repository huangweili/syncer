#!/usr/bin/env bash


while :;do
        timestamp=$(date +"%Y%m%d-%H%M%S")
        src=`docker-compose -f mysql.yml exec mysql_0 mysql -uroot -proot test_0 -N -s -e "select count(1) from news" | tr -s 'A-Z' 'a-z' | grep -v "warn" | xargs`
        dst=`docker-compose -f mysql.yml exec mysql_0 mysql -uroot -psimu123 -h192.168.1.204 test_0 -N -s -e "select count(1) from news_bak" | tr -s 'A-Z' 'a-z' | grep -v "warn" | xargs`
        echo "time: $timestamp"
        echo "src=$src"
        echo "dst=$dst"
        sleep 5
done